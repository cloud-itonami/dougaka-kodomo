;; どうぶつのこえ 映像レンダラ(ADR-2607164500)。
;;   nbb tools/render_doubutsu_video.cljs <build-dir>
;; どうぶつカード(名前+簡易フェイス)が歌詞と同期して出現。キャストは共有。
(ns render-doubutsu-video
  (:require ["fs" :as fs] ["path" :as path] ["child_process" :as cp] [kotoba.lang.text :as str]))

(def build-dir (or (first *command-line-args*) "build"))
(def W 1280) (def H 720) (def beat (/ 60.0 100))

(defn sh [cmd args]
  (cp/execFileSync cmd (clj->js (map str args)) #js {:stdio #js ["ignore" "ignore" "pipe"]}))
(defn write-svg [name svg] (fs/writeFileSync (path/join build-dir name) svg))
(defn png [svg-name png-name w]
  (sh "rsvg-convert" ["-w" w "-o" (path/join build-dir png-name) (path/join build-dir svg-name)]))

;; --- characters (共有キャスト) ----------------------------------------------
(def melo-svg
  (str "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 360 460'>"
       "<circle cx='180' cy='150' r='118' fill='#F4813F'/><circle cx='180' cy='168' r='94' fill='#FFE3C9'/>"
       "<circle cx='128' cy='96' r='46' fill='#F4813F'/><circle cx='180' cy='84' r='50' fill='#F4813F'/>"
       "<circle cx='232' cy='96' r='46' fill='#F4813F'/>"
       "<circle cx='145' cy='178' r='11' fill='#3A2B22'/><circle cx='149' cy='174' r='3.5' fill='#fff'/>"
       "<circle cx='215' cy='178' r='11' fill='#3A2B22'/><circle cx='219' cy='174' r='3.5' fill='#fff'/>"
       "<circle cx='122' cy='208' r='13' fill='#FFB3B3' opacity='.75'/><circle cx='238' cy='208' r='13' fill='#FFB3B3' opacity='.75'/>"
       "<path d='M158,208 Q180,228 202,208' stroke='#B5651D' stroke-width='6' fill='none' stroke-linecap='round'/>"
       "<rect x='118' y='258' width='124' height='132' rx='30' fill='#FFD84D'/>"
       "<rect x='140' y='258' width='18' height='40' fill='#FFC61A'/><rect x='202' y='258' width='18' height='40' fill='#FFC61A'/>"
       "<circle cx='98' cy='310' r='20' fill='#FFE3C9'/><circle cx='262' cy='310' r='20' fill='#FFE3C9'/>"
       "<rect x='142' y='388' width='28' height='42' rx='12' fill='#FFE3C9'/><rect x='190' y='388' width='28' height='42' rx='12' fill='#FFE3C9'/></svg>"))
(def popo-svg
  (str "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 280 350'>"
       "<path d='M140,18 Q150,40 140,52' stroke='#C98A5B' stroke-width='7' fill='none' stroke-linecap='round'/>"
       "<circle cx='140' cy='120' r='76' fill='#FFE3C9'/>"
       "<circle cx='112' cy='120' r='9' fill='#3A2B22'/><circle cx='115' cy='117' r='3' fill='#fff'/>"
       "<circle cx='168' cy='120' r='9' fill='#3A2B22'/><circle cx='171' cy='117' r='3' fill='#fff'/>"
       "<circle cx='95' cy='145' r='11' fill='#FFB3B3' opacity='.75'/><circle cx='185' cy='145' r='11' fill='#FFB3B3' opacity='.75'/>"
       "<ellipse cx='140' cy='155' rx='11' ry='13' fill='#E0705A'/>"
       "<rect x='82' y='198' width='116' height='108' rx='28' fill='#B8EAD9'/>"
       "<circle cx='66' cy='240' r='16' fill='#FFE3C9'/><circle cx='214' cy='240' r='16' fill='#FFE3C9'/>"
       "<rect x='102' y='306' width='24' height='34' rx='11' fill='#FFE3C9'/><rect x='152' y='306' width='24' height='34' rx='11' fill='#FFE3C9'/></svg>"))
(def mimi-svg
  (str "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 300 430'>"
       "<rect x='100' y='8' width='38' height='128' rx='19' fill='#fff' stroke='#E3D9CF' stroke-width='3'/>"
       "<rect x='162' y='8' width='38' height='128' rx='19' fill='#fff' stroke='#E3D9CF' stroke-width='3'/>"
       "<rect x='111' y='26' width='16' height='92' rx='8' fill='#FFC7D6'/><rect x='173' y='26' width='16' height='92' rx='8' fill='#FFC7D6'/>"
       "<circle cx='150' cy='205' r='82' fill='#fff' stroke='#E3D9CF' stroke-width='3'/>"
       "<circle cx='120' cy='196' r='9' fill='#3A2B22'/><circle cx='123' cy='193' r='3' fill='#fff'/>"
       "<circle cx='180' cy='196' r='9' fill='#3A2B22'/><circle cx='183' cy='193' r='3' fill='#fff'/>"
       "<path d='M143,222 L157,222 L150,232 Z' fill='#FF9FB6'/>"
       "<circle cx='100' cy='222' r='11' fill='#FFC7D6' opacity='.8'/><circle cx='200' cy='222' r='11' fill='#FFC7D6' opacity='.8'/>"
       "<ellipse cx='150' cy='345' rx='68' ry='72' fill='#fff' stroke='#E3D9CF' stroke-width='3'/>"
       "<ellipse cx='150' cy='360' rx='40' ry='46' fill='#FFF4E8'/></svg>"))

;; --- どうぶつカード(名前+簡易フェイス) --------------------------------------
;; [背景色 顔SVG片(cx=150,cy=115 中心)]
(def animal-art
  {"いぬ"   ["#C79A6B"
             "<ellipse cx='95' cy='75' rx='24' ry='40' fill='#8A5A33'/><ellipse cx='205' cy='75' rx='24' ry='40' fill='#8A5A33'/><circle cx='150' cy='115' r='62' fill='#E8C79A'/><circle cx='128' cy='108' r='8' fill='#3A2B22'/><circle cx='172' cy='108' r='8' fill='#3A2B22'/><ellipse cx='150' cy='135' rx='16' ry='11' fill='#5A4636'/>"]
   "ねこ"   ["#B4A7D6"
             "<path d='M98,70 L118,110 L82,105 Z' fill='#9C8AC9'/><path d='M202,70 L182,110 L218,105 Z' fill='#9C8AC9'/><circle cx='150' cy='118' r='60' fill='#D8CCF0'/><circle cx='130' cy='112' r='7' fill='#3A2B22'/><circle cx='170' cy='112' r='7' fill='#3A2B22'/><path d='M143,128 L157,128 L150,136 Z' fill='#E86A8A'/>"]
   "うし"   ["#F0C0C8"
             "<ellipse cx='90' cy='80' rx='22' ry='16' fill='#E39AA8'/><ellipse cx='210' cy='80' rx='22' ry='16' fill='#E39AA8'/><circle cx='150' cy='118' r='60' fill='#FFF'/><circle cx='128' cy='105' r='16' fill='#C8C0BC'/><circle cx='128' cy='105' r='7' fill='#3A2B22'/><circle cx='172' cy='108' r='7' fill='#3A2B22'/><ellipse cx='150' cy='140' rx='30' ry='20' fill='#F0B8C0'/>"]
   "ぶた"   ["#FFC7D6"
             "<circle cx='150' cy='115' r='62' fill='#FFB3C7'/><path d='M112,80 L100,60 L124,72 Z' fill='#FF9FB6'/><path d='M188,80 L200,60 L176,72 Z' fill='#FF9FB6'/><circle cx='130' cy='108' r='7' fill='#3A2B22'/><circle cx='170' cy='108' r='7' fill='#3A2B22'/><ellipse cx='150' cy='135' rx='26' ry='18' fill='#FF8FB0'/><circle cx='142' cy='135' r='5' fill='#D96A8A'/><circle cx='158' cy='135' r='5' fill='#D96A8A'/>"]
   "ひよこ" ["#FFE9A8"
             "<circle cx='150' cy='118' r='62' fill='#FFD84D'/><circle cx='130' cy='110' r='7' fill='#3A2B22'/><circle cx='170' cy='110' r='7' fill='#3A2B22'/><path d='M138,128 L162,128 L150,142 Z' fill='#F5943B'/>"]})

(defn animal-svg [nm]
  (let [[bg face] (animal-art nm)]
    (str "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 300 320'>"
         "<rect x='8' y='8' width='284' height='260' rx='40' fill='" bg "'/>"
         face
         "<text x='150' y='240' text-anchor='middle' font-family='Hiragino Sans, sans-serif'"
         " font-weight='800' font-size='48' fill='#3A2B22'>" nm "</text></svg>")))

(defn bg-base [& body]
  (str "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 1280 720'>"
       "<defs><linearGradient id='sky' x1='0' y1='0' x2='0' y2='1'>"
       "<stop offset='0' stop-color='#CDEBD6'/><stop offset='1' stop-color='#FFF7E6'/></linearGradient></defs>"
       "<rect width='1280' height='720' fill='url(#sky)'/>"
       "<circle cx='1110' cy='104' r='60' fill='#FFD86B'/>"
       "<g fill='#fff' opacity='.9'><ellipse cx='250' cy='120' rx='95' ry='34'/><ellipse cx='820' cy='86' rx='78' ry='26'/></g>"
       "<ellipse cx='320' cy='800' rx='560' ry='210' fill='#9FD98A'/><ellipse cx='1030' cy='820' rx='600' ry='230' fill='#8CD07F'/>"
       (apply str body) "</svg>"))
(def bg-title
  (bg-base
   "<text x='640' y='330' text-anchor='middle' font-family='Hiragino Sans, sans-serif'"
   " font-weight='800' font-size='104' fill='#FF8A5B' stroke='#fff' stroke-width='14' paint-order='stroke'>どうぶつのこえ</text>"
   "<text x='640' y='410' text-anchor='middle' font-family='Hiragino Sans, sans-serif'"
   " font-weight='600' font-size='44' fill='#4A6FA5' stroke='#fff' stroke-width='8' paint-order='stroke'>わんわん・にゃあ・もー!</text>"))
(def bg-meadow (bg-base))
(def bg-chorus
  (str "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 1280 720'>"
       "<defs><linearGradient id='pk' x1='0' y1='0' x2='0' y2='1'>"
       "<stop offset='0' stop-color='#FFF2E0'/><stop offset='1' stop-color='#E7F5E9'/></linearGradient></defs>"
       "<rect width='1280' height='720' fill='url(#pk)'/>"
       "<path d='M240,720 A400,400 0 0 1 1040,720' fill='none' stroke='#8CD07F' stroke-width='30'/>"
       "<path d='M288,720 A352,352 0 0 1 992,720' fill='none' stroke='#FFD84D' stroke-width='30'/>"
       "<path d='M336,720 A304,304 0 0 1 944,720' fill='none' stroke='#F5943B' stroke-width='30'/>"
       "<text x='640' y='150' text-anchor='middle' font-family='Hiragino Sans, sans-serif'"
       " font-weight='800' font-size='76' fill='#FF7FA8' stroke='#fff' stroke-width='12' paint-order='stroke'>なきごえ たのしいな!</text></svg>"))

(defn gen-assets! []
  (write-svg "melo.svg" melo-svg) (write-svg "popo.svg" popo-svg) (write-svg "mimi.svg" mimi-svg)
  (write-svg "bg-title.svg" bg-title) (write-svg "bg-meadow.svg" bg-meadow) (write-svg "bg-chorus.svg" bg-chorus)
  (png "bg-title.svg" "bg-title.png" W) (png "bg-meadow.svg" "bg-meadow.png" W) (png "bg-chorus.svg" "bg-chorus.png" W)
  (doseq [[svg out w] [["melo.svg" "melo-m.png" 260] ["melo.svg" "melo-l.png" 330]
                       ["popo.svg" "popo-m.png" 200] ["popo.svg" "popo-l.png" 250]
                       ["mimi.svg" "mimi-m.png" 220] ["mimi.svg" "mimi-l.png" 280]]]
    (png svg out w))
  (doseq [nm (keys animal-art)]
    (let [f (str "an-" (hash nm))]
      (write-svg (str f ".svg") (animal-svg nm))
      (png (str f ".svg") (str f "-m.png") 220)))
  (println "assets rendered"))
(defn an-png [nm] (str "an-" (hash nm) "-m.png"))

(defn bounce [y amp] (str y "-" amp "*abs(sin(PI*t/" beat "))"))
(defn bob [y amp phase] (str y "+" amp "*sin(2*PI*t/1.2+" phase ")"))

(defn render-section! [out bg dur overlays]
  (let [inputs (concat ["-loop" "1" "-t" dur "-i" (path/join build-dir bg)]
                       (mapcat (fn [{:keys [img]}] ["-loop" "1" "-t" dur "-i" (path/join build-dir img)]) overlays))
        chain (str/join ";"
                        (map-indexed
                         (fn [i {:keys [x y-expr enable]}]
                           (str "[v" i "][" (inc i) ":v]overlay=x=" x ":y=" y-expr
                                (when enable (str ":enable='" enable "'"))
                                (if (= i (dec (count overlays))) ",fps=30[vout]" (str "[v" (inc i) "]"))))
                         overlays))
        fg (str "[0:v]null[v0];" chain)]
    (sh "ffmpeg" (concat inputs ["-filter_complex" fg "-map" "[vout]" "-c:v" "libx264"
                                 "-preset" "veryfast" "-crf" "20" "-pix_fmt" "yuv420p" "-y"
                                 (path/join build-dir out)]))
    (println out "rendered")))

(defn -main []
  (gen-assets!)
  (let [tl (js->clj (js/JSON.parse (fs/readFileSync (path/join build-dir "timeline.json") "utf8")) :keywordize-keys true)
        secs (:sections tl)
        animals ["いぬ" "ねこ" "うし" "ぶた" "ひよこ"]
        x-of (fn [i n] (+ 90 (* (/ (- W 380) (max 1 (dec n))) i)))
        verse-chars [{:img "melo-m.png" :x 30 :y-expr (bounce 340 22)}
                     {:img "mimi-m.png" :x 1010 :y-expr (bounce 350 26)}]
        chorus-chars [{:img "melo-l.png" :x 210 :y-expr (bounce 280 30)}
                      {:img "popo-l.png" :x 540 :y-expr (bounce 360 26)}
                      {:img "mimi-l.png" :x 840 :y-expr (bounce 300 32)}]
        verse (fn [n]
                (concat verse-chars
                        (map-indexed (fn [i nm]
                                       {:img (an-png nm) :x (x-of i 5) :y-expr (bob 130 8 i)
                                        :enable (str "gte(t," (* i 4 beat) ")")}) animals)))]
    (render-section! "sec0.mp4" "bg-title.png" (:dur-sec (nth secs 0))
                     [{:img "melo-m.png" :x 300 :y-expr (bounce 440 22)}
                      {:img "popo-m.png" :x 590 :y-expr (bounce 480 18)}
                      {:img "mimi-m.png" :x 830 :y-expr (bounce 450 26)}])
    (render-section! "sec1.mp4" "bg-meadow.png" (:dur-sec (nth secs 1)) (verse 5))
    (render-section! "sec2.mp4" "bg-chorus.png" (:dur-sec (nth secs 2)) chorus-chars)
    (render-section! "sec3.mp4" "bg-meadow.png" (:dur-sec (nth secs 3)) (verse 5))
    (render-section! "sec4.mp4" "bg-chorus.png" (:dur-sec (nth secs 4)) chorus-chars)
    (render-section! "sec5.mp4" "bg-meadow.png" (:dur-sec (nth secs 5))
                     (concat [{:img "melo-m.png" :x 160 :y-expr (bounce 350 22)}
                              {:img "mimi-m.png" :x 980 :y-expr (bounce 360 26)}]
                             (map-indexed (fn [i nm] {:img (an-png nm) :x (+ 70 (* 230 i)) :y-expr (bob 120 6 i)})
                                          animals)))
    (fs/writeFileSync (path/join build-dir "concat.txt")
                      (str/join "\n" (for [i (range 6)] (str "file 'sec" i ".mp4'"))))
    (sh "ffmpeg" ["-f" "concat" "-safe" "0" "-i" (path/join build-dir "concat.txt")
                  "-c" "copy" "-y" (path/join build-dir "video-noaudio.mp4")])
    (sh "ffmpeg" ["-i" (path/join build-dir "video-noaudio.mp4") "-i" (path/join build-dir "audio-final.wav")
                  "-c:v" "copy" "-c:a" "aac" "-b:a" "192k" "-movflags" "+faststart" "-shortest" "-y"
                  (path/join build-dir "doubutsu-no-uta.mp4")])
    (println "doubutsu-no-uta.mp4 done")))

(-main)
