(ns datomizer.datomize.decode
  "Decode datomized entities."
  (:require [datomizer.datomize.encode :refer [ref-type]]))

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
