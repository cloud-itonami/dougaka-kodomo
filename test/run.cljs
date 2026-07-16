;; nbb テストランナー（第一経路。JVM 互換は clojure -M:test）
;;   nbb --classpath src:test test/run.cljs
(ns run
  (:require [clojure.test :as t]
            [kodomo.song-test]
            [kodomo.safety-test]))

(defmethod t/report [:cljs.test/default :end-run-tests] [m]
  (when-not (t/successful? m)
    (set! (.-exitCode js/process) 1)))

(t/run-tests 'kodomo.song-test 'kodomo.safety-test)
