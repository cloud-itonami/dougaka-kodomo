(ns kodomo.safety-test
  (:require [clojure.test :refer [deftest is testing]]
            [kodomo.safety :as safety]
            [kodomo.pipeline :as pipeline]))

(def green-facts
  {:integrated-lufs -14.5
   :true-peak-dbtp -1.5
   :duration-sec 150
   :kind :single
   :max-flashes-per-sec 0.5
   :lyric-tokens ["えー" "びー" "しー" "うた"]
   :lexicon ["えー" "びー" "しー" "うた" "いっしょ"]
   :made-for-kids? true
   :description "ABCのうた | VOICEVOX:四国めたん"
   :uses-voicevox? true
   :ip-flags []})

(deftest gate-publishes-on-all-green
  (let [{:keys [decision failed]} (safety/gate green-facts)]
    (is (= :publish decision) (pr-str failed))))

(deftest gate-holds-on-loudness
  (let [{:keys [decision failed]} (safety/gate (assoc green-facts :integrated-lufs -8.0))]
    (is (= :hold decision))
    (is (some #(= :loudness (:check %)) failed))))

(deftest gate-holds-on-flash
  (let [{:keys [decision]} (safety/gate (assoc green-facts :max-flashes-per-sec 8))]
    (is (= :hold decision))))

(deftest gate-holds-on-missing-made-for-kids
  (let [{:keys [decision failed]} (safety/gate (assoc green-facts :made-for-kids? false))]
    (is (= :hold decision))
    (is (some #(= :metadata (:check %)) failed))))

(deftest gate-holds-on-external-link
  (let [{:keys [decision]} (safety/gate (assoc green-facts :description
                                               "みてね https://example.com VOICEVOX:x"))]
    (is (= :hold decision))))

(deftest gate-holds-on-voicevox-credit-missing
  (let [{:keys [decision]} (safety/gate (assoc green-facts :description "ABCのうた"))]
    (is (= :hold decision))))

(deftest gate-holds-on-unknown-vocab
  (let [{:keys [decision]} (safety/gate (assoc green-facts
                                               :lyric-tokens ["脆弱性" "サプライチェーン" "えー" "びー"]))]
    (is (= :hold decision))))

(deftest gate-holds-on-ip-flag
  (let [{:keys [decision]} (safety/gate (assoc green-facts :ip-flags [:looks-like-cocomelon-jj]))]
    (is (= :hold decision))))

(deftest compilation-duration-band
  (testing "コンピレーションは 900〜3600 秒帯"
    (is (:ok? (safety/check-duration {:duration-sec 1800 :kind :compilation})))
    (is (not (:ok? (safety/check-duration {:duration-sec 150 :kind :compilation}))))))

;; --- pipeline gate 統合 -------------------------------------------------------

(deftest pipeline-holds-at-safety-gate
  (let [end (pipeline/run-plan
             {:song {:song/id "x" :song/bpm 96
                     :song/sections
                     [{:section/kind :chorus
                       :section/lines [{:line/lyric "らら" :line/notes [{:note/pitch :C4}]}
                                       {:line/lyric "らら" :line/notes [{:note/pitch :C4}]}]}]}
              :facts (assoc green-facts :max-flashes-per-sec 9)})]
    (is (= :held (:status end)))
    (is (= :flag (:gate end)))))

(deftest pipeline-completes-on-green
  (let [end (pipeline/run-plan
             {:song {:song/id "x" :song/bpm 96
                     :song/sections
                     [{:section/kind :chorus
                       :section/lines [{:line/lyric "らら" :line/notes [{:note/pitch :C4}]}
                                       {:line/lyric "らら" :line/notes [{:note/pitch :C4}]}]}]}
              :facts green-facts})]
    (is (= :done (:status end)))
    (is (= :green (:gate end)))))
