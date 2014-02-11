(ns datomizer.edenize.encode
  "Store data in Datomic, serializing complex data with EDN."
  (:require [datomizer.datoms :refer [rehearse-transaction
                                      remove-conflicts
                                      resolve-idents]]
            [datomizer.utility.debug :refer [dbg]]
            [datomizer.utility.misc :refer [ref-type]]))

(defn edenize-attribute [db entity key]
  (let [value (get entity key)]
    (if (= :dmzr.ref.type/edn (ref-type db key))
      [:db/add (:db/id entity) key (pr-str value)]
      [:db/add (:db/id entity) key value])))

(defn edenize
  "Encode enity data, serializing data structures with EDN."
  [db entity & {:keys [partition] :or {partition :db.part/user}}]
  (let [id (:db/id entity)
        data (dissoc entity :db/id)
        retractions (rehearse-transaction db [[:db.fn/retractEntity id]])
        additions (map (partial resolve-idents db) (map (partial edenize-attribute db entity) (keys data) ))]
    (remove-conflicts db additions retractions)))
