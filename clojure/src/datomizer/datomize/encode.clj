(ns datomizer.datomize.encode
  "Encode entities by datomizing their data-structures."
  (:require [datomic.api :as d :refer [q]]
            [datomizer.datomize.datoms :refer :all]
            [datomizer.utility.byte-array :refer [byte-array-class]]))


(defn ref-type
  "Determine the reference type of an attribute."
  [db key]
  (let [attribute (d/entity db (keyword key))]
    (:ref/type attribute)))

(defn attribute-for-value
  "Datomic attribute to use for element value, based on its type."
  [value]
  (if (nil? value)
    :element.value/nil
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
      (throw (java.lang.IllegalArgumentException. (str "Marshalling not supported for type " (.toString (class value)))))
      )))


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

(defn determine-empty-marker-id [context]
  (or (ffirst (q '[:find ?e
                   :in $ ?attribute ?parent-id
                   :where
                   [?parent-id ?attribute ?e]
                   [?e :ref/empty true]]
                 (:db context)
                 (:attribute context)
                 (:id context)))
      (d/tempid (:partition context))))

(defn determine-variant-id [context]
  (or (ffirst (q '[:find ?e
                    :in $ ?attribute ?parent-id
                    :where
                    [?parent-id ?attribute ?e]]
                  (:db context)
                  (:attribute context)
                  (:id context)))
      (d/tempid (:partition context))))


(defn determine-key-attribute [context]
  (case (ref-type (:db context) (:attribute context))
    :ref.type/map :element.map/key
    :ref.type/vector :element.vector/index
    :ref.type/variant nil
    nil nil))

(defn encode-value
  "Encode a value to a list of values/references and datoms to add or
  retract from the current context."
  [context value]
  (let [[encoded-value datoms] (encode context value)
        value-attribute (:attribute context)]
    [(:id context)
     (concat datoms
             (if (sequential? encoded-value)
               (map (fn [value]
                      [(:operation context) (:id context) value-attribute value]) encoded-value)
               [[(:operation context) (:id context) value-attribute encoded-value]]))]))

(defn encode-pair
  "Encode a key/value or index/value pair as a list of references and
  datoms to add or retract to the current context."
  [context key-attribute k v]
  (let [id (determine-element-id context key-attribute k)
        value-attribute (attribute-for-value v)
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

(defn encode-empty [context]
  (let [id (determine-empty-marker-id context)]
    [id [[(:operation context) id :ref/empty true]]]))

(defmethod encode :ref.type/map [context value]
  (if (empty? value)
    (encode-empty context)
    (condense-elements (map (fn [[k, v]] (encode-pair context :element.map/key k v))
                            value))))

(defmethod encode :ref.type/vector [context value]
  (if (empty? value)
    (encode-empty context)
    (condense-elements (map (fn [[i, v]] (encode-pair context :element.vector/index i v))
                            (zipmap (range) value)))))

(defmethod encode :ref.type/variant [context value]
  (let [id (determine-variant-id context)]
    (encode-value (assoc context :id id :attribute (attribute-for-value value)) value)))

(defmethod encode nil [_ value]
  (if (nil? value)
    [:NIL []]
    [value []]))

(defn encode-data [context data]
  (mapcat (fn [[attribute, value]]
            (second (encode-value (assoc context :attribute attribute) value)))
          data))

(defn datomize
  [db entity & {:keys [partition] :or {partition :db.part/user}}]
  (let [id (:db/id entity)
        data (dissoc entity :db/id)
        context (map->Context {:db db, :operation :db/add, :partition partition, :id id})]
    (let [retractions (rehearse-transaction db [[:db.fn/retractEntity id]])
          additions (map (partial resolve-idents db) (encode-data context data))]
      (remove-conflicts db additions retractions))))
