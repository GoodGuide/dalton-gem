(ns datomizer.datomize
  (:use datomizer.debug
        [datomic.api :as d :only (db q)]))

(defmulti datomize
  "Convert collections to datoms."
  (fn [partition value] (class value)))

(defn element-value-attribute
  "Datomic attribute to use for element value, based on its type."
  [value]
  (condp instance? value
    String :element.value/string
    java.util.List :element.value/vector
    java.util.Map :element.value/map
    java.lang.Long :element.value/long
    ;; TODO: add more
    (throw (java.lang.IllegalArgumentException. (str "Marshalling not supported for type " (class value))))))

(defn element-value
  "Datomic-compatible value for a element"
  [partition value]
  (if (coll? value)
    (datomize partition value)
    value))

(defmethod datomize java.util.Map
  [partition value]
  (if (empty? value)
    :ref.map/empty
    (map (fn [[k, v]]
           {:db/id (d/tempid partition)
            :element.map/key k
            (element-value-attribute v) (element-value partition v)})
         value)))

(defmethod datomize java.util.List
  [partition value]
  (if (empty? value)
    :ref.vector/empty
    (map (fn [[i, v]]
              {:db/id (d/tempid partition)
               :element.vector/index i
               (element-value-attribute v) (element-value partition v)})
         (zipmap (range) value))))

(defn ref-type
  "Determine the reference type of an attribute."
  [entity key]
  (let [attribute (d/entity (.db entity) (keyword key))]
    (:ref/type attribute)))

(declare decode-elements)

(defn decode
  "Convert a datomized element to a collection [key value] pair"
  [entity element]
  (if (instance? clojure.lang.ILookup element)
    (let [key (or (get element :element.map/key) (get element :element.vector/index))]
      (if key
        (let [value-attribute (first (filter #(re-matches #"^:element.value/.*" (str %)) (keys element)))
              value (value-attribute element)]
          [key (decode-elements entity value-attribute value)])
        element))
    element))

(defn decode-elements
  "Convert datomized collection elements back into a collection."
  [entity key elements]
  (if (set? elements)
    (case elements
      #{:ref.vector/empty} []
      #{:ref.map/empty} {}
      (case (ref-type entity key)
        (:ref/map :ref.type/map) (apply hash-map (flatten (map #(decode entity %) elements)))
        (:ref/vector :ref.type/vector) (map last (sort-by first (map #(decode entity %) elements)))
        elements))
    elements))

(defn undatomize
  [entity]
  (apply hash-map (mapcat (fn [k] [k (decode-elements entity k (get entity (str k)))]) (conj (keys entity) :db/id))))
