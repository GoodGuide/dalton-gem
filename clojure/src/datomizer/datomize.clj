(ns datomizer.datomize
  (:require [datomizer.debug :refer :all]
            [datomizer.utility :refer :all]
            clojure.data
            [clojure.string :as str]
            [datomic.api :as d :refer (db q)]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Schema

(defn load-datomizer-schema
  "Load the schema used by datomizer."
  [dbc]
  (load-datoms-from-edn-resource-file dbc "datomizer-schema.edn"))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Introspection

(defn ref-type
  "Determine the reference type of an attribute."
  [db key]
  (let [attribute (d/entity db (keyword key))]
    (:ref/type attribute)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Storage

(def byte-array-class (class (byte-array 1))) ; is there a clojure literal for the byte-array class?

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
    byte-array-class :element.value/bytes
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

(defrecord Context [operation db partition parent-entity-id attribute-on-parent])

(defmulti encode
  "Encode a value as datoms.
  Returns a pair of values: the to assign to the parent attribute
  and a vec of datoms to transact."
  (fn [context value-to-encode]
    (ref-type (:db context) (:attribute-on-parent context))))


(defn determine-element-id [context key-attribute key]
  (or (ffirst (q '[:find ?e
                   :in $ ?attribute ?parent-id ?key-attribute ?key
                   :where
                   [?parent-id ?attribute ?e]
                   [?e ?key-attribute ?key]]
                 (:db context)
                 (:attribute-on-parent context)
                 (:parent-entity-id context)
                 key-attribute
                 key))
      (d/tempid (:partition context))))

(defn determine-variant-id [context]
  (case (:operation context)
    :db/add (d/tempid (:partition context))
    :db/retract (ffirst (q '[:find ?e
                             :in $ ?attribute ?parent-id
                             :where
                             [?parent-id ?attribute ?e]]
                           (:db context)
                           (:attribute-on-parent context)
                           (:parent-entity-id context)))))

(defn encode-pair [context key-attribute k v]
  (let [element-id (determine-element-id context key-attribute k)
        value-attribute (element-value-attribute v)
        element-context (assoc context :parent-entity-id element-id :attribute-on-parent value-attribute )
        [encoded-values datoms] (encode element-context v)]
    [element-id (concat datoms
                        [[(:operation context) element-id key-attribute k]]
                        (if (sequential? encoded-values)
                          (map (fn [encoded-value] [(:operation context) element-id value-attribute encoded-value]) encoded-values)
                          [[(:operation context) element-id value-attribute encoded-values]]))]))

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
  (let [id (determine-variant-id context)]
    [id [[(:operation context) id (element-value-attribute value-to-encode) value-to-encode]]]))

(defmethod encode nil [_ value-to-encode]
  [value-to-encode []])


(declare undatomize)

(defn datomize
  [db entity & {:keys [partition] :or {partition :db.part/user}}]
  (let [entity-id (:db/id entity)
        data (dissoc entity :db/id)
        existing-entity (if (pos? (d/entid db entity-id)) (dissoc (undatomize (d/entity db entity-id)) :db/id) {})
        [new-pairs obsolete-pairs unchanged-pairs] (clojure.data/diff data existing-entity)]
    (let [retractions (second (condense-elements (map (fn [[attribute, value]]
                                                        (let [[encoded-values datoms] (encode (->Context :db/retract db partition entity-id attribute) value)]
                                                          [entity-id (concat datoms
                                                                             (if (sequential? encoded-values)
                                                                             (map (fn [encoded-value] [:db/retract entity-id attribute encoded-value]) encoded-values)
                                                                             [[:db/retract entity-id attribute encoded-values]]))]))
                                                      obsolete-pairs)))
          additions (second (condense-elements (map (fn [[attribute, value]]
                                                      (let [[encoded-values datoms] (encode (->Context :db/add db partition entity-id attribute) value)]
                                                        [entity-id (concat datoms
                                                                           (if (sequential? encoded-values)
                                                                             (map (fn [encoded-value] [:db/add entity-id attribute encoded-value]) encoded-values)
                                                                             [[:db/add entity-id attribute encoded-values]]))]))
                                                    new-pairs)))]
      (let [conflicts (clojure.set/intersection (apply hash-set (map rest retractions)) (apply hash-set (map rest additions)))]
        (let [conflict? (fn [datom] (contains? conflicts (rest datom)))
              datoms (remove conflict? (concat retractions additions))]
          datoms)))))

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
