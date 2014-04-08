(ns datomizer.datomize.encode
  "Encode entities by datomizing their data-structures."
  (:require [datomic.api :as d :refer [q]]
            [datomizer.datoms :refer :all]
            [datomizer.utility.debug :refer :all]
            [datomizer.utility.misc :refer [ref-type]]))


(defprotocol DatomizerValue
  (attribute-for-value [value] "Datomizer attribute to use for an element value."))

(extend-type nil DatomizerValue
        (attribute-for-value [v] :dmzr.element.value/nil))

(extend-type java.lang.String DatomizerValue
        (attribute-for-value [v] :dmzr.element.value/string))

(extend-type java.lang.Long DatomizerValue
        (attribute-for-value [v] :dmzr.element.value/long))

(extend-type java.lang.Float DatomizerValue
        (attribute-for-value [v] :dmzr.element.value/float))

(extend-type java.lang.Double DatomizerValue
        (attribute-for-value [v] :dmzr.element.value/double))

(extend-type java.lang.Boolean DatomizerValue
        (attribute-for-value [v] :dmzr.element.value/boolean))

(extend-type java.util.Date DatomizerValue
        (attribute-for-value [v] :dmzr.element.value/instant))

(extend-type clojure.lang.Keyword DatomizerValue
        (attribute-for-value [v] :dmzr.element.value/keyword))

(extend-type java.util.List DatomizerValue
        (attribute-for-value [v] :dmzr.element.value/vector))

(extend-type java.util.Map DatomizerValue
        (attribute-for-value [v] :dmzr.element.value/map))

(extend-type java.math.BigDecimal DatomizerValue
        (attribute-for-value [v] :dmzr.element.value/bigdec))

(extend-type java.math.BigInteger DatomizerValue
        (attribute-for-value [v] :dmzr.element.value/bigint))

(extend-type (Class/forName "[B") DatomizerValue
        (attribute-for-value [v] :dmzr.element.value/bytes))

(defn tempid-from-same-partition [db id]
  (d/tempid (d/part (d/entid db id))))

(defn determine-element-id
  "Entity id of existing element, if any. Otherwise, a tempid."
  [db id attribute key-attribute key]
  (or (ffirst (q '[:find ?e
                   :in $ ?attribute ?parent-id ?key-attribute ?key
                   :where
                   [?parent-id ?attribute ?e]
                   [?e ?key-attribute ?key]]
                 db
                 attribute
                 id
                 key-attribute
                 key))
      (tempid-from-same-partition db id)))

(defn determine-empty-marker-id
  "Entity id of existing element, if any. Otherwise, a tempid."
  [db id attribute]
  (or (ffirst (q '[:find ?e
                   :in $ ?attribute ?parent-id
                   :where
                   [?parent-id ?attribute ?e]
                   [?e :dmzr/empty true]]
                 db
                 attribute
                 id))
      (tempid-from-same-partition db id)))

(defn determine-variant-id [db id attribute]
  "Entity id of existing variant, if any. Otherwise, a tempid."
  (or (ffirst (q '[:find ?e
                   :in $ ?attribute ?parent-id
                   :where
                   [?parent-id ?attribute ?e]]
                 db
                 attribute
                 id))
      (tempid-from-same-partition db id)))



(def key-attributes {:dmzr.type/map :dmzr.element.map/key
                     :dmzr.type/vector :dmzr.element.vector/index})

(defn concat-nested
  "Concatenate nested vectors.
  [[[:a :b] [1 2]] [[:c :d] [3 4]]] -> [[:a :b :c :d] [1 2 3 4]]"
  [x]
  (mapv vec (apply map concat x)))

(declare encode-value)

(defn encode-key
  "Encode an element key as a datom."
  [id key-attribute k]
  [:db/add id key-attribute k])

(defn encode-pair
  "Encode a key/value or index/value pairs as datoms."
  [db collection-id attribute k v]
  (let [key-attribute ((ref-type db attribute) key-attributes)
        element-id (determine-element-id db collection-id attribute key-attribute k)]
    (conj (encode-value db element-id (attribute-for-value v) v)
          (encode-key element-id key-attribute k)
          [:db/add collection-id attribute element-id])))

(defn encode-empty [db id attribute]
  (let [element-id (determine-empty-marker-id db id attribute)]
    [[:db/add element-id :dmzr/empty true]
     [:db/add id attribute element-id]]))

(defmulti encode-value
  "Encode a value as datoms."
  (fn [db id attribute value-to-encode]
    (ref-type db attribute)))

(defmethod encode-value :dmzr.type/map
  [db id attribute value]
  (if (empty? value)
    (encode-empty db id attribute)
    (mapcat (fn [[k v]] (encode-pair db id attribute k v))
            value)))

(defmethod encode-value :dmzr.type/vector
  [db id attribute value]
  (if (empty? value)
    (encode-empty db id attribute)
    (apply concat (map-indexed (fn [i v] (encode-pair db id attribute i v))
                      value))))

(defmethod encode-value :dmzr.type/variant
  [db id attribute value]
  (let [variant-id (determine-variant-id db id attribute)]
    (conj (encode-value db variant-id (attribute-for-value value) value)
          [:db/add id attribute variant-id])))

(defmethod encode-value :dmzr.type/edn
  [db id attribute value]
  [[:db/add id attribute (pr-str value)]])

(defmethod encode-value nil ; attribute is not annotated with a datomizer type.
  [db id attribute value]
  (if (nil? value)
    [[:db/add id attribute :NIL]]
    [[:db/add id attribute value]]))

(defn datomize
  [db entity]
  (let [id (:db/id entity)
        data (dissoc entity :db/id)
        retractions (rehearse-transaction db [[:db.fn/retractEntity id]])
        additions (map (partial resolve-idents db)
                       (mapcat (fn [[attribute, value]]
                                 (encode-value db id attribute value))
                               data))]
    (remove-conflicts db additions retractions)))
