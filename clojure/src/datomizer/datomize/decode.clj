(ns datomizer.datomize.decode
  "Decode datomized entities."
  (:require [datomizer.datomize.encode :refer [ref-type]]
            [datomizer.utility.debug :refer :all]
            [datomizer.utility.misc :refer [first-matching]]))

(defn element-key-attribute
  "Return an element's key attribute"
  [element]
  (-> element
      keys
      (first-matching #"^:element\.(map/key|vector/index)")))

(defn element-value-attribute
  "Return an element's value attribute."
  [element]
  (-> element
       keys
       (first-matching #"^:element\.value/.*")))

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
    (if (= :element.value/nil value-attribute)
      nil
      value)))

(defn empty-datomized-container?
  "Does this value represent and empty container?"
  [value]
  (and (coll? value) (some :ref/empty value)))

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
    (:element.value/map) (decode-map value)
    (:element.value/vector) (decode-vector value)
    (:element.value/nil) nil
    value))

(defn undatomize-attribute
  "Decode an entity's attribute value."
  [entity key]
  (let [elements (get entity (str key))]
    (case (ref-type (.db entity) key)
      (:ref.type/map) (decode-map elements)
      (:ref.type/vector) (decode-vector elements)
      (:ref.type/variant) (decode-variant elements)
      (when-not (= :element.value/nil key) elements))))

(defn undatomize
  "Decode a datomized entity."
  [entity]
  (->> (conj (keys entity) :db/id)
       (mapcat (fn [key] [key (undatomize-attribute entity key)]))
       (apply hash-map )))
