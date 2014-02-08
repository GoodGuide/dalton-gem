(ns datomizer.datomize.validation
  "Short package description."
  (:require [datomic.api :as d :refer [q]]))


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
    :ref.type/variant (and (not (contains? attributes :element.map/key))
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
            :ref.type/variant (valid-value? db element)))))

(defn invalid-elements [db]
  (remove (partial valid? db) (retrieve-all-elements db)))
