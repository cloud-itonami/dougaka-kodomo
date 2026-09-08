(ns kodomo.song
  "幼児向け知育ソングの song spec — 純データ planner + 検証。
   yukkuri の generate_script（L/R 掛け合い台本）に相当する層を、
   歌詞 + メロディ EDN + 絵コンテ（storyboard）に置き換える。
   外部 IO はしない: LLM 呼び出しは request spec を返すだけで、
   実行は :exec 側（murakumo text engine）が担う。"
  (:require [kotoba.lang.text :as str]))

;; --- song spec shape -------------------------------------------------------
;; {:song/id        "abc-song-001"
;;  :song/topic     :curriculum/abc          ; resources/curriculum.edn の topic id
;;  :song/language  "ja"
;;  :song/bpm       96
;;  :song/key       :C
;;  :song/sections  [{:section/kind :verse   ; :verse :chorus :bridge :call-response
;;                    :section/lines
;;                    [{:line/lyric "きらきら ひかる"
;;                      :line/singer :melo   ; characters.edn の cast id
;;                      :line/notes [{:note/pitch :C4 :note/beats 0.5} ...]
;;                      :line/motion :bounce}]}]}   ; 拍同期ループアニメの motion cue

(def singable-range
  "幼児が一緒に歌える音域（おおよそ C4〜D5）。この外の pitch は検証で弾く。"
  #{:C4 :C#4 :D4 :D#4 :E4 :F4 :F#4 :G4 :G#4 :A4 :A#4 :B4 :C5 :C#5 :D5})

(def max-bpm 120)
(def min-bpm 70)

(defn- section-lyrics [section]
  (map :line/lyric (:section/lines section)))

(defn repetition-ratio
  "歌詞行のうち、他の行と完全一致で反復されている行の割合。
   幼児ソングは反復が学習装置なので高いほど良い（gate は >= 0.3 を要求）。"
  [song]
  (let [lines (mapcat section-lyrics (:song/sections song))
        n (count lines)]
    (if (zero? n)
      0.0
      (let [freq (frequencies lines)
            repeated (count (filter #(> (freq %) 1) lines))]
        (double (/ repeated n))))))

(defn out-of-range-pitches
  "singable-range 外の pitch の一覧（空なら OK）。"
  [song]
  (->> (:song/sections song)
       (mapcat :section/lines)
       (mapcat :line/notes)
       (map :note/pitch)
       (remove singable-range)
       distinct
       vec))

(defn validate
  "song spec の決定論検証。{:valid? bool :errors [..]} を返す。"
  [song]
  (let [errors
        (cond-> []
          (str/blank? (str (:song/id song)))
          (conj {:error :missing-id})

          (not (<= min-bpm (or (:song/bpm song) 0) max-bpm))
          (conj {:error :bpm-out-of-range :bpm (:song/bpm song)
                 :allowed [min-bpm max-bpm]})

          (empty? (:song/sections song))
          (conj {:error :no-sections})

          (not-any? #(= :chorus (:section/kind %)) (:song/sections song))
          (conj {:error :no-chorus})

          (seq (out-of-range-pitches song))
          (conj {:error :pitch-out-of-singable-range
                 :pitches (out-of-range-pitches song)})

          (< (repetition-ratio song) 0.3)
          (conj {:error :insufficient-repetition
                 :ratio (repetition-ratio song)
                 :minimum 0.3}))]
    {:valid? (empty? errors) :errors errors}))

;; --- planner（LLM request spec を返すだけ。IO しない） ----------------------

(defn plan-song-request
  "curriculum topic + channel cast から、song spec を生成させる LLM request spec
   を組み立てる。yukkuri generate_script と同じ {:system :user :model-hint} 形。"
  [{:keys [topic cast language] :or {language "ja"}}]
  {:system (str "あなたは幼児向け知育ソングの作詞作曲家です。"
                "対象は0〜4歳。短い語・反復・call-and-response を多用し、"
                "サビ(:chorus)を必ず含め、音域は C4〜D5 に収めてください。"
                "出力は EDN の song spec 1つだけ。")
   :user (str "topic: " (pr-str (:curriculum/id topic))
              "\nねらい: " (:curriculum/goal topic)
              "\n言語: " language
              "\n歌い手 cast: " (pr-str (mapv :cast/id cast))
              "\nbpm は " min-bpm "〜" max-bpm " の範囲。")
   :model-hint "murakumo:inference/text"})
