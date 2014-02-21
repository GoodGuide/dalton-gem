(ns datomizer.datomize.setup
  (:require [datomic.api :as d :refer [db]]
            [datomizer.utility.misc :refer :all]))

(defn load-datomizer-schema
  "Load the schema used by datomizer."
  [conn]
  (load-datoms-from-edn-resource-file conn "datomizer-schema.edn"))

(defn load-datomizer-functions
  "Load datomizer functions.  Requires datomizer jar in transactor lib
  directory."
  [conn]
  (load-datoms-from-edn-resource-file conn "datomizer-functions.edn"))

(comment
  ;; Use like this:
  @(d/transact conn [[:dmzr/datomize {:db/id (d/tempid :db.part/user) :test/map {:a 1}}]])
  (undatomize (d/entity (db conn) 17592186046111)))
