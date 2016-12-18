(defproject citius "0.2.4-SNAPSHOT"
  :description "Comparative benchmarking using Criterium"
  :url "https://github.com/kumarshantanu/citius"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[criterium    "0.4.4"]
                 [cljfreechart "0.1.1"]
                 [myguidingstar/clansi "1.3.0" :exclusions [[org.clojure/clojure]]]]
  :global-vars {*warn-on-reflection* true
                *assert* true}
  :jvm-opts ^:replace ["-server" "-Xms2048m" "-Xmx2048m"]
  :profiles {:c16 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :c17 {:dependencies [[org.clojure/clojure "1.7.0"]]
                   :global-vars {*unchecked-math* :warn-on-boxed}}
             :c18 {:dependencies [[org.clojure/clojure "1.8.0"]]
                   :global-vars {*unchecked-math* :warn-on-boxed}}
             :dev {:dependencies [[org.clojure/tools.nrepl "0.2.10"]]}
             :pro {:global-vars {*assert* false}}})
