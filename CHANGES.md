## Changes and TODO


## 2016-May-?? / 0.2.3

* Updated dependency version: Criterium `0.4.4`
* Given option concurrency as a comma-separated integer string, repeat last integer indefinitely
  * Impacts system property `citius_concurrency` and environment variable `CITIUS_CONCURRENCY`


## 2015-August-06 / 0.2.2

* Handle merging of arrays in concurrent benchmark results


## 2015-June-29 / 0.2.1

* Show extrapolated throughput in comparative benchmark output
* Fix reflection warning
* Set thread name as citius-{label}-{testcase}-{N} when benchmarking
* Fix formatting for the benchmarking notice
* Fix message/color for single benchmark summary


## 2015-June-27 / 0.2.0

* Support benchmarking with tunable concurrency - can override at runtime


## 2015-June-27 / 0.1.2

* Fix typo in `make-bench-wrapper` call


## 2015-June-06 / 0.1.1

* Criterium output format - can override at runtime


## 2015-June-01 / 0.1.0

* Criterium benchmarking
* Output format
  * Tabular, comparative Criterium output
  * Quick-bench option (can override at runtime)
  * Colorize summary output (can override at runtime)
* Bar chart generation option
