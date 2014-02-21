(ns user
  "Tools for interactive development with the REPL. This file should
  not be included in a production build of the application."
  (:require
   [clojure.java.io :as io]
   [clojure.java.javadoc :refer (javadoc)]
   [clojure.pprint :refer (pprint)]
   [clojure.reflect :refer (reflect)]
   [clojure.repl :refer (apropos dir doc find-doc pst source)]
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.test :as test]
   [clojure.tools.namespace.repl :refer (refresh refresh-all)]
   [datomic.api :as d :refer [db q]]
   [datomizer.system :as system]
   [datomizer.utility.debug :refer :all]
   [datomizer.datomize-test :refer :all]
   [datomizer.datomize.decode :refer :all]
   [datomizer.datomize.encode :refer :all]
   [datomizer.datomize-test :refer :all]
   ))

(def system nil)
(def conn nil)

(defn init
  "Constructs the current development system."
  []
  (alter-var-root #'system
    (constantly (system/system :db-uri "datomic:dev://localhost:4334/datomizer-development"))))

(defn start
  "Starts the current development system."
  []
  (alter-var-root #'system system/start)
  (alter-var-root #'conn (fn [_] @(:conn system))))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Miscellaneous tools

(defn show-methods [x] (sort (filter #(not (re-find #"^(__|const)" (str %))) (map :name (:members (clojure.reflect/reflect x))))))
