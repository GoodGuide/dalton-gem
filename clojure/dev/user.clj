(ns user
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.pprint :refer (pprint pp)]
            [clojure.repl :refer :all]
            clojure.test
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [datomizer.system :as system])
  (:use [datomic.api :as d :only (db q)]
        [clojure.reflect :only [reflect]]))



(def system nil)

(defn init
  "Constructs the current development system."
  []
  (alter-var-root #'system
    (constantly (system/system :db-uri "datomic:dev://localhost:4334/datomizer-development"))))

(defn start
  "Starts the current development system."
  []
  (alter-var-root #'system system/start))

(defn rebuild-database
  "Replace the database for the current development system."
  []
  (alter-var-root #'system system/rebuild-database!))

(defn stop
  "Shuts down and destroys the current development system."
  []
  (alter-var-root #'system
    (fn [s] (when s (system/stop s)))))

(defn go
  "Initializes the current development system and starts it running."
  []
  (init)
  (start))

(defn reset []
  (stop)
  (refresh :after 'user/go))

(defn run-datomizer-tests
  []
  (clojure.test/run-all-tests #"^datomizer.*"))

(defn t []
  (refresh :after 'user/run-datomizer-tests))

(defn dbc [] @(:dbc system))

(defn show-methods [x] (filter #(not (re-find #"^(__|const)" (str %))) (map :name (:members (clojure.reflect/reflect x)))))


(require '[simple-check.core :as sc]
         '[simple-check.generators :as gen]
         '[simple-check.properties :as prop])

(use 'datomizer.datomize-test)
