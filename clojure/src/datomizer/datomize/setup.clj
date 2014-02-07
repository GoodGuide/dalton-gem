(ns datomizer.datomize.setup
  (:require [datomic.api :as d :refer [db]]
            [datomizer.utility.misc :refer :all]))


(defn load-datomizer-schema
  "Load the schema used by datomizer."
  [dbc]
  (load-datoms-from-edn-resource-file dbc "datomizer-schema.edn"))


(def datomize-db-fn
  (d/function {:lang "clojure"
               :params '[db entity]
               :requires '[[datomizer.datomize]]
               :code "(datomizer.datomize/datomize db entity)"}))

(defn install-database-functions [dbc]
  (d/transact dbc [{:db/id (d/tempid :db.part/user)
                   :db/ident :dmzr.datomize
                    :db/fn datomize-db-fn}]))

(defn datomize-with-db-fn [dbc]
  (let [f (:db/fn (d/entity (db dbc) :dmzr.datomize))]
    f
    (.invoke f (db dbc) {:db/id (d/tempid :db.part/user) :test/map {:a 1}})))

(comment
  (d/transact dbc [[:dmzr.datomize {:db/id (d/tempid :db.part/user) :test/map {:a 1}}]])
  (undatomize (d/entity (db dbc) 17592186046111)))
