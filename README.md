# citius

A Clojure library for comparative benchmarking using
[Criterium](https://github.com/hugoduncan/criterium).

## Usage

Leiningen dependency: `[citius "0.1.0-SNAPSHOT"]`

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

## License

Copyright © 2015 Shantanu Kumar (kumar.shantanu@gmail.com, shantanu.kumar@concur.com)

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
