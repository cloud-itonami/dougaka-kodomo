(ns kodomo.pipeline
  "produce パイプラインの stage-order と advance reducer。
   yukkuri graphs/produce.cljc と同型の純データ骨格 — 各 stage は
   実行計画（request spec / ffmpeg plan / gate 判定）を返すだけで IO しない。
   実行は :exec 側（murakumo engines / renderer-mac / YouTube client）が担う。"
  (:require [kodomo.safety :as safety]
            [kodomo.song :as song]))

(def stage-order
  "generate-song の後の 4 asset stage は並列実行可（yukkuri asset-stage-jobs と同型）。
   :score-safety は HARD gate — fail は :hold で停止し、advisory 続行しない。"
  [:compose            ; curriculum topic 選定 → project 起票
   :generate-song      ; song spec（歌詞 + メロディ EDN + storyboard）
   :synthesize-vocals  ; 歌唱合成（VOICEVOX song 経路）+ 話しパート speech
   :generate-visual    ; 背景・小物（ComfyUI + character LoRA）
   :generate-character ; キャラ表情/ポーズシート（reference sheets 準拠）
   :arrange-music      ; 伴奏編曲（ongaku compose + murakumo music）
   :compose-scene      ; 拍同期ループアニメ plan（cutout layer + motion cue）
   :render-video       ; renderer-mac ffmpeg fleet（dougaka contract）
   :score-safety       ; kids-safety HARD gate（kodomo.safety/gate）
   :localize           ; 多言語展開（per-language 歌詞→歌唱→字幕）
   :publish            ; YouTube madeForKids=true + cadence 投稿
   :audit])            ; 生成イベント台帳 append

(def parallel-asset-stages
  #{:synthesize-vocals :generate-visual :generate-character :arrange-music})

(defn next-stage [current]
  (->> stage-order (drop-while #(not= % current)) second))

(defn advance
  "1 stage 分 state を進める reducer。
   state = {:stage kw :song spec :facts map :status :running|:held|:done ...}"
  [{:keys [stage] :as state}]
  (case stage
    :generate-song
    (let [{:keys [valid? errors]} (song/validate (:song state))]
      (if valid?
        (assoc state :stage (next-stage stage))
        (assoc state :status :rejected :errors errors)))

    :score-safety
    (let [{:keys [decision failed]} (safety/gate (:facts state))]
      (if (= :publish decision)
        (assoc state :stage (next-stage stage) :gate :green)
        (assoc state :status :held :gate :flag :failed failed)))

    :audit
    (assoc state :status :done)

    ;; その他の stage は実行計画を :exec 側が消費した前提で前進のみ
    (if-let [nxt (next-stage stage)]
      (assoc state :stage nxt)
      (assoc state :status :done))))

(defn run-plan
  "全 stage を（gate 停止まで）畳み込む。テスト/ドライラン用。"
  [initial-state]
  (loop [state (assoc initial-state :stage (first stage-order) :status :running)]
    (if (not= :running (:status state))
      state
      (let [state' (advance state)]
        (cond
          (not= :running (:status state')) state'
          (= (:stage state') (:stage state)) (assoc state' :status :stuck)
          :else (recur state'))))))
