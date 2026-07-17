;; Phase A 音声レンダラ — compose_kazu.cljs の出力プランを実行する IO 層。
;;   nbb tools/render_kazu_audio.cljs <build-dir> [voicevox-url]
;; 1) VOICEVOX ソング合成 (sing_frame_audio_query 6000 → frame_synthesis)
;; 2) VOICEVOX スピーチ (audio_query → synthesis)
;; 3) 伴奏シンセ (chord pad + bass + glock + soft kick, 44.1kHz mono PCM)
;; 4) ffmpeg adelay + amix + loudnorm → audio-final.wav
(ns render-kazu-audio
  (:require ["fs" :as fs]
            ["path" :as path]
            ["child_process" :as cp]
            [clojure.string :as str]
            [promesa.core :as p]))

(def build-dir (or (first *command-line-args*) "build"))
;; 第2引数:
;;   省略 or http://127.0.0.1:50021 → ローカル VOICEVOX 直叩き(2ステップ)
;;   https://api.murakumo.cloud → 公開 API(/v1/audio/song, /v1/audio/speech、
;;     ADR-2607164500 Phase B)経由 = headless/cron で動く
(def vv-url (or (second *command-line-args*) "http://127.0.0.1:50021"))
(def public-mode? (str/includes? vv-url "murakumo.cloud"))

(def sing-style {"melo" 3001 "popo" 3000})   ; frame_decode: ずんだもん/四国めたん あまあま
(def speech-style {"melo" 1 "popo" 0})       ; speech: 同上

(defn read-json [f]
  (js/JSON.parse (fs/readFileSync (path/join build-dir f) "utf8")))

(defn- delay-ms [ms] (js/Promise. (fn [res _] (js/setTimeout res ms))))

;; public API 経由(tunnel + Cloudflare)は 524/5xx が出うる — 単一 VOICEVOX
;; エンジンへの並列殺到でタイムアウトするため、synth は public モードで直列化
;; (下記 -main)し、加えて transient 5xx を数回リトライする。
(defn post-json
  ([url body] (post-json url body 3))
  ([url body tries]
   (-> (js/fetch url #js {:method "POST"
                          :headers #js {"content-type" "application/json"}
                          :body (js/JSON.stringify body)})
       (.then (fn [^js res]
                (cond
                  (.-ok res) res
                  (and (>= (.-status res) 500) (> tries 1))
                  (p/let [_ (delay-ms (+ 800 (* 400 (- 3 tries))))]
                    (post-json url body (dec tries)))
                  :else (p/let [t (.text res)]
                          (throw (js/Error. (str url " -> " (.-status res) " " t))))))))))

(defn synth-song [score-entry]
  (let [{:keys [id singer score]} (js->clj score-entry :keywordize-keys true)
        out (path/join build-dir (str id ".wav"))]
    (p/let [s (if public-mode?
                ;; 公開 API: 1リクエストで score → wav
                (post-json (str vv-url "/v1/audio/song")
                           (clj->js {:query_speaker 6000 :speaker (sing-style singer)
                                     :score score}))
                ;; ローカル: sing_frame_audio_query → frame_synthesis
                (p/let [q (post-json (str vv-url "/sing_frame_audio_query?speaker=6000")
                                     (clj->js score))
                        qj (.json q)]
                  (post-json (str vv-url "/frame_synthesis?speaker=" (sing-style singer)) qj)))
            buf (.arrayBuffer s)]
      (fs/writeFileSync out (js/Buffer.from buf))
      out)))

