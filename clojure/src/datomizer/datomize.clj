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


(defrecord Context [operation   ; What operation we're currently performing: :db/add or :db/retract
                    db          ; The database.
                    partition   ; The partition where we're putting new datoms. Used for tempids.
                    id          ; Id of the entity to which we are attaching a value.
                    attribute]) ; Attribute on the entity which will point to this value.

(defmulti encode
  "Encode a value as datoms.
  Returns a pair of values: the to assign to the parent attribute
  and a vec of datoms to transact."
  (fn [context value-to-encode]
    (ref-type (:db context) (:attribute context))))


(defn determine-element-id [context key-attribute key]
  (or (ffirst (q '[:find ?e
                   :in $ ?attribute ?parent-id ?key-attribute ?key
                   :where
                   [?parent-id ?attribute ?e]
                   [?e ?key-attribute ?key]]
                 (:db context)
                 (:attribute context)
                 (:id context)
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
                           (:attribute context)
                           (:id context)))))


(defn determine-key-attribute [context]
  (case (ref-type (:db context) (:attribute context))
    :ref.type/map :element.map/key
    :ref.type/vector :element.vector/index
    :ref.type/value nil
    nil nil))

(defn encode-value
  "Encode a value to a list of values/references and datoms to add or retract from the current context."
  [context value]
  (let [[encoded-value datoms] (encode context value)
        value-attribute (:attribute context)]
    [(:id context) (concat datoms
                           (if (sequential? encoded-value)
                             (map (fn [encoded-value]
                                    [(:operation context) (:id context) value-attribute encoded-value]) encoded-value)
                             [[(:operation context) (:id context) value-attribute encoded-value]]))]))

(defn encode-pair
  "Encode a key/value or index/value pair as a list of references and datoms to add or retract to the current context."
  [context key-attribute k v]
  (let [id (determine-element-id context key-attribute k)
        value-attribute (element-value-attribute v)
        element-context (assoc context :id id :attribute value-attribute )
        key-datom [(:operation context) id key-attribute k]]
    (let [[values datoms] (encode-value element-context v)]
      [values (conj datoms key-datom)])))

(defn condense-elements
  "Merge a list of value - datom list pairs."
  [elements]
  (reduce (fn [[accumulated-values accumulated-datoms]
              [value datoms]]
            [(concat accumulated-values (flatten [value])) (concat accumulated-datoms datoms)])
          [[] []]
          elements))

(defmethod encode :ref.type/map [context value-to-encode]
  (when-not (map? value-to-encode)
    (throw (java.lang.IllegalArgumentException. (str (:attribute context) " expects a map. Got " value-to-encode)) ))
  (if (empty? value-to-encode)
    [:ref.map/empty []]
    (condense-elements (map (fn [[k, v]] (encode-pair context :element.map/key k v))
                            value-to-encode))))

(defmethod encode :ref.type/vector [context value-to-encode]
  (when-not (vector? value-to-encode)
    (throw (java.lang.IllegalArgumentException. (str (:attribute context) " expects a vector. Got " value-to-encode)) ))
  (if (empty? value-to-encode)
    [:ref.vector/empty []]
    (condense-elements (map (fn [[i, v]] (encode-pair context :element.vector/index i v))
                            (zipmap (range) value-to-encode)))))

(defmethod encode :ref.type/value [context value-to-encode]
  (let [id (determine-variant-id context)]
    [id [[(:operation context) id (element-value-attribute value-to-encode) value-to-encode]]]))

(defmethod encode nil [_ value-to-encode]
  [value-to-encode []])

(defn encode-data [context data]
  (mapcat (fn [[attribute, value]]
            (second (encode-value (assoc context :attribute attribute) value)))
          data))

(defn remove-conflicts
  "Remove conflicting additions & retractions."
  [additions retractions]
  (let [conflicts (clojure.set/intersection (apply hash-set (map rest retractions)) (apply hash-set (map rest additions)))]
    (let [conflict? (fn [datom] (contains? conflicts (rest datom)))
          datoms (remove conflict? (concat retractions additions))]
      datoms)))

(declare undatomize)

(defn datomize
  [db entity & {:keys [partition] :or {partition :db.part/user}}]
  (let [id (:db/id entity)
        data (dissoc entity :db/id)
        existing-entity (if (pos? (d/entid db id)) (dissoc (undatomize (d/entity db id)) :db/id) {})
        [data-to-add data-to-retract _] (clojure.data/diff data existing-entity)
        context (map->Context {:db db :partition partition :id id})]
    (let [retractions (encode-data (assoc context :operation :db/retract) data-to-retract)
          additions (encode-data (assoc context :operation :db/add) data-to-add)]
      (remove-conflicts additions retractions))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Retrieval

(declare decode-elements)

(defn decode
  "Convert a datomized element to a collection [key value] pair"
  [entity element]
  (if (instance? clojure.lang.ILookup element)
    (let [key (or (get element :element.map/key) (get element :element.vector/index))
          value-attribute (first (filter #(re-matches #"^:element.value/.*" (str %)) (keys element)))
          value (value-attribute element)]
      (cond
       (and key value-attribute) [key (decode-elements entity value-attribute value)]
       (not (nil? value-attribute)) value
       :else element))
    element))

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

