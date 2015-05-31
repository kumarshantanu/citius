(ns citius.core-test
  (:require
    [clojure.string :as s]
    [clojure.test :refer [deftest use-fixtures]]
    [citius.core :as c]))


(use-fixtures :once (c/make-bench-wrapper ["pig" "horse" "cheetah"] {:chart-title "Animals"
                                                                     :chart-filename "bench-animals.png"}))


(deftest test-string
  (let [nums (range 10)]
    (c/compare-perf "Joining string tokens"
      (let [delimited (interpose ", " nums)]
        (reduce str delimited))
      (->> (interpose ", " nums)
        (apply str))
      (s/join ", " nums))))


(deftest test-sum
  (let [n 100
        nums (range n)]
    (c/compare-perf "Summing numbers"
      (->> (map inc nums)
        (reduce +))
      (+ ^long (reduce + nums) n)
      (+ ^long (/ (* n (inc n)) 2) n))))