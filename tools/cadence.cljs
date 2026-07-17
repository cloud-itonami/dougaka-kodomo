;; Phase C cadence 選択器(ADR-2607164500 / 2607162200)。
;;   nbb --classpath src:resources tools/cadence.cljs [--publish]
;; kodomo.aozora.app の既存投稿(getAuthorFeed)を確認し、songs.edn の未投稿トピックを
;; :priority 降順で1つ選び produce.cljs に委譲する。全投稿済みなら no-op(exit 0)。
;; publish 承認は gate green で auto(ADR-2607162200: governor auto-publish +
;; escalate-on-flag。gate hold は produce.cljs が非ゼロ終了で escalate)。
(ns cadence
  (:require ["fs" :as fs]
            ["path" :as path]
            ["child_process" :as cp]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [promesa.core :as p]))

(def args (vec *command-line-args*))
(def publish? (some #(= "--publish" %) args))
(def repo-root (path/dirname (path/dirname (path/resolve "tools/cadence.cljs"))))
(def appview "https://appview.aozora.app")
(def handle "kodomo.aozora.app")

(def songs
  (:songs (edn/read-string (fs/readFileSync (path/join repo-root "resources" "songs.edn") "utf8"))))

(defn posted-rkeys []
  (p/let [res (js/fetch (str appview "/xrpc/app.bsky.feed.getAuthorFeed?actor="
                             handle "&limit=100"))
          j (.json res)
          feed (js->clj (or (.-feed j) #js []) :keywordize-keys true)]
    (into #{} (keep (fn [item]
                      (let [uri (get-in item [:post :uri])]
                        (when uri (last (str/split uri #"/")))))
                    feed))))

(defn -main []
  (p/let [done (posted-rkeys)]
    (let [pending (->> songs
                       (remove #(contains? done (:rkey %)))
                       (sort-by :priority >))]
      (println "posted rkeys:" (pr-str done))
      (if-let [next-song (first pending)]
        (let [topic (name (:topic next-song))]
          (println (str "→ next topic: " topic " (rkey " (:rkey next-song) ")"))
          (let [r (cp/spawnSync "nbb"
                                (clj->js (concat ["--classpath" "src:resources" "tools/produce.cljs" topic]
                                                 (when publish? ["--publish"])))
                                #js {:cwd repo-root :encoding "utf8" :stdio "inherit"})]
            (js/process.exit (.-status r))))
        (println "✓ all registered songs already posted — nothing to do.")))))

(-main)
