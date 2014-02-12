(ns datomizer.datomize.decode
  "Decode datomized entities."
  (:require [datomizer.utility.debug :refer :all]
            [datomizer.utility.misc :refer [ref-type first-matching]]))

(defn element-key-attribute
  "Return an element's key attribute"
  [element]
  (-> element
      keys
      (first-matching #"^:dmzr.element\.(map/key|vector/index)")))

(defn element-value-attribute
  "Return an element's value attribute."
  [element]
  (-> element
       keys
       (first-matching #"^:dmzr.element\.value/.*")))

(defn element-key
  "Return an element's key or index."
  [element]
  ((element-key-attribute element) element))

(defn element-value
  "Return an element's value"
  [element]
  ((element-value-attribute element) element))

(declare decode)

(defn element->pair
  "Decode a datomized collection element. Returns a key value pair."
  [element]
  [(element-key element) (decode element (element-value-attribute element))])

(defn empty-datomized-container?
  "Does this value represent and empty container?"
  [value]
  (some :dmzr/empty value))

(defmulti decode
  "Decode an entity's attribute value."
  (fn [entity key] (ref-type (.db entity) key)))

(defmethod decode :dmzr.type/map
  [entity key]
  (if (empty-datomized-container? (key entity))
    {}
    (->> (key entity)
         (mapcat element->pair)
         (apply hash-map))))

(defmethod decode :dmzr.type/vector
  [entity key]
    (if (empty-datomized-container? (key entity))
    []
    (->> (key entity)
         (map element->pair)
         (sort-by first)
         (mapv last))))

(defmethod decode :dmzr.type/variant
  [entity key]
  (if (= :dmzr.element.value/nil (element-value-attribute (key entity)))
    nil
    (element-value (key entity))))

(defmethod decode :dmzr.type/edn
  [entity key]
  (clojure.edn/read-string (key entity)))

(defmethod decode :default
  [entity key]
  (if (= :dmzr.element.value/nil key)
    nil
    (key entity)))

(defn undatomize
  "Decode a datomized entity."
  [entity]
  (->> (conj (keys entity) :db/id)
       (mapcat (fn [key] [key (decode entity key)]))
       (apply hash-map )))
