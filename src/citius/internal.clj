(ns citius.internal
  (:require
    [clojure.string :as s]
    [clansi.core    :as a]
    [criterium.core :as c]))


;; ----- reporting and error reporting -----


(defn unexpected
  [msg value]
  (throw (IllegalArgumentException.
           ^String (format "Expected %s, but found (%s) %s" msg (class value) (pr-str value)))))


(defn echo
  [& args]
  (apply println "\n[citius] " args))


;; ----- options -----


(def ^:dynamic *labels* nil)


(def ^:dynamic *options* nil)


(def ^:dynamic *bar-chart-data* nil)


(defn option-colorize?
  []
  (if (contains? *options* :colorize?)
    (get *options* :colorize?)
    (if-let [^String colorize-prop (System/getProperty "citius_colorize")]
      (Boolean/parseBoolean colorize-prop)
      (if-let [^String colorize-env (System/getenv "CITIUS_COLORIZE")]
        (Boolean/parseBoolean colorize-env)
        true))))


(defn option-criterium-output
  []
  (if (contains? *options* :criterium-output)
    (:criterium-output *options*)
    :tabular))


(defn option-quick-bench?
  []
  (if (contains? *options* :quick?)
    (get *options* :quick?)
    (if-let [^String quick-bench-prop (System/getProperty "citius_quick_bench")]
      (Boolean/parseBoolean quick-bench-prop)
      (if-let [^String quick-bench-env (System/getenv "CITIUS_QUICK_BENCH")]
        (Boolean/parseBoolean quick-bench-env)
        false))))


(def time-unit-factors
  {:minutes [(/ 60) "minutes"]
   :nanos   [1e9 "nano seconds"]
   :micros  [1e6 "micro seconds"]
   :millis  [1e3 "milli seconds"]
   :seconds [1 "seconds"]})


(defn time-factor
  ([]
    (time-factor (if (contains? *options* :chart-time-unit)
                   (get *options* :chart-time-unit)
                   :nanos)))
  ([time-unit]
    (when-not (contains? time-unit-factors time-unit)
      (unexpected (str "either of " (keys time-unit-factors)) time-unit))
    (get time-unit-factors time-unit)))


;; ----- helpers -----


(defn save-bar-chart-data!
  "Save given map as bar-chart data."
  [bench-name & name-mean-pairs]
  (let [[factor _] (time-factor)]
    (->> (partition 2 name-mean-pairs)
     (mapcat (fn [[name mean]]
               [name (* ^double mean ^long factor)]))
     (apply array-map :bench-name bench-name)
     (swap! *bar-chart-data* conj))))


(defmacro measure
  [expr]
  `(do
     (echo ":::::" (if (option-quick-bench?) "Quick-benchmarking" "Benchmarking") ~(pr-str expr))
     (let [result# (if (option-quick-bench?)
                     (c/quick-benchmark ~expr {})
                     (c/benchmark ~expr {}))]
       [result# (with-out-str (c/report-result result#))])))


(defn nix?
  []
  (let [os (s/lower-case (str (System/getProperty "os.name")))]
    (some #(>= (.indexOf os ^String %) 0) ["mac" "linux" "unix"])))


(defn colorize
  [text & args]
  (if (and (option-colorize?) (nix?))
    (apply a/style text args)
    text))


(defn comparison-summary
  [& bench-results]
  (cond
    (empty? bench-results)   (colorize "No benchmark results found." :grey :bg-black)
    (= 1
      (count bench-results)) (colorize "Only one benchmark result found." :grey :bg-black)
    :otherwise               ;; find max (mx) and min (mn)
                             (let [{:keys [^double mx ^long ix
                                           ^double mn ^long in]} (->> bench-results
                                                                   (map-indexed (fn [i m] (assoc m :index i)))
                                                                   (reduce (fn [m br]
                                                                             (let [{:keys [^double mx ^long ix
                                                                                           ^double mn ^long in]
                                                                                    :or {mx Double/NEGATIVE_INFINITY
                                                                                         ix -1
                                                                                         mn Double/POSITIVE_INFINITY
                                                                                         in -1}} m
                                                                                   ^double mean  (first (:mean br))
                                                                                   index (:index br)
                                                                                   [mx ix] (if (< mx mean)
                                                                                             [mean index]
                                                                                             [mx ix])
                                                                                   [mn in] (if (> mn mean)
                                                                                             [mean index]
                                                                                             [mn in])]
                                                                               (assoc m :mx mx :ix ix :mn mn :in in)))
                                                                     {}))
                                   pc-faster (fn [^double n] (let [diff (Math/abs ^double (- mx n))]
                                                               (format "%.2f%% faster than %s"
                                                                 (double (/ (* 100 diff) n)) (get *labels* ix))))
                                   pc-slower (fn [^double n] (let [diff (Math/abs ^double (- n mn))]
                                                               (format "%.2f%% slower than %s"
                                                                 (double (/ (* 100 diff) mn)) (get *labels* in))))]
                               (->> bench-results
                                 (map-indexed (fn [i r]
                                                (colorize (str (get *labels* i)
                                                            " ("
                                                            (->> [(when (not= i ix) (pc-faster (first (:mean r))))
                                                                  (when (not= i in) (pc-slower (first (:mean r))))]
                                                              (keep identity)
                                                              (s/join ", "))
                                                            ")")
                                                  :black (condp = i
                                                           in :bg-green
                                                           ix :bg-red
                                                           :bg-yellow))))
                                 (interpose ", ")
                                 (apply str)))))
