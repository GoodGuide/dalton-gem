(ns datomizer.system
  "Running datomizer system (mostly for development)"
  (:require [datomic.api :as d]
            [datomizer.datomize-test :as dzt]
            [datomizer.datomize.setup :as dzs]))

(defn system
  "Returns a new instance of the application"
  [& {:keys [db-uri]}]
   {:db-uri db-uri
   :conn (atom nil)})

(defn start
  "Ensure database exists and connect to it."
  [system]
  (d/create-database (:db-uri system))
  (reset! (:conn system) (d/connect (:db-uri system)))
  (dzs/load-datomizer-schema @(:conn system))
  (dzs/load-datomizer-functions @(:conn system))
  (dzt/load-datomizer-test-schema @(:conn system))
  system)

(defn stop
  "Disconnect from database."
  [system]
  (reset! (:conn system) nil)
  system)

(defn rebuild-database!
  "Delete and re-create database"
  [system]
  (stop system)
  (d/delete-database (:db-uri system))
  (start system)
  system)
