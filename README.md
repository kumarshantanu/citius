# citius

A Clojure library for comparative benchmarking using
[Criterium](https://github.com/hugoduncan/criterium).


## Usage

Leiningen dependency: `[citius "0.2.2"]`

Requiring namespace:
```clojure
(require '[citius.core :as c])
```

### Using in tests (clojure.test)

Define `use-fixtures` setup:

```clojure
(clojure.test/use-fixtures
  :once (c/make-bench-wrapper ["Apply" "Reduce"]
          {:chart-title "Apply vs Reduce"
           :chart-filename (format "bench-small-clj-%s.png" c/clojure-version-str)}))
```

Under each test, conduct the comparative benchmarking:

```clojure
(deftest test-simple
  (c/compare-perf
    "concat strs" (apply str ["foo" "bar" "baz"]) (reduce str ["foo" "bar" "baz"]))
  (c/compare-perf
    "sum numbers" (apply + [1 2 3 4 5 6 7 8 9 0]) (reduce + [1 2 3 4 5 6 7 8 9 0])))
```

### Using outside of clojure.test

Outside of clojure.test, use the `with-bench-context` macro:

```clojure
(c/with-bench-context ["Apply" "Reduce"]
  {:chart-title "Apply vs Reduce"
   :chart-filename (format "bench-simple-clj-%s.png" c/clojure-version-str)}
  (c/compare-perf
    "concat strs" (apply str ["foo" "bar" "baz"]) (reduce str ["foo" "bar" "baz"]))
  (c/compare-perf
    "sum numbers" (apply + [1 2 3 4 5 6 7 8 9 0]) (reduce + [1 2 3 4 5 6 7 8 9 0])))
```

### Controlling runtime behavior

You may tweak the runtime behavior of benchmarking with the following system properties and environment variables:

| Description              | Choices              | Default    | Java system property      | Environment variable      |
|--------------------------|----------------------|------------|---------------------------|---------------------------|
| Benchmark concurrency    | comma delimited ints | 1 for each | `citius_concurrency`      | `CITIUS_CONCURRENCY`      |
| Colorize summary output? | `true` or `false`    |    `true`  | `citius_colorize`         | `CITIUS_COLORIZE`         |
| Criterium output format  | `true` or `false`    | `:tabular` | `citius_criterium_output` | `CITIUS_CRITERIUM_OUTPUT` |
| Perform quick bench?     | `true` or `false`    |    `true`  | `citius_quick_bench`      | `CITIUS_QUICK_BENCH`      |

For example, if the tabular Criterium output exceeds the width of your screen you may want to view it vertically:

On Unix-like systems:
```bash
CITIUS_CRITERIUM_OUTPUT=true lein with-profile clj17,bench test
# or
export CITIUS_CRITERIUM_OUTPUT=true
lein with-profile clj17,bench test
```

On Windows:
```batch
set CITIUS_CRITERIUM_OUTPUT=true
lein with-profile clj17,bench test
```


## License

Copyright © 2015 Shantanu Kumar (kumar.shantanu@gmail.com, shantanu.kumar@concur.com)

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
