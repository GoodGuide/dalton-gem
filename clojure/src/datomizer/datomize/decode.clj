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

(defn decode-element
  "Decode a datomized collection element. Returns a key value pair."
  [element]
  (let [key (element-key element)
        value-attribute (element-value-attribute element)
        value (value-attribute element)]
    [key (decode value-attribute value)]))

(defn decode-variant
  "Decode a datomized variant value."
  [element]
  (let [value-attribute (element-value-attribute element)
        value (value-attribute element)]
    (if (= :dmzr.element.value/nil value-attribute)
      nil
      value)))

(defn empty-datomized-container?
  "Does this value represent and empty container?"
  [value]
  (and (coll? value) (some :dmzr.ref/empty value)))

(defn decode-map
  "Decode a datomized map."
  [elements]
  (if (empty-datomized-container? elements)
    {}
    (->> elements
         (mapcat decode-element)
         (apply hash-map))))

(defn decode-vector
  "Decode a datomized vector."
  [elements]
  (if (empty-datomized-container? elements)
    []
    (->> elements
         (map decode-element)
         (sort-by first)
         (map last))))

(defn decode
  "Decode a value."
  [key value]
  (case key
    (:dmzr.element.value/map) (decode-map value)
    (:dmzr.element.value/vector) (decode-vector value)
    (:dmzr.element.value/nil) nil
    value))

(defn undatomize-attribute
  "Decode an entity's attribute value."
  [entity key]
  (let [elements (get entity (str key))]
    (case (ref-type (.db entity) key)
      (:dmzr.ref.type/map) (decode-map elements)
      (:dmzr.ref.type/vector) (decode-vector elements)
      (:dmzr.ref.type/variant) (decode-variant elements)
      (when-not (= :dmzr.element.value/nil key) elements))))

(defn undatomize
  "Decode a datomized entity."
  [entity]
  (->> (conj (keys entity) :db/id)
       (mapcat (fn [key] [key (undatomize-attribute entity key)]))
       (apply hash-map )))
