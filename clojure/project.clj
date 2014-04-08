(defproject datomizer "0.1.0-SNAPSHOT"
  :description "Simple Datomic adapter and marshalling for JRuby"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.datomic/datomic-pro "0.9.4707"]
                 [org.clojure/test.check "0.5.7"]]
  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[org.clojure/tools.namespace "0.2.4"]
                                  [org.clojure/java.classpath "0.2.1"]]}}
  :jvm-opts ["-Xmx1g"])