(defn synth-spoken [i spoken-entry]
  (let [{:keys [text singer]} (js->clj spoken-entry :keywordize-keys true)
        out (path/join build-dir (str "spk" i ".wav"))
        sp (speech-style singer)]
    (p/let [s (if public-mode?
                (post-json (str vv-url "/v1/audio/speech")
                           (clj->js {:input text :speaker sp :speed 0.95}))
                (p/let [q (js/fetch (str vv-url "/audio_query?speaker=" sp
                                         "&text=" (js/encodeURIComponent text))
                                    #js {:method "POST"})
                        qj (.json q)
                        _ (set! (.-speedScale qj) 0.95)]
                  (post-json (str vv-url "/synthesis?speaker=" sp) qj)))
            buf (.arrayBuffer s)]
      (fs/writeFileSync out (js/Buffer.from buf))
      out)))

;; --- 伴奏シンセ ---------------------------------------------------------------

(def SR 44100)
(defn midi->hz [m] (* 440 (js/Math.pow 2 (/ (- m 69) 12))))

(defn render-accomp []
  (let [{:keys [beat-sec total-beats bars]} (js->clj (read-json "accomp.json")
                                                     :keywordize-keys true)
        total-n (js/Math.ceil (* total-beats beat-sec SR))
        buf (js/Float64Array. total-n)
        add-tone!
        (fn [start-sec dur-sec hz gain shape]
          (let [s0 (js/Math.floor (* start-sec SR))
                n (js/Math.floor (* dur-sec SR))]
            (dotimes [i n]
              (let [idx (+ s0 i)
                    t (/ i SR)
                    env (case shape
                          :pad (* (min 1 (/ t 0.08))
                                  (min 1 (max 0 (/ (- dur-sec t) 0.15))))
                          :pluck (js/Math.exp (* -5 t))
                          :kick (js/Math.exp (* -18 t)))
                    f (if (= shape :kick) (+ 45 (* (- hz 45) (js/Math.exp (* -30 t)))) hz)
                    v (* gain env (js/Math.sin (* 2 js/Math.PI f t)))]
                (when (< idx total-n)
                  (aset buf idx (+ (aget buf idx) v)))))))]
    (doseq [{:keys [start-beat beats chord bass]} bars]
      (let [t0 (* start-beat beat-sec)
            bar-dur (* beats beat-sec)]
        ;; pad
        (doseq [m chord]
          (add-tone! t0 bar-dur (midi->hz m) 0.045 :pad))
        ;; bass on beats 0,2
        (doseq [b [0 2]]
          (add-tone! (+ t0 (* b beat-sec)) (* 0.9 beat-sec) (midi->hz bass) 0.11 :pluck))
        ;; glock arpeggio (octave up) on each beat
        (doseq [b (range 4)]
          (add-tone! (+ t0 (* b beat-sec)) (* 0.8 beat-sec)
                     (* 2 (midi->hz (nth chord (mod b (count chord))))) 0.05 :pluck))
        ;; soft kick beats 0,2
        (doseq [b [0 2]]
          (add-tone! (+ t0 (* b beat-sec)) 0.14 120 0.22 :kick))))
    ;; normalize to 0.85 peak max
    (let [peak (reduce (fn [mx i] (max mx (js/Math.abs (aget buf i)))) 0 (range total-n))
          scale (if (> peak 0.85) (/ 0.85 peak) 1.0)
          pcm (js/Buffer.alloc (* 2 total-n))]
      (dotimes [i total-n]
        (.writeInt16LE pcm (js/Math.round (* 32767 scale (aget buf i))) (* 2 i)))
      ;; WAV header (16-bit mono 44100)
      (let [hdr (js/Buffer.alloc 44)]
        (.write hdr "RIFF" 0)
        (.writeUInt32LE hdr (+ 36 (.-length pcm)) 4)
        (.write hdr "WAVE" 8)
        (.write hdr "fmt " 12)
        (.writeUInt32LE hdr 16 16) (.writeUInt16LE hdr 1 20) (.writeUInt16LE hdr 1 22)
        (.writeUInt32LE hdr SR 24) (.writeUInt32LE hdr (* SR 2) 28)
        (.writeUInt16LE hdr 2 32) (.writeUInt16LE hdr 16 34)
        (.write hdr "data" 36)
        (.writeUInt32LE hdr (.-length pcm) 40)
        (fs/writeFileSync (path/join build-dir "accomp.wav")
                          (js/Buffer.concat #js [hdr pcm]))))
    (println "accomp.wav written:" total-n "samples")))

;; --- mix ----------------------------------------------------------------------

(defn ffmpeg-mix [score-files spoken-files]
  (let [scores (read-json "scores.json")
        spoken (read-json "spoken.json")
        vocal-inputs (map (fn [f e] {:file f :delay-ms (js/Math.round (* 1000 (.-offset-sec e)))})
                          score-files scores)
        spoken-inputs (map (fn [f e] {:file f :delay-ms (js/Math.round (* 1000 (.-offset-sec e)))})
                           spoken-files spoken)
        all (concat [{:file (path/join build-dir "accomp.wav") :delay-ms 0}]
                    vocal-inputs spoken-inputs)
        n (count all)
        inputs (mapcat (fn [{:keys [file]}] ["-i" file]) all)
        chains (map-indexed
                (fn [i {:keys [delay-ms]}]
                  (let [gain (if (zero? i) 0.85 1.0)]
                    (str "[" i ":a]aresample=44100,volume=" gain
                         ",adelay=" delay-ms "|" delay-ms "[a" i "]")))
                all)
        fg (str (str/join ";" chains) ";"
                (apply str (map #(str "[a" % "]") (range n)))
                "amix=inputs=" n ":normalize=0,loudnorm=I=-14:TP=-1.5:LRA=11[out]")
        args (concat inputs
                     ["-filter_complex" fg "-map" "[out]"
                      "-ar" "44100" "-ac" "2" "-y"
                      (path/join build-dir "audio-final.wav")])]
    (println "mixing" n "inputs...")
    (cp/execFileSync "ffmpeg" (clj->js (map str args)) #js {:stdio "inherit"})
    (println "audio-final.wav done")))

;; public モードは単一 VOICEVOX エンジンを共有するため直列(過負荷=524 回避)。
;; ローカルモードは高速なので従来どおり並列。
(defn- p-seq [f xs]
  (p/loop [items (vec xs) acc []]
    (if (empty? items)
      acc
      (p/let [r (f (first items))]
        (p/recur (subvec items 1) (conj acc r))))))

(defn -main []
  (let [scores (read-json "scores.json")
        spoken (read-json "spoken.json")]
    (render-accomp)
    (if public-mode?
      (p/let [score-files (p-seq synth-song scores)
              spoken-files (p-seq (fn [[i e]] (synth-spoken i e)) (map-indexed vector spoken))]
        (ffmpeg-mix score-files spoken-files))
      (p/let [score-files (p/all (map synth-song scores))
              spoken-files (p/all (map-indexed synth-spoken spoken))]
        (ffmpeg-mix score-files spoken-files)))))

(-main)
