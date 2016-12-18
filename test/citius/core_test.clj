;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns citius.core-test
  (:require
    [clojure.string :as s]
    [clojure.test :refer [deftest use-fixtures]]
    [citius.core :as c]))


(use-fixtures :once (c/make-bench-wrapper ["pig" "horse" "cheetah"]
                      {:chart-title "Animals"
                       :chart-filename (format "target/bench-animals-clj-%s.png" c/clojure-version-str)}))


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