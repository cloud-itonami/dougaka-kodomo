(ns kodomo.safety
  "kids-safety HARD gate — 幼児向けチャンネルの公開前検査。
   すべて決定論チェック（LLM 判定はここに置かない — 「計測されない
   メトリクス＝劇場」問題の回避。design-quality の audit.cljc と同じ思想）。
   入力は計測済みの facts map（実測は :exec 側が ffmpeg/loudnorm 等で行う）。
   1つでも fail があれば :hold（auto-publish しない）。
   escalate-on-flag の publish gate 骨格は aozora ADR-2607162200 と同型。"
  (:require [kotoba.lang.text :as str]))

;; --- 個別チェック（それぞれ {:check .. :ok? bool :detail ..} を返す） --------

(defn check-loudness
  "YouTube 正規化 (-14 LUFS) を前提に、幼児向けはピークの暴れを厳しめに見る。
   integrated: -20..-12 LUFS、true peak <= -1.0 dBTP。"
  [{:keys [integrated-lufs true-peak-dbtp]}]
  {:check :loudness
   :ok? (boolean (and (number? integrated-lufs) (number? true-peak-dbtp)
                      (<= -20.0 integrated-lufs -12.0)
                      (<= true-peak-dbtp -1.0)))
   :detail {:integrated-lufs integrated-lufs :true-peak-dbtp true-peak-dbtp}})

(defn check-duration
  "単発ソング 60〜300 秒、コンピレーション 900〜3600 秒。"
  [{:keys [duration-sec kind] :or {kind :single}}]
  (let [[lo hi] (case kind :compilation [900 3600] [60 300])]
    {:check :duration
     :ok? (boolean (and (number? duration-sec) (<= lo duration-sec hi)))
     :detail {:duration-sec duration-sec :kind kind :allowed [lo hi]}}))

(defn check-flash
  "光過敏対策: 1 秒あたりの大輝度変化（フラッシュ）回数 <= 3。
   flashes-per-sec は :exec 側がフレーム間 luma delta から計測して渡す。"
  [{:keys [max-flashes-per-sec]}]
  {:check :flash
   :ok? (boolean (and (number? max-flashes-per-sec) (<= max-flashes-per-sec 3.0)))
   :detail {:max-flashes-per-sec max-flashes-per-sec}})

(defn check-vocab
  "歌詞の語彙が対象年齢語彙リスト（curriculum.edn の :curriculum/lexicon）に
   どれだけ収まっているか。未知語率 <= 0.2。"
  [{:keys [lyric-tokens lexicon]}]
  (let [tokens (vec lyric-tokens)
        n (count tokens)
        unknown (if (zero? n) [] (vec (remove (set lexicon) tokens)))
        ratio (if (zero? n) 1.0 (double (/ (count unknown) n)))]
    {:check :vocab
     :ok? (and (pos? n) (<= ratio 0.2))
     :detail {:unknown-ratio ratio :unknown-sample (vec (take 10 unknown))}}))

(defn check-metadata
  "COPPA / YouTube Kids: madeForKids=true 必須、description に外部リンク禁止、
   VOICEVOX 使用時はクレジット必須。"
  [{:keys [made-for-kids? description uses-voicevox?]}]
  (let [desc (str description)
        errors (cond-> []
                 (not (true? made-for-kids?)) (conj :made-for-kids-missing)
                 (re-find #"https?://" desc) (conj :external-link-in-description)
                 (and uses-voicevox? (not (str/includes? desc "VOICEVOX")))
                 (conj :voicevox-credit-missing))]
    {:check :metadata :ok? (empty? errors) :detail {:errors errors}}))

(defn check-ip-similarity
  "既存商用幼児 IP との類似申告。:exec 側の類似判定（画像 embedding /
   人手レビュー）の結果 flags を受け取り、非空なら fail。"
  [{:keys [ip-flags]}]
  {:check :ip-similarity :ok? (empty? ip-flags) :detail {:flags (vec ip-flags)}})

;; --- gate -------------------------------------------------------------------

(def all-checks
  [check-loudness check-duration check-flash check-vocab
   check-metadata check-ip-similarity])

(defn gate
  "facts map を全チェックに通す。
   {:decision :publish|:hold :results [..] :failed [..]} を返す。
   :hold の場合は owner escalate（auto-publish しない）。"
  [facts]
  (let [results (mapv #(% facts) all-checks)
        failed (vec (remove :ok? results))]
    {:decision (if (empty? failed) :publish :hold)
     :results results
     :failed failed}))
