;; かずのうた (1〜10) — Phase A の決定論コンポーザ。
;;   nbb --classpath src tools/compose_kazu.cljs <build-dir>
;; song spec (content/kazu-no-uta.edn) と実行プラン
;; (scores.json / spoken.json / accomp.json / timeline.json) を純データで出力する。
;; 外部 IO なし（VOICEVOX/ffmpeg 実行は render_kazu_audio.cljs 側）。
(ns compose-kazu
  (:require ["fs" :as fs]
            ["path" :as path]
            [kodomo.song :as song]))

(def bpm 100)
(def beat-sec (/ 60.0 bpm))
(def frames-per-beat (* beat-sec 93.75)) ; VOICEVOX 24000Hz/256hop = 93.75fps

(def midi->kw
  {60 :C4 61 :C#4 62 :D4 63 :D#4 64 :E4 65 :F4 66 :F#4 67 :G4
   68 :G#4 69 :A4 70 :A#4 71 :B4 72 :C5 73 :C#5 74 :D5})

;; --- 楽曲データ（motif） -----------------------------------------------------

(def v1-counts
  [["いち" [["い" 60 0.75] ["ち" 62 0.75]]]
   ["に"   [["に" 64 1.5]]]
   ["さん" [["さ" 64 0.75] ["ん" 65 0.75]]]
   ["し"   [["し" 67 1.5]]]
   ["ご"   [["ご" 69 1.5]]]])

(def v2-counts
  [["ろく"   [["ろ" 60 0.75] ["く" 62 0.75]]]
   ["しち"   [["し" 64 0.75] ["ち" 65 0.75]]]
   ["はち"   [["は" 67 0.75] ["ち" 67 0.75]]]
   ["きゅう" [["きゅ" 69 0.75] ["う" 69 0.75]]]
   ["じゅう" [["じゅ" 72 1.0] ["う" 72 0.5]]]])

(def recap1 ["いちにさんしご"
             [["い" 60 0.5] ["ち" 62 0.5] ["に" 64 0.5] ["さ" 64 0.25]
              ["ん" 65 0.25] ["し" 67 0.5] ["ご" 69 1.0]]])

(def recap2 ["ろくしちはちきゅうじゅう"
             [["ろ" 60 0.5] ["く" 62 0.5] ["し" 64 0.5] ["ち" 65 0.5]
              ["は" 67 0.5] ["ち" 67 0.5] ["きゅ" 69 0.5] ["う" 69 0.5]
              ["じゅ" 72 1.0] ["う" 72 1.0]]])

(def chorus-a ["かぞえよう" [["か" 67 0.5] ["ぞ" 69 0.5] ["え" 71 0.5]
                             ["よ" 72 1.0] ["う" 72 0.5]]])
(def chorus-b ["いちからじゅうまで"
               [["い" 72 0.5] ["ち" 71 0.5] ["か" 69 0.5] ["ら" 67 0.5]
                ["じゅ" 65 0.5] ["う" 64 0.5] ["ま" 62 0.5] ["で" 60 0.5]]])

(defn mk-line [start-beat singer kind [lyric notes]]
  {:line/lyric lyric
   :line/singer singer
   :line/kind kind
   :line/start-beat start-beat
   :line/notes (mapv (fn [[mora midi beats]]
                       {:note/mora mora :note/pitch (midi->kw midi)
                        :note/midi midi :note/beats beats})
                     notes)})

(defn verse-lines
  "5 counts × (lead 2拍 + echo 2拍) + recap。section 相対 beat。"
  [counts recap recap-start echo-recap?]
  (let [count-lines
        (mapcat (fn [i [lyric notes]]
                  (let [b (* i 4)]
                    [(mk-line b :melo :sung [lyric notes])
                     (mk-line (+ b 2) :popo :sung [lyric notes])]))
                (range) counts)
        recap-lines (cond-> [(mk-line recap-start :melo :sung recap)]
                      echo-recap? (conj (mk-line (+ recap-start 4) :popo :sung recap)))]
    (vec (concat count-lines recap-lines))))

(defn chorus-lines []
  (vec (for [[bar singer l] [[0 :melo chorus-a] [1 :popo chorus-a]
                             [2 :melo chorus-b] [3 :melo chorus-a]
                             [4 :melo chorus-a] [5 :popo chorus-a]
                             [6 :melo chorus-b] [7 :melo chorus-a]]]
         (mk-line (* bar 4) singer :sung l))))

(def outro-spoken
  ;; [rel-beat singer text]
  (map vector
       [1 2 3 4 5 7 8 9 10 11]
       (cycle [:melo :popo])
       ["いち" "に" "さん" "し" "ご" "ろく" "しち" "はち" "きゅう" "じゅう"]))

(def sections
  ;; [kind rel-len-beats lines]
  [[:intro 16 []]
   [:verse 28 (verse-lines v1-counts recap1 20 true)]
   [:chorus 32 (chorus-lines)]
   [:verse 32 (verse-lines v2-counts recap2 20 false)]
   [:chorus 32 (chorus-lines)]
   [:call-response 24
    (vec (concat
          (for [[b singer text] outro-spoken]
            {:line/lyric text :line/singer singer :line/kind :spoken
             :line/start-beat b})
          [(mk-line 16 :melo :sung ["じゅう" [["じゅ" 72 1.5] ["う" 72 1.5]]])
           (mk-line 16 :popo :sung ["じゅう" [["じゅ" 72 1.5] ["う" 72 1.5]]])]))]])

(def section-starts
  (vec (reductions + 0 (map second sections))))

(def total-beats (last section-starts))

;; --- song spec (kodomo.song 検証対象) ---------------------------------------

(def song-spec
  {:song/id "kazu-1-10-001"
   :song/topic :kazu-1-10
   :song/language "ja"
   :song/bpm bpm
   :song/key :C
   :song/sections
   (mapv (fn [[kind _len lines]]
           {:section/kind (if (= kind :intro) :verse kind) ; intro は器楽、検証上 verse 扱い
            :section/lines
            (mapv (fn [l]
                    (cond-> {:line/lyric (:line/lyric l)
                             :line/singer (:line/singer l)}
                      (:line/notes l)
                      (assoc :line/notes
                             (mapv #(select-keys % [:note/pitch :note/beats])
                                   (:line/notes l)))))
                  lines)})
         (rest sections))}) ; intro(空)は除外

;; --- VOICEVOX score 生成（drift-free rounding） ------------------------------

(defn beats->frames [b] (js/Math.round (* b frames-per-beat)))

;; VOICEVOX はスコア先頭に無音 rest を要求する（先頭が有声だと
;; consonant_lengths[0] エラー）。全スコアに 1 拍のプリロール rest を入れ、
;; 配置時に offset-sec から同じ長さを引いて相殺する。
(def pre-roll-frames (beats->frames 1))
(def pre-roll-sec (/ pre-roll-frames 93.75))

(defn build-score
  "1 section × 1 singer の sung notes → VOICEVOX Score。
   絶対 frame 位置で rest を埋める。"
  [lines]
  (let [notes (sort-by :abs-beat
                       (for [l lines
                             :when (= :sung (:line/kind l))
                             :let [start (:line/start-beat l)]
                             [i n] (map-indexed vector (:line/notes l))
                             :let [nb (reduce + (map :note/beats (take i (:line/notes l))))]]
                         {:abs-beat (+ start nb)
                          :beats (:note/beats n)
                          :midi (:note/midi n)
                          :mora (:note/mora n)}))]
    (when (seq notes)
      (let [out #js []]
        (.push out #js {:key nil :frame_length pre-roll-frames :lyric ""})
        (loop [cursor-f 0 ns' notes]
          (if (empty? ns')
            (do (.push out #js {:key nil :frame_length 20 :lyric ""})
                {:notes out})
            (let [{:keys [abs-beat beats midi mora]} (first ns')
                  start-f (beats->frames abs-beat)
                  end-f (beats->frames (+ abs-beat beats))]
              (when (> start-f cursor-f)
                (.push out #js {:key nil :frame_length (- start-f cursor-f) :lyric ""}))
              (.push out #js {:key midi :frame_length (max 1 (- end-f (max start-f cursor-f)))
                              :lyric mora})
              (recur (max end-f cursor-f) (rest ns')))))))))

;; --- 伴奏プラン ---------------------------------------------------------------

(def chord-table {:C [60 64 67] :F [65 69 72] :G [67 71 74] :Am [57 60 64]})

(def progression
  (concat [:C :G :Am :F]                       ; intro 4
          [:C :C :F :C :G :G :C]               ; verse1 7
          [:C :F :G :C :C :F :G :C]            ; chorus 8
          [:C :C :F :C :G :G :C :C]            ; verse2 8
          [:C :F :G :C :C :F :G :C]            ; chorus 8
          [:C :F :G :C :C :C]))                ; outro 6

(def accomp-plan
  {:bpm bpm :beat-sec beat-sec :total-beats total-beats
   :bars (vec (map-indexed
               (fn [i ch]
                 {:start-beat (* i 4) :beats 4
                  :chord (chord-table ch) :bass (- (first (chord-table ch)) 24)})
               progression))})

;; --- 出力 ---------------------------------------------------------------------

(defn -main []
  (let [build-dir (or (first *command-line-args*) "build")
        repo-root (path/dirname (path/dirname (path/resolve "tools/compose_kazu.cljs")))
        _ (fs/mkdirSync build-dir #js {:recursive true})
        {:keys [valid? errors]} (song/validate song-spec)]
    (when-not valid?
      (js/console.error "song spec INVALID:" (pr-str errors))
      (js/process.exit 1))
    ;; song EDN（リポジトリの content/ に正本として置く）
    (fs/mkdirSync (path/join repo-root "content") #js {:recursive true})
    (fs/writeFileSync (path/join repo-root "content" "kazu-no-uta.edn")
                      (str ";; generated by tools/compose_kazu.cljs — ADR-2607164500 Phase A\n"
                           (pr-str song-spec) "\n"))
    ;; scores.json: [{:id :singer :offset-sec :score}]
    (let [scores
          (for [[idx [kind _len lines]] (map-indexed vector sections)
                singer [:melo :popo]
                :let [ls (filter #(= singer (:line/singer %)) lines)
                      score (build-score ls)]
                :when score]
            {:id (str "s" idx "-" (name kind) "-" (name singer))
             :singer (name singer)
             :offset-sec (- (* (section-starts idx) beat-sec) pre-roll-sec)
             :score score})
          spoken
          (for [[idx [_kind _len lines]] (map-indexed vector sections)
                l lines
                :when (= :spoken (:line/kind l))]
            {:text (str (:line/lyric l) "!")
             :singer (name (:line/singer l))
             :offset-sec (* (+ (section-starts idx) (:line/start-beat l)) beat-sec)})
          timeline
          {:fps 30 :bpm bpm :beat-sec beat-sec
           :total-sec (* total-beats beat-sec)
           :sections (vec (map-indexed
                           (fn [idx [kind len _]]
                             {:kind (name kind)
                              :start-sec (* (section-starts idx) beat-sec)
                              :dur-sec (* len beat-sec)})
                           sections))
           :apples (vec (concat
                         (for [i (range 5)]
                           {:n (inc i) :at-sec (* (+ (section-starts 1) (* i 4)) beat-sec)})
                         (for [i (range 5)]
                           {:n (+ 6 i) :at-sec (* (+ (section-starts 3) (* i 4)) beat-sec)})))
           :outro-counts (vec (for [[b _ text] outro-spoken]
                                {:text text
                                 :at-sec (* (+ (section-starts 5) b) beat-sec)}))}]
      (fs/writeFileSync (path/join build-dir "scores.json")
                        (js/JSON.stringify (clj->js scores) nil 1))
      (fs/writeFileSync (path/join build-dir "spoken.json")
                        (js/JSON.stringify (clj->js spoken) nil 1))
      (fs/writeFileSync (path/join build-dir "accomp.json")
                        (js/JSON.stringify (clj->js accomp-plan) nil 1))
      (fs/writeFileSync (path/join build-dir "timeline.json")
                        (js/JSON.stringify (clj->js timeline) nil 1))
      (println "song spec valid. total" total-beats "beats ="
               (* total-beats beat-sec) "sec;"
               (count scores) "scores," (count spoken) "spoken lines"))))

(-main)
