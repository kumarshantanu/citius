(ns citius.internal
  (:require
    [clojure.set    :as t]
    [clojure.string :as s]
    [clansi.core    :as a]
    [criterium.core :as c])
  (:import
    [java.util.concurrent Callable Executors ExecutorService Future]))


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
    (if-let [^String criterium-output-prop (System/getProperty "citius_criterium_output")]
      (Boolean/parseBoolean criterium-output-prop)
      (if-let [^String criterium-output-env (System/getenv "CITIUS_CRITERIUM_OUTPUT")]
        (Boolean/parseBoolean criterium-output-env)
        :tabular))))


(defn option-quick-bench?
  []
  (if (contains? *options* :quick?)
    (get *options* :quick?)
    (if-let [^String quick-bench-prop (System/getProperty "citius_quick_bench")]
      (Boolean/parseBoolean quick-bench-prop)
      (if-let [^String quick-bench-env (System/getenv "CITIUS_QUICK_BENCH")]
        (Boolean/parseBoolean quick-bench-env)
        false))))


(defn option-concurrency
  []
  (if (contains? *options* :concurrency)
    (get *options* :concurrency)
    (if-let [^String concurrency-prop (System/getProperty "citius_concurrency")]
      (->> (s/split concurrency-prop #",")
        (mapv s/trim)
        (mapv #(Integer/parseInt ^String %)))
      (if-let [^String concurrency-env (System/getenv "CITIUS_CONCURRENCY")]
        (->> (s/split concurrency-env #",")
          (mapv s/trim)
          (mapv #(Integer/parseInt ^String %)))
        (repeat 1)))))


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


(defn concurrently
  "Given a function f of no args, presumably with side effects, execute it concurrently in n threads returning a vector
  of results. When specified, function g handles the java.util.concurrent.Future objects."
  ([n f]
    (concurrently n f #(mapv deref %)))
  ([n f g]
    (let [^ExecutorService thread-pool (Executors/newFixedThreadPool n)
          future-vals (transient [])]
      (dotimes [i n]
        (let [^Callable task (if (coll? f) (nth f i) f)
              ^Future each-future (.submit thread-pool task)]
          (conj! future-vals each-future)))
      (try
        (g (persistent! future-vals))
        (finally
          (.shutdown thread-pool))))))


(defn cr-merge
  "Merge Criterium benchmark results."
  [coll] ; (doseq [each coll] (println each)) ; uncomment for debugging
  (when-not (->> (map class coll)
              (apply =))
    (unexpected "all values of the same type" coll))
  (let [v (first coll)
        n (count coll)
        as-coll (fn [x] (cond
                          (vector? coll) (vec x)
                          (set? coll)    (set x)
                          :otherwise     (list* x)))
        average (fn [x] (condp = (class v)
                          Short   (short  (/ ^short  x n))
                          Integer (int    (/ ^int    x n))
                          Long    (long   (/ ^long   x n))
                          Float   (float  (/ ^float  x n))
                          Double  (double (/ ^double x n))
                          (unexpected "Integer, Long, Float or Double" x)))]
    (cond
      (apply = coll) v
      (number? v) (-> (apply + coll)
                    average)
      (string? v) (if (apply = coll)
                    v
                    (s/join ", " coll))
      (map? v)    (let [ks (->> (map keys coll)
                             (map set)
                             (apply t/union))]
                    (->> (map (fn [k]
                                {k (->> (map #(get % k) coll)
                                     vec
                                     cr-merge)}) ks)
                      (apply merge)))
      (coll? v)   (->> (apply map vector coll)
                    (map cr-merge)
                    as-coll)
      :otherwise  (unexpected "number, string, map or collection" v))))


(defmacro measure
  "Benchmark expression latency"
  [bench-name index expr]
  `(let [concurrency# (nth (option-concurrency) ~index 1)
         next-number# (let [counter# (atom 0)]
                        (fn [] (swap! counter# inc)))
         index-label# (nth *labels* ~index)
         benchmark-f# (fn []
                        (let [thread-name# (.getName (Thread/currentThread))]
                          (try
                            (.setName (Thread/currentThread)
                              (str "citius-" index-label# \- ~bench-name \- (next-number#)))
                            (if (option-quick-bench?)
                              (c/quick-benchmark ~expr {})
                              (c/benchmark ~expr {}))
                            (finally
                              (.setName (Thread/currentThread) thread-name#)))))
         dummy#  (echo ":::::" (if (option-quick-bench?) "Quick-benchmarking" "Benchmarking")
                   (str \' (nth *labels* ~index) "'::'" ~bench-name "':") ~(pr-str expr)
                   "::::: Concurrency:" concurrency#)
         result# (if (= 1 concurrency#)
                   (benchmark-f#)
                   (cr-merge (concurrently concurrency# benchmark-f#)))]
     [result# (str "Extrapolated throughput: "
                (long (/ 1 (double (first (:mean result#)))))
                "/sec/thread\n"
                (with-out-str (c/report-result result#)))]))


(defn nix?
  "Return true if a Unix-like system detected, false other."
  []
  (let [os (s/lower-case (str (System/getProperty "os.name")))]
    (some #(>= (.indexOf os ^String %) 0) ["mac" "linux" "unix"])))


(defn colorize
  "Colorize text"
  [text & args]
  (if (and (option-colorize?) (nix?))
    (apply a/style text args)
    text))


(defn comparison-summary
  "Return comparative benchmark summary text"
  [& bench-results]
  (cond
    (empty? bench-results)   (colorize "No benchmark results found." :black :bg-yellow)
    (= 1
      (count bench-results)) (colorize "Only one benchmark result found - comparison not possible." :black :bg-yellow)
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


;; ----- call stats helpers -----


(defn call-count
  "Return a thread-safe function (using atom) that manipulates call counts as follows:
  Argument Description
  none     returns current count
  :reset   resets the current counts to 0
  :count   updates the counter"
  []
  (let [stats (atom 0)]
    (fn
      ([] (deref stats))
      ([k] (if (identical? :reset k)
             (reset! stats 0)
             (swap! stats unchecked-inc))))))


(defn wrap-call-stats
  "Given a function f wrap it using function stats (with side effects) to record invocation stats."
  [stats f]
  (fn [& args]
    (try
      (let [result (apply f args)]
        (stats :count)
        result))))


(defn wait-until-millis
  "Wait until specified time in milliseconds, showing progress."
  ([^long timeout-millis]
    (wait-until-millis timeout-millis 100))
  ([^long timeout-millis ^long progress-millis]
    (while (< (System/currentTimeMillis) timeout-millis)
      (let [millis (min progress-millis (- timeout-millis (System/currentTimeMillis)))]
        (when (pos? millis)
          (try
            (Thread/sleep millis)
            (catch InterruptedException e
              (.interrupt ^Thread (Thread/currentThread))))
          (print \.)
          (flush))))))


(defn benchmark-throughput*
  "Run throughput benchmark and return result map."
  [^long concurrency ^long warmup-millis ^long bench-millis f]
  (let [exit?      (atom false)
        stats-coll (repeatedly concurrency call-count)
        g-coll     (->> (repeat f)
                     (map wrap-call-stats stats-coll)
                     (map-indexed (fn [i g]
                                    (fn []
                                      (let [r (nth stats-coll i)]
                                        (while (not (deref exit?))
                                          (g))
                                        (r)))))
                     vec)
        call-count (->> (fn [future-vals]
                          (print "\nWarming up")
                          (wait-until-millis (+ (System/currentTimeMillis) warmup-millis))
                          (mapv #(% :reset) stats-coll) ; reset counters
                          (print "\nBenchmarking")
                          (wait-until-millis (+ (System/currentTimeMillis) bench-millis))
                          (println)
                          (swap! exit? not)
                          (mapv deref future-vals))
                     (concurrently concurrency g-coll)
                     (apply +))]
    {:concurrency concurrency
     :calls-count call-count
     :duration-millis bench-millis
     :calls-per-second (->> (/ bench-millis 1000)
                         double
                         (/ ^long call-count)
                         long)}))


(defmacro benchmark-throughput
  "Benchmark a body of code for throughput."
  [concurrency warmup-millis bench-millis & body]
  `(benchmark-throughput* ~concurrency ~warmup-millis ~bench-millis (fn [] ~@body)))
