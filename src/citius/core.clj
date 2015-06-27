(ns citius.core
  (:require
    [clojure.test :refer :all]
    [clojure.pprint    :as pp]
    [clojure.string    :as s]
    [cljfreechart.core :as b]
    [citius.internal   :as i]))


(def ^String clojure-version-str (->> [:major :minor :incremental :qualifier]
                                   (map *clojure-version*)
                                   (keep identity)
                                   (s/join \.)))


(defn make-bench-wrapper
  "Given a vector of unique labels and options, return an arity-1 fn that accepts an arity-0 fn and executes it in the
  context of the labels and options - few options may be overridden via system property or environment variable.
  Option            Type                         Default      System property         Environment variable
  :chart-title      stringable                   \"Latency\"
  :chart-filename   bar-chart image filename
  :chart-width      natural number                1280
  :chart-height     natural number                 800
  :chart-time-unit  Either of :minutes, :nanos,  :nanos
                    :micros, :millis, :seconds
  :concurrency      list of ints (for each expr)              citius_concurrency      CITIUS_CONCURRENCY (comma delimit)
  :colorize?        true or false                 true        citius_colorize         CITIUS_COLORIZE
  :criterium-output :tabular or true or false    :tabular     citius_criterium_output CITIUS_CRITERIUM_OUTPUT
  :quick-bench?     true or false                 true        citius_quick_bench      CITIUS_QUICK_BENCH
  Example:
  (clojure.test/use-fixtures :once
    (make-bench-wrapper [\"pig\" \"horse\" \"cheetah\"] {:chart-title \"Animals\"
                                                         :chart-filename \"bench-animals.png\"
                                                         :quick-bench? true}))"
  [labels options]
  (when-not (vector? labels)
    (throw (IllegalArgumentException.
             (str "Expected a vector of labels, but found (" (class labels) ") " (pr-str labels)))))
  (when-not (= (distinct labels) (list* labels))
    (throw (IllegalArgumentException.
             (str "Expected labels to be distinct, but found " (pr-str labels)))))
  (let [{:keys [chart-filename chart-title chart-width chart-height chart-time-unit]
         :or {chart-title "Latency"
              chart-width 1280
              chart-height 800
              chart-time-unit :nanos}} options]
    (fn [f]
      (binding [i/*labels* labels
                i/*options* options
                i/*bar-chart-data* (atom [])]
        (f)
        (when chart-filename
          (-> (let [raw-data @i/*bar-chart-data*
                    make-row (fn [k] (reduce (fn [x {:keys [bench-name] :as y}]
                                               (assoc x bench-name (get y k)))
                                       {} raw-data))]
                (->> (map make-row i/*labels*)
                  (map #(assoc %2 :name %1) i/*labels*)
                  vec))
            (b/make-category-dataset {:group-key :name})
            (b/make-bar-chart-3d (format "%s %s statistics (lower is better)"
                                   chart-title (if (i/option-quick-bench?) "quick-benchmark" "benchmark"))
              {:category-title "Test cases"
               :value-title (let [[_ unit] (i/time-factor chart-time-unit)]
                              (format "Latency in %s (lower is better)" unit))})
            (b/save-chart-as-file chart-filename {:width chart-width
                                                  :height chart-height})))))))


(defmacro with-bench-context
  "Evaluate body of code in a benchmark context.
  See: make-bench-wrapper"
  [labels options & body]
  `((make-bench-wrapper ~labels ~options) (fn [] ~@body)))


(defmacro compare-perf
  "Conduct a comparative benchmark for the given expressions using Criterium."
  [bench-name & exprs]
  `(do
     (i/echo "========== " ~bench-name " ==========")
     (let [result-and-reports# ~(-> (fn [index expr]
                                      `(i/measure ~index ~expr))
                                  (map-indexed exprs)
                                  vec)]
       ;; print comparative tabular report
       (let [criterium-output# (i/option-criterium-output)]
         (cond
           (= criterium-output#
             :tabular)          (->> result-and-reports#
                                  (map second)  ; get Criterium reports
                                  (map s/split-lines)
                                  (apply map (fn [& lines#] (->> (map s/trim lines#)
                                                              (zipmap i/*labels*))))
                                  (pp/print-table i/*labels*))
           criterium-output#    (->> result-and-reports#
                                  (map second)
                                  (map #(i/echo "----- " %1 " -----\n" %2) i/*labels*)
                                  dorun)))
       ;; print summary report
       (->> result-and-reports#
         (map first)  ; get Criterium result data
         (apply i/comparison-summary)
         i/echo)
       ;; save bar-chart data for this comparative benchmark
       (->> result-and-reports#
         (map first)
         (map :mean)
         (map first)
         (interleave i/*labels*)
         (apply i/save-bar-chart-data! ~bench-name)))))
