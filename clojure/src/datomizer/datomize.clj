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
;; Datom processing

(defn Datom->vector
  "Convert a Datom to a vector of [operation entity-id attribute value]"
  [datom]
  [(if (.added datom) :db/add :db/retract)
   (.e datom)
   (.a datom)
   (.v datom)])

(defn resolve-idents
  "Resolves any idents in a datom addition/retraction."
  [db [op e a v]]
  (let [ref? (= datomic.Attribute/TYPE_REF (.valueType (d/attribute db a)))]
    [op (d/entid db e) (d/entid db a) (if ref? (d/entid db v) v)]))

(defn transaction-datom?
  "Does this Datom refer to a transaction entity?"
  [db datum]
  (= :db.part/tx (d/ident db (d/part (.e datum)))))

(defn remove-transaction-datoms
  "Returns a list of datoms with transaction entity (creation) datoms removed."
  [db datoms]
  (remove (partial transaction-datom? db) datoms))

(defn flip-tx-data
  "Convert tx-data from a transaction result into a list of datom vectors usable with
   transact."
  [db tx-data]
  (->> tx-data
       (remove-transaction-datoms db)
       (map Datom->vector)))

(defn rehearse-transaction
  "Rehearses a transaction, returning a vector of datom addition/retraction vectors,
   suitable for re-submitting.  This flattens nested tx-data, resolves entity idents to ids, and runs transaction
   funcitons, capturing their result datoms.  Does not affect the real database!"
  [db datoms]
  (let [result (d/with db datoms)]
    (flip-tx-data db (:tx-data result))))

(defn remove-conflicts
  "Remove conflicting additions & retractions."
  [db additions retractions]
  (let [conflicts (clojure.set/intersection (set (map rest retractions)) (set (map rest additions)) )]
    (remove (fn [datom] (contains? conflicts (rest datom)))
            (concat retractions additions))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Storage

(def byte-array-class (class (byte-array 1))) ; is there a clojure literal for the byte-array class?

(defn element-value-attribute
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
    :ref.type/value nil
    nil nil))

(defn encode-value
  "Encode a value to a list of values/references and datoms to add or retract from the current context."
  [context value]
  (let [[encoded-value datoms] (encode context value)
        value-attribute (:attribute context)]
    [(:id context) (concat datoms
                           (if (sequential? encoded-value)
                             (map (fn [value]
                                    [(:operation context) (:id context) value-attribute value]) encoded-value)
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

(defn encode-empty [context]
  (let [id (determine-empty-marker-id context)]
    [id [[(:operation context) id :ref/empty true]]]))

(defmethod encode :ref.type/map [context value-to-encode]
  (if (empty? value-to-encode)
    (encode-empty context)
    (condense-elements (map (fn [[k, v]] (encode-pair context :element.map/key k v))
                            value-to-encode))))

(defmethod encode :ref.type/vector [context value-to-encode]
  (if (empty? value-to-encode)
    (encode-empty context)
    (condense-elements (map (fn [[i, v]] (encode-pair context :element.vector/index i v))
                            (zipmap (range) value-to-encode)))))

(defmethod encode :ref.type/value [context value-to-encode]
  (let [id (determine-variant-id context)]
    (encode-value (assoc context :id id :attribute (element-value-attribute value-to-encode)) value-to-encode)))

(defmethod encode nil [_ value-to-encode]
  (if (nil? value-to-encode)
    [:NIL []]
    [value-to-encode []]))

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
      (if key
        (if value-attribute
          [key (decode-elements entity value-attribute value)]
          [key nil]) ; should never happen

        (if value-attribute
          (if (= :element.value/nil value-attribute)
            nil
            value)
          element)))
    element))

(defn decode-elements
  "Convert datomized collection elements back into a collection."
  [entity key elements]
  (let [empty? (and (coll? elements) (some :ref/empty elements))]
    (case (ref-type (.db entity) key)
      (:ref/map :ref.type/map) (if empty? {} (apply hash-map (mapcat #(decode entity %) elements)))
      (:ref/vector :ref.type/vector) (if empty? [] (map last (sort-by first (map #(decode entity %) elements))))
      (:ref.type/value) (decode entity elements)
      (if (= :element.value/nil key)
        nil
        elements))))

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


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Integrity checks

(defn retrieve-all-elements [db]
  (let [rules '[[[element? ?e] [_ :test/map ?e]]]]
    (map (fn [x] (->> x first (d/entity db) d/touch)) (q '[:find ?e :in $ % :where (element? ?e)] db rules))))

(defn valid? [db element]
  (let [references (d/datoms db :vaet (:db/id element))
        ownerships (filter (fn [datom] (:is-component  (d/attribute db (.a datom)))) references)
        ownership (first ownerships)
        ownership-type (:ref/type (d/entity db (.a ownership)))
        attributes (apply hash-set (keys element))]
    (and  (= 1 (count ownerships))
          (case ownership-type
            :ref.type/map (or (:ref/empty element)
                              (and (contains? attributes :element.map/key)
                                   (not (contains? attributes :element.vector/index))
                                   (some #(re-matches #"^:element\.value/.*" (str %)) attributes)))
            :ref.type/vector (or (:ref/empty element)
                                 (and (not (contains? (keys element) :element.map/key))
                                      (contains? attributes :element.vector/index)
                                      (some #(re-matches #"^:element\.value/.*" (str %)) attributes)))
            :ref.type/value (and (not (contains? attributes :element.map/key))
                                 (not (contains? attributes :element.vector/index))
                                 (some #(re-matches #"^:element\.value/.*" (str %)) attributes))))))

(defn invalid-elements [db]
  (remove (partial valid? db) (retrieve-all-elements db)))
