(ns kodomo.song-test
  (:require [clojure.test :refer [deftest is testing]]
            [kodomo.song :as song]))

(def valid-song
  {:song/id "test-abc-001"
   :song/topic :abc
   :song/language "ja"
   :song/bpm 96
   :song/key :C
   :song/sections
   [{:section/kind :verse
     :section/lines
     [{:line/lyric "えー びー しー" :line/singer :melo
       :line/notes [{:note/pitch :C4 :note/beats 1} {:note/pitch :D4 :note/beats 1}]
       :line/motion :bounce}
      {:line/lyric "いっしょに うたおう" :line/singer :melo
       :line/notes [{:note/pitch :E4 :note/beats 1}]
       :line/motion :clap}]}
    {:section/kind :chorus
     :section/lines
     [{:line/lyric "えー びー しー" :line/singer :popo
       :line/notes [{:note/pitch :C4 :note/beats 1}]
       :line/motion :bounce}
      {:line/lyric "えー びー しー" :line/singer :melo
       :line/notes [{:note/pitch :C5 :note/beats 1}]
       :line/motion :wave}]}]})

(deftest validate-accepts-valid-song
  (let [{:keys [valid? errors]} (song/validate valid-song)]
    (is valid? (pr-str errors))))

(deftest validate-rejects-out-of-range-pitch
  (let [bad (assoc-in valid-song
                      [:song/sections 0 :section/lines 0 :line/notes 0 :note/pitch]
                      :G5)
        {:keys [valid? errors]} (song/validate bad)]
    (is (not valid?))
    (is (some #(= :pitch-out-of-singable-range (:error %)) errors))))

(deftest validate-rejects-missing-chorus
  (let [bad (update valid-song :song/sections
                    (fn [ss] (mapv #(assoc % :section/kind :verse) ss)))
        {:keys [valid? errors]} (song/validate bad)]
    (is (not valid?))
    (is (some #(= :no-chorus (:error %)) errors))))

(deftest validate-requires-repetition
  (let [bad (-> valid-song
                (assoc-in [:song/sections 1 :section/lines 0 :line/lyric] "あか")
                (assoc-in [:song/sections 1 :section/lines 1 :line/lyric] "あお"))
        {:keys [valid? errors]} (song/validate bad)]
    (is (not valid?))
    (is (some #(= :insufficient-repetition (:error %)) errors))))

(deftest validate-rejects-fast-bpm
  (let [{:keys [valid? errors]} (song/validate (assoc valid-song :song/bpm 160))]
    (is (not valid?))
    (is (some #(= :bpm-out-of-range (:error %)) errors))))

(deftest plan-song-request-shape
  (testing "planner は IO せず request spec を返す"
    (let [req (song/plan-song-request
               {:topic {:curriculum/id :abc :curriculum/goal "アルファベット"}
                :cast [{:cast/id :melo}]})]
      (is (string? (:system req)))
      (is (string? (:user req)))
      (is (= "murakumo:inference/text" (:model-hint req))))))
