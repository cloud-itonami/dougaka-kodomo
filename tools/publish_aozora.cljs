;; aozora.app への video post 投稿（Phase A publisher。ADR-2607164500）。
;; 実行は kotobase-client の node_modules を解決できる場所から:
;;   cd orgs/kotoba-lang/kotobase-client && \
;;   nbb --classpath src <this>/tools/publish_aozora.cljs <mp4> <post-text> [handle]
;; 認証は studio と同じ self-sovereign CACAO (kotobase.cacao/mint-cacao、
;; aud=did:web:aozora.app, capability=account:session)。seed は macOS Keychain
;; service "aozora-studio-actor-key-kodomo"（studio の慣習と同一、無ければ生成して保存）。
(ns publish-aozora
  (:require ["fs" :as fs]
            ["child_process" :as cp]
            ["@noble/curves/ed25519.js" :refer [ed25519]]
            [clojure.string :as str]
            [promesa.core :as p]
            [kotobase.cacao :as cacao]
            [kotobase.cid :as cid]))

(def service "https://pds.aozora.app")
(def mp4-path (first *command-line-args*))
(def post-text (fs/readFileSync (second *command-line-args*) "utf8"))
(def handle (or (nth *command-line-args* 2 nil) "kodomo.aozora.app"))
;; post rkey は曲ごとに一意(省略時は かずのうた。1曲=1 record)。
(def post-rkey (nth *command-line-args* 3 "kazu-no-uta-001"))
(def video-alt (nth *command-line-args* 4 "かずのうた — 1から10までかぞえる知育ソング"))
(def keychain-service "aozora-studio-actor-key-kodomo")

(defn hex->bytes [h]
  (js/Uint8Array.from (map #(js/parseInt (apply str %) 16) (partition 2 h))))

(defn keychain-seed! []
  (let [r (cp/spawnSync "security" #js ["find-generic-password" "-s" keychain-service "-w"]
                        #js {:encoding "utf8"})]
    (if (zero? (.-status r))
      (str/trim (.-stdout r))
      (let [raw (js/crypto.getRandomValues (js/Uint8Array. 32))
            hex (apply str (map #(.padStart (.toString % 16) 2 "0") raw))
            add (cp/spawnSync "security"
                              #js ["add-generic-password" "-s" keychain-service
                                   "-a" "kodomo" "-w" hex "-U"]
                              #js {:encoding "utf8"})]
        (when-not (zero? (.-status add))
          (throw (js/Error. (str "keychain add failed: " (.-stderr add)))))
        (println "new actor seed generated and stored in Keychain:" keychain-service)
        hex))))

(defn xrpc! [ep body jwt]
  (p/let [res (js/fetch (str service "/xrpc/" ep)
                        (clj->js {:method "POST"
                                  :headers (cond-> {"content-type" "application/json"}
                                             jwt (assoc "authorization" (str "Bearer " jwt)))
                                  :body (js/JSON.stringify (clj->js body))}))
          j (.json res)]
    (js->clj j :keywordize-keys true)))

(defn -main []
  (let [seed (hex->bytes (keychain-seed!))
        did (cid/did-key-from-ed25519-pub (.getPublicKey ed25519 seed))
        mint #(-> (cacao/mint-cacao {:secret-key seed :aud "did:web:aozora.app"
                                     :capability "account:session" :graph did
                                     :ttl-sec 300})
                  :cacao-b64)
        video-bytes (fs/readFileSync mp4-path)]
    (println "actor did:" did "handle:" handle)
    (p/let [acct (xrpc! "com.atproto.server.createAccount"
                        {:handle handle :cacao_b64 (mint)} nil)
            sess (if (and (:did acct) (:accessJwt acct))
                   acct
                   (xrpc! "com.atproto.server.createSession" {:cacao_b64 (mint)} nil))
            _ (when-not (:accessJwt sess)
                (throw (js/Error. (str "auth failed: " (js/JSON.stringify (clj->js [acct sess]))))))
            jwt (:accessJwt sess)
            rdid (:did sess)
            ;; upload video blob (R2, ≤100MB)
            up-res (js/fetch (str service "/xrpc/com.atproto.repo.uploadBlob")
                             (clj->js {:method "POST"
                                       :headers {"content-type" "video/mp4"
                                                 "authorization" (str "Bearer " jwt)}
                                       :body video-bytes}))
            up (.json up-res)
            upc (js->clj up :keywordize-keys true)
            blob (:blob upc)
            _ (when-not blob (throw (js/Error. (str "uploadBlob failed: " (js/JSON.stringify up)))))
            blob-cid (get-in blob [:ref :$link])
            src (str service "/xrpc/com.atproto.sync.getBlob?cid=" blob-cid)
            ;; profile (rkey self)
            prof (xrpc! "com.atproto.repo.createRecord"
                        {:repo rdid :collection "app.bsky.actor.profile" :rkey "self"
                         :record {:$type "app.bsky.actor.profile"
                                  :displayName "こどもチャンネル(メロとポポ)"
                                  :description "幼児向け知育ソング・アニメ(0〜4さい)。メロ・ポポ・ミミといっしょにうたおう! VOICEVOX使用。ai-gftd-dougaka-kodomo (ADR-2607164500)"}}
                        jwt)
            ;; video post
            post (xrpc! "com.atproto.repo.createRecord"
                        {:repo rdid :collection "app.bsky.feed.post"
                         :rkey post-rkey
                         :record {:$type "app.bsky.feed.post"
                                  :text post-text
                                  :createdAt (.toISOString (js/Date.))
                                  :embed {:$type "app.aozora.embed.video"
                                          :src src
                                          :mimeType "video/mp4"
                                          :video blob
                                          :alt video-alt
                                          :aspectRatio {:width 16 :height 9}}}}
                        jwt)]
      (println "blob cid:" blob-cid)
      (println "blob url:" src)
      (println "profile:" (pr-str prof))
      (println "post:" (pr-str post)))))

(-main)
