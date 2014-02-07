(ns datomizer.datomize.setup
  (:require [datomic.api :as d :refer [db]]
            [datomizer.utility.misc :refer :all]))

(defn load-datomizer-schema
  "Load the schema used by datomizer."
  [dbc]
  (load-datoms-from-edn-resource-file dbc "datomizer-schema.edn"))

(defn load-datomizer-functions
  "Load datomizer functions.  Requires datomizer jar in transactor lib
  directory."
  [dbc]
  (load-datoms-from-edn-resource-file dbc "datomizer-functions.edn"))

(comment
  ;; Use like this:
  @(d/transact dbc [[:dmzr.datomize {:db/id (d/tempid :db.part/user) :test/map {:a 1}}]])
  (undatomize (d/entity (db dbc) 17592186046111)))
