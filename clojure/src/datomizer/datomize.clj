(ns datomizer.datomize
  (:require [datomizer.debug :refer :all]
            [datomizer.utility :refer :all]
            clojure.data
            [datomic.api :as d :refer (db q)]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Schema

(defn load-datomizer-schema
  "Load the schema used by datomizer."
  [dbc]
  (load-datoms-from-edn-resource-file dbc "datomizer-schema.edn"))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Introspection

;; TODO:
;; inline ref-type
;; switch tests to use database functions
;; convert EVA, datomize, and construct to database functions
;; develop the diff and update function.

(defn ref-type
  "Determine the reference type of an attribute."
  [db key]
  (let [attribute (d/entity db (keyword key))]
    (:ref/type attribute)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Storage



(defn element-value-attribute
  "Datomic attribute to use for element value, based on its type."
  [value]
  (condp instance? value
    java.lang.String :element.value/string
    java.lang.Long :element.value/long
    java.lang.Float :element.value/float
    java.lang.Double :element.value/double
    java.lang.Boolean :element.value/boolean
    java.util.Date :element.value/instant
    clojure.lang.Keyword :element.value/keyword
    java.util.List :element.value/vector
    java.util.Map :element.value/map
    java.math.BigDecimal :element.value/bigdec
    java.math.BigInteger :element.value/bigint
    (class (byte-array 1)) :element.value/bytes
    ;; :element.value/fn
    ;; :element.value/ref
    (throw (java.lang.IllegalArgumentException. (str "Marshalling not supported for type " (class value))))
    ))

(defn condense-elements
  "Merge a list of value - datom list pairs."
  [elements]
  (reduce (fn [[accumulated-values accumulated-datoms]
              [value datoms]]
            [(concat accumulated-values (flatten [value])) (concat accumulated-datoms datoms)])
          [[] []]
          elements))

(defrecord Context [db partition parent-entity-id attribute-on-parent])

(defmulti encode
  "Encode a value as datoms.
  Returns a pair of values: the to assign to the parent attribute
  and a vec of datoms to transact."
  (fn [context value-to-encode]
    (ref-type (:db context) (:attribute-on-parent context))))

(defn encode-pair [context key-attribute k v]
  (let [element-id (d/tempid (:partition context))
        element-value-attribute (element-value-attribute v)
        element-context (assoc context :parent-entity-id element-id :attribute-on-parent element-value-attribute )
        [encoded-values datoms] (encode element-context v)]
    [element-id (concat datoms
                        [[:db/add element-id key-attribute k]
                         [:db/add element-id element-value-attribute encoded-values]])]))

(defmethod encode :ref.type/map [context value-to-encode]
  (when-not (map? value-to-encode)
    (throw (java.lang.IllegalArgumentException. (str (:attribute-on-parent context) " expects a map. Got " value-to-encode)) ))
  (if (empty? value-to-encode)
    [:ref.map/empty []]
    (condense-elements (map (fn [[k, v]] (encode-pair context :element.map/key k v))
                            value-to-encode))))

(defmethod encode :ref.type/vector [context value-to-encode]
  (when-not (vector? value-to-encode)
    (throw (java.lang.IllegalArgumentException. (str (:attribute-on-parent context) " expects a vector. Got " value-to-encode)) ))
  (if (empty? value-to-encode)
    [:ref.vector/empty []]
    (condense-elements (map (fn [[i, v]] (encode-pair context :element.vector/index i v))
                            (zipmap (range) value-to-encode)))))

(defmethod encode :ref.type/value [context value-to-encode]
  (let [id (d/tempid (:partition context))]
    [id [[:db/add id (element-value-attribute value-to-encode) value-to-encode]]]))

(defmethod encode nil [_ value-to-encode]
  [value-to-encode []])


(declare undatomize)

(defn datomize
  [db entity & {:keys [partition] :or {partition :db.part/user}}]
  (let [entity-id (:db/id entity)
        data (dissoc entity :db/id)
        existing-entity (if (pos? (d/entid db entity-id)) (undatomize (d/entity db entity-id)) {})
        [new-pairs obsolete-pairs unchanged-pairs] (clojure.data/diff data existing-entity)]
    (second (condense-elements (map (fn [[attribute, value]]
                                      (let [[encoded-values datoms] (encode (->Context db partition entity-id attribute) value)]
                                        [entity-id (into datoms [[:db/add entity-id attribute encoded-values]])]))
                                    data)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Retrieval

(declare decode-elements)

(defn decode
  "Convert a datomized element to a collection [key value] pair"
  [entity element]
  (cond
   (instance? clojure.lang.ILookup element) (let [key (or (get element :element.map/key) (get element :element.vector/index))
                                                  value-attribute (first (filter #(re-matches #"^:element.value/.*" (str %)) (keys element)))
                                                  value (value-attribute element)]
                                              (cond
                                               (and key value-attribute) [key (decode-elements entity value-attribute value)]
                                               (not (nil? value-attribute)) value
                                               :else element))
   :else element))

(defn decode-elements
  "Convert datomized collection elements back into a collection."
  [entity key elements]
  (case elements
    #{:ref.vector/empty} []
    #{:ref.map/empty} {}
    (case (ref-type (.db entity) key)
      (:ref/map :ref.type/map) (apply hash-map (mapcat #(decode entity %) elements))
      (:ref/vector :ref.type/vector) (map last (sort-by first (map #(decode entity %) elements)))
      (:ref.type/value) (decode entity elements)
      elements)))

(defn undatomize
  [entity]
  (apply hash-map (mapcat (fn [k] [k (decode-elements entity k (get entity (str k)))]) (conj (keys entity) :db/id))))
