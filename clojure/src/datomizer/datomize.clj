(ns datomizer.datomize
  (:require [datomic.api :as d :refer [db q]]
            [datomizer.datomize.encode :refer :all]
            [datomizer.utility :refer :all]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Schema

(defn load-datomizer-schema
  "Load the schema used by datomizer."
  [dbc]
  (load-datoms-from-edn-resource-file dbc "datomizer-schema.edn"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Retrieval

(declare decode)

(defn element-key
  [element]
  (or (get element :element.map/key) (get element :element.vector/index)))

(defn element-value-attribute
  [element]
  (first (filter #(re-matches #"^:element.value/.*" (str %)) (keys element))))

(defn decode-element
  "Decode a datomized element to a collection [key value] pair."
  [entity element]
  (let [key (element-key element)
        value-attribute (element-value-attribute element)
        value (value-attribute element)]
    [key (decode entity value-attribute value)]))

(defn decode-value
  "Decode a datomized variant value."
  [entity element]
  (let [value-attribute (first (filter #(re-matches #"^:element.value/.*" (str %)) (keys element)))
        value (value-attribute element)]
    (if (= :element.value/nil value-attribute) nil value)))


(defn empty-datomized-container?
  [value]
  (and (coll? value) (some :ref/empty value)))

(defn decode-map
  "Decode a datomized map."
  [entity elements]
  (if (empty-datomized-container? elements)
    {}
    (->> elements
         (mapcat #(decode-element entity %))
         (apply hash-map))))

(defn decode-vector
  "Decode a datomized vector."
  [entity elements]
  (if (empty-datomized-container? elements)
    []
    (->> elements
         (map #(decode-element entity %))
         (sort-by first)
         (map last))))

(defn decode
  "Decode values on a datomized entity."
  [entity key elements]
  (case (ref-type (.db entity) key)
    (:ref/map :ref.type/map) (decode-map entity elements)
    (:ref/vector :ref.type/vector) (decode-vector entity elements)
    (:ref.type/value) (decode-value entity elements)
    (when-not (= :element.value/nil key) elements)))

(defn undatomize
  [entity]
  (apply hash-map (mapcat (fn [k] [k (decode entity k (get entity (str k)))]) (conj (keys entity) :db/id))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Database functions

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


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Integrity checks

(defn retrieve-all-elements [db]
  (let [rules '[[[element? ?e] [_ :test/map ?e]]]]
    (map (fn [x] (->> x first (d/entity db) d/touch)) (q '[:find ?e :in $ % :where (element? ?e)] db rules))))


(defn valid-map? [db element]
  (let [attributes (apply hash-set (keys element))]
    (or (:ref/empty element)
        (and (contains? attributes :element.map/key)
             (not (contains? attributes :element.vector/index))
             (some #(re-matches #"^:element\.value/.*" (str %)) attributes)))))

(defn valid-vector? [db element]
  (let [attributes (apply hash-set (keys element))]
    (or (:ref/empty element)
        (and (not (contains? (keys element) :element.map/key))
             (contains? attributes :element.vector/index)
             (some #(re-matches #"^:element\.value/.*" (str %)) attributes)))
    :ref.type/value (and (not (contains? attributes :element.map/key))
                         (not (contains? attributes :element.vector/index))
                         (some #(re-matches #"^:element\.value/.*" (str %)) attributes))))


(defn valid-value? [db element]
  (let [attributes (apply hash-set (keys element))]
    (and (not (contains? attributes :element.map/key))
         (not (contains? attributes :element.vector/index))
         (some #(re-matches #"^:element\.value/.*" (str %)) attributes))))

(defn valid? [db element]
  (let [references (d/datoms db :vaet (:db/id element))
        ownerships (filter (fn [datom] (:is-component  (d/attribute db (.a datom)))) references)
        ownership (first ownerships)
        ownership-type (:ref/type (d/entity db (.a ownership)))
        attributes (apply hash-set (keys element))]
    (and  (= 1 (count ownerships))
          (case ownership-type
            :ref.type/map (valid-map? db element)
            :ref.type/vector (valid-vector? db element)
            :ref.type/value (valid-value? db element)))))

(defn invalid-elements [db]
  (remove (partial valid? db) (retrieve-all-elements db)))
