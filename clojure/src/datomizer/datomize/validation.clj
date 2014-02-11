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
    (or (:dmzr/empty element)
        (and (contains? attributes :dmzr.element.map/key)
             (not (contains? attributes :dmzr.element.vector/index))
             (some #(re-matches #"^:dmzr.element\.value/.*" (str %)) attributes)))))

(defn valid-vector? [db element]
  (let [attributes (apply hash-set (keys element))]
    (or (:dmzr/empty element)
        (and (not (contains? (keys element) :dmzr.element.map/key))
             (contains? attributes :dmzr.element.vector/index)
             (some #(re-matches #"^:dmzr.element\.value/.*" (str %)) attributes)))
    :dmzr.type/variant (and (not (contains? attributes :dmzr.element.map/key))
                         (not (contains? attributes :dmzr.element.vector/index))
                         (some #(re-matches #"^:dmzr.element\.value/.*" (str %)) attributes))))


(defn valid-value? [db element]
  (let [attributes (apply hash-set (keys element))]
    (and (not (contains? attributes :dmzr.element.map/key))
         (not (contains? attributes :dmzr.element.vector/index))
         (some #(re-matches #"^:dmzr.element\.value/.*" (str %)) attributes))))

(defn valid? [db element]
  (let [references (d/datoms db :vaet (:db/id element))
        ownerships (filter (fn [datom] (:is-component  (d/attribute db (.a datom)))) references)
        ownership (first ownerships)
        ownership-type (:dmzr.ref/type (d/entity db (.a ownership)))
        attributes (apply hash-set (keys element))]
    (and  (= 1 (count ownerships))
          (case ownership-type
            :dmzr.type/map (valid-map? db element)
            :dmzr.type/vector (valid-vector? db element)
            :dmzr.type/variant (valid-value? db element)))))

(defn invalid-elements [db]
  (remove (partial valid? db) (retrieve-all-elements db)))
