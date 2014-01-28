(ns datomizer.system
  "Running datomizer system (mostly for development)"
  (:require [datomizer.datomize :as dz]
            [datomizer.datomize-test :as dzt])
  (:use [datomic.api :as d :only (db q)]))

(defn system
  "Returns a new instance of the application"
  [& {:keys [db-uri]}]
   {:db-uri db-uri
   :dbc (atom nil)})

(defn start
  "Ensure database exists and connect to it."
  [system]
  (d/create-database (:db-uri system))
  (reset! (:dbc system) (d/connect (:db-uri system)))
  (dz/load-datomizer-schema @(:dbc system))
  (dzt/load-datomizer-test-schema @(:dbc system))
  system)

(defn stop
  "Disconnect from database."
  [system]
  (if @(:dbc system) (d/release @(:dbc system)))
  (reset! (:dbc system) nil)
  system)

(defn rebuild-database!
  "Delete and re-create database"
  [system]
  (stop system)
  (d/delete-database (:db-uri system))
  (start system)
  system)

#_(defn -main
  [& m]
  (let [s (system :db-uri "datomic:mem:://datomizer-development")]
    (start s)
    (.addShutdownHook (Runtime/getRuntime) (Thread. #((stop s)
                                                      (d/shutdown false))))))
