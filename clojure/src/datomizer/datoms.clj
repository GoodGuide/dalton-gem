(ns datomizer.datoms
  "Datom wrangling."
  (:require [datomic.api :as d]
            [datomizer.utility.debug :refer [dbg]]))

(defn Datom->vector
  "Convert a Datom to a vector of [operation entity-id attribute value]"
  [datom]
  [(if (.added datom) :db/add :db/retract)
   (.e datom)
   (.a datom)
   (.v datom)])

(defn resolve-idents
  "Resolves any idents in a datom addition/retraction."
  [db [op e a v]]
  (let [ref? (= datomic.Attribute/TYPE_REF (.valueType (d/attribute db a)))]
    [op (d/entid db e) (d/entid db a) (if ref? (d/entid db v) v)]))

(defn transaction-datom?
  "Does this Datom refer to a transaction entity?"
  [db datum]
  (= :db.part/tx (d/ident db (d/part (.e datum)))))

(defn remove-transaction-datoms
  "Returns a list of datoms with transaction entity (creation) datoms
  removed."
  [db datoms]
  (remove (partial transaction-datom? db) datoms))

(defn flip-tx-data
  "Convert tx-data from a transaction result into a list of datom
   vectors usable with transact."
  [db tx-data]
  (->> tx-data
       (remove-transaction-datoms db)
       (map Datom->vector)))

(defn rehearse-transaction
  "Rehearses a transaction, returning a vector of datom
   addition/retraction vectors, suitable for re-submitting.  This
   flattens nested tx-data, resolves entity idents to ids, and runs
   transaction funcitons, capturing their result datoms.  Does not
   affect the real database!"
  [db datoms]
  (let [result (d/with db datoms)]
    (flip-tx-data db (:tx-data result))))

(defn remove-conflicts
  "Remove conflicting additions & retractions."
  [db additions retractions]
  (let [conflicts (clojure.set/intersection (set (map rest retractions)) (set (map rest additions)) )]
    (remove (fn [datom] (contains? conflicts (rest datom)))
            (concat retractions additions))))
