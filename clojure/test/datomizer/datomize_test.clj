(ns datomizer.datomize-test
  "Tests for datomize."
  (:require [clojure.pprint :refer [pprint]]
            [clojure.test :refer :all]
            [datomic.api :as d :refer [db]]
            [datomizer.datomize.decode :refer :all]
            [datomizer.datomize.encode :refer :all]
            [datomizer.datomize.setup :refer :all]
            [datomizer.datomize.validation :refer :all]
            [datomizer.test-utility.check :refer [datomizable-value
                                                  edenizable-value]]
            [datomizer.utility.byte-array :refer :all]
            [datomizer.utility.debug :refer :all]
            [datomizer.utility.misc :refer [ref-type]]
            [simple-check.clojure-test :refer [defspec]]
            [simple-check.properties :as prop]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Fixtures

(def test-schema
  [{:db/id (d/tempid :db.part/db)
    :db/ident :test/map
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :db/unique :db.unique/value
    :db/doc "A reference attribute for testing map marshalling"
    :db/isComponent true
    :dmzr.ref/type :dmzr.ref.type/map
    :db.install/_attribute :db.part/db}
   {:db/id (d/tempid :db.part/db)
    :db/ident :test/vector
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :db/unique :db.unique/value
    :db/doc "A reference attribute for testing vector marshalling"
    :db/isComponent true
    :dmzr.ref/type :dmzr.ref.type/vector
    :db.install/_attribute :db.part/db}
   {:db/id (d/tempid :db.part/db)
    :db/ident :test/value
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/value
    :db/doc "A reference attribute for testing variant marshalling"
    :db/isComponent true
    :dmzr.ref/type :dmzr.ref.type/variant
    :db.install/_attribute :db.part/db}
   {:db/id (d/tempid :db.part/db)
    :db/ident :test/edn
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "An EDN string field for edenization testing."
    :dmzr.ref/type :dmzr.ref.type/edn
    :db.install/_attribute :db.part/db}
   {:db/id (d/tempid :db.part/db)
    :db/ident :test/names
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/many
    :db/doc "A multiple string attribute for testing."
    :db.install/_attribute :db.part/db}])

(defn load-datomizer-test-schema [dbc]
  (d/transact dbc test-schema))

(defonce test-database (atom nil))

;;(def test-database-uri "datomic:dev://localhost:4334/datomizer-test")
(def test-database-uri "datomic:mem://datomizer-test")

(defn delete-test-database []
  (when @test-database
    (d/delete-database test-database-uri)
    (reset! test-database nil)))

(defn fresh-dbc
  "Create a fresh database for the test"
  []
  (do
    (delete-test-database)
    (d/create-database test-database-uri)
    (let [dbc (d/connect test-database-uri)]
      (load-datomizer-schema dbc)
      (load-datomizer-functions dbc)
      (load-datomizer-test-schema dbc)
      (reset! test-database dbc)
      dbc)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Round-Trip Testing

(defn test-attribute
  "Determine the correct test attribute for datomizing value."
  [value]
  (cond (map? value) :test/map
        (vector? value) :test/vector
        :else :test/value))

(defn marshal-test-entity [dbc marshalling-function attribute value & {:keys [id]}]
  (let [id (or id (d/tempid :db.part/user))
        entity-data {:db/id id
                    :db/doc "Test entity."
                    attribute value}
        tx-result @(d/transact dbc [[marshalling-function entity-data]])
        entity-id (if (number? id) id (d/resolve-tempid (:db-after tx-result) (:tempids tx-result) id))
        entity (d/entity (:db-after tx-result) entity-id)]
    (d/touch entity)))

(defn datomize-test-entity [dbc value & {:keys [id]}]
  (marshal-test-entity dbc :dmzr/datomize (test-attribute value) value :id id ))

(defn edenize-test-entity [dbc value & {:keys [id]}]
  (marshal-test-entity dbc :dmzr/datomize :test/edn value :id id ))

(defn round-trip-via-datomize
  "Store, then retrieve a value to/from Datomic using datomization."
  [dbc value]
  (let [entity (datomize-test-entity dbc value)
        data (undatomize entity)]
    ((test-attribute value) data)))

(defn round-trip-via-edenize
  "Store, then retrieve a value to/from Datomic using edenization."
  [dbc value]
  (let [entity (edenize-test-entity dbc value)
        data (undatomize entity)]
    (:test/edn data)))

(defn round-trip-edenize-test
  "Test that a value is stored and retrieved from Datomic."
  [value]
  (is (equivalent? value (round-trip-via-edenize (fresh-dbc) value))))

(defn round-trip-datomize-test
  "Test that a value is stored and retrieved from Datomic."
  [value]
  (is (equivalent? value (round-trip-via-datomize (fresh-dbc) value))))


(def datomizables [23 nil {} {:a 1} {:a 1, :b 2} {:a 1, :z {:aa 1, :bb 2, :cc {:aaa 1, :bbb 2}}} [] [1] [1 2 3] [1 2 [11 22 33] 3] :a (byte-array [(byte 1) (byte 2)])])

(deftest test-datomize
  (map println datomizables)
  (round-trip-datomize-test 23)
  (round-trip-datomize-test nil)
  (round-trip-datomize-test {})
  (round-trip-datomize-test {:a 1})
  (round-trip-datomize-test {:a 1 :b 2})
  (round-trip-datomize-test {:a 1 :z {:aa 1 :bb 2 :cc {:aaa 1 :bbb 2}}})
  (round-trip-datomize-test [])
  (round-trip-datomize-test [1])
  (round-trip-datomize-test [1 2 3])
  (round-trip-datomize-test [1 2 [11 22 33] 3])
  (round-trip-datomize-test :a)
  (round-trip-datomize-test (byte-array [(byte 1) (byte 2)])))

(deftest test-edenize
  (round-trip-edenize-test 23)
  (round-trip-edenize-test nil)
  (round-trip-edenize-test {})
  (round-trip-edenize-test {:a 1})
  (round-trip-edenize-test {:a 1 :b 2})
  (round-trip-edenize-test {:a 1 :z {:aa 1 :bb 2 :cc {:aaa 1 :bbb 2}}})
  (round-trip-edenize-test [])
  (round-trip-edenize-test [1])
  (round-trip-edenize-test [1 2 3])
  (round-trip-edenize-test [1 2 [11 22 33] 3])
  (round-trip-edenize-test :a)
  (round-trip-edenize-test [[[-0.1]]])
  ; edenizing byte-arrays is not supported
  )


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Update tests

(defn update-via-datomize [dbc initial-value subsequent-value]
  (let [initial-entity (datomize-test-entity dbc initial-value)
        result-entity (datomize-test-entity dbc subsequent-value :id (:db/id initial-entity))]
    ((test-attribute subsequent-value) (undatomize result-entity))))


(defn update-via-edenize [dbc initial-value subsequent-value]
  (let [initial-entity (edenize-test-entity dbc initial-value)
        result-entity (edenize-test-entity dbc subsequent-value :id (:db/id initial-entity))]
    (:test/edn (undatomize result-entity))))


(defn update-via-datomize-test
  "Test that a value stored in Datomic can be updated (without creating malformed elements)."
  [initial-value subsequent-value]
  (let [dbc (fresh-dbc)]
    (is (equivalent? subsequent-value (update-via-datomize dbc initial-value subsequent-value)))
    (is (= [] (invalid-elements (db dbc))))))

(defn update-via-edenize-test
  "Test that a value stored in Datomic can be updated (without creating malformed elements)."
  [initial-value subsequent-value]
  (let [dbc (fresh-dbc)]
    (is (equivalent? subsequent-value (update-via-edenize dbc initial-value subsequent-value)))
    (is (= [] (invalid-elements (db dbc))))))

(deftest test-update-via-datomize

  (testing "map update-via-datomize"
    (update-via-datomize-test {:same "stays the same", :old "is retracted", :different "gets changed" :nested {:a 1 :b 2 :c 3}}
                 {:same "stays the same", :new "is added", :different "see, now different!" :nested {:a 1 :b 4 :d 5} }))

  (testing "updating a byte-array"
    (update-via-datomize-test (byte-array [(byte 11) (byte 22)])
                 (byte-array [(byte 33) (byte 44)])))

  (testing "updating a map with nil values"
    (update-via-datomize-test {}
                 {:0 nil})))

(deftest test-update-via-edenize

  (testing "map update-via-edenize"
    (update-via-edenize-test {:same "stays the same", :old "is retracted", :different "gets changed" :nested {:a 1 :b 2 :c 3}}
                 {:same "stays the same", :new "is added", :different "see, now different!" :nested {:a 1 :b 4 :d 5} }))

  (testing "updating a map with nil values"
    (update-via-edenize-test {}
                 {:0 nil})))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Unit Testing

(deftest test-attribute-for-value
  (testing "with a String"
    (is (= :dmzr.element.value/string (attribute-for-value "I'm a string!"))))
  (testing "with a vector"
    (is (= :dmzr.element.value/vector (attribute-for-value [:a :vector]))))
  (testing "with an java ArrayList"
    (is (= :dmzr.element.value/vector (attribute-for-value (java.util.ArrayList. [:an "arraylist"])))))
  (testing "with a clojure map"
    (is (= :dmzr.element.value/map (attribute-for-value {:a "map"}))))
  (testing "with a java Map"
    (is (= :dmzr.element.value/map (attribute-for-value (java.util.HashMap. {:a "hashmap"})))))
  (testing "with a Long"
    (is (= :dmzr.element.value/long (attribute-for-value 23))))
  (testing "with a Float"
    (is (= :dmzr.element.value/float (attribute-for-value (float 23.1)))))
  (testing "with a Double"
    (is (= :dmzr.element.value/double (attribute-for-value 23.1))))
  (testing "with a Boolean"
    (is (= :dmzr.element.value/boolean (attribute-for-value true))))
  (testing "with a Date"
    (is (= :dmzr.element.value/instant (attribute-for-value (java.util.Date.)))))
  (testing "with a keyword"
    (is (= :dmzr.element.value/keyword (attribute-for-value :keyword))))
  (testing "with a BigDecimal"
    (is (= :dmzr.element.value/bigdec (attribute-for-value (java.math.BigDecimal. 23)))))
  (testing "with a BigInteger"
    (is (= :dmzr.element.value/bigint (attribute-for-value (java.math.BigInteger. "23")))))
  (testing "with a byte array"
    (is (= :dmzr.element.value/bytes (attribute-for-value (byte-array 1)))))
  (testing "with something unsupported"
    (is (thrown? java.lang.IllegalArgumentException (attribute-for-value (Object.))))))

(deftest test-ref-type
  (testing "with an attribute representing a map"
    (let [dbc (fresh-dbc)
          tx-result @(d/transact dbc [{:db/id (d/tempid :db.part/user)
                                       :test/map {:db/id (d/tempid :db.part/user)
                                                  :dmzr.ref/empty true}}])
          entity-id (first (vals (:tempids tx-result)))
          entity (d/entity (db dbc) entity-id)]
      (is (= :dmzr.ref.type/map (ref-type (db dbc) :test/map)))))

  (testing "with an attribute representing a vector"
    (let [dbc (fresh-dbc)
          tx-result @(d/transact dbc [{:db/id (d/tempid :db.part/user)
                                         :test/vector {:db/id (d/tempid :db.part/user)
                                                  :dmzr.ref/empty true}}])
          entity-id (first (vals (:tempids tx-result)))
          entity (d/entity (db dbc) entity-id)]
      (is (= :dmzr.ref.type/vector (ref-type (db dbc) :test/vector))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Generative Testing

(def prop-round-trip-via-datomize
  (prop/for-all [value datomizable-value]
                (let [dbc (fresh-dbc)
                      result (round-trip-via-datomize dbc value)
                      garbage (invalid-elements (db dbc))]
                  (when-not (= 0 (count garbage))
                    (print "invalid elements: ")
                    (pprint garbage))
                  (and (= [] garbage)
                       (equivalent? value result)))))
(defspec quickcheck-round-trip-via-datomize 30 prop-round-trip-via-datomize)

(def prop-update-via-datomize
  (prop/for-all [initial-value datomizable-value
                 subsequent-value datomizable-value]
                (let [dbc (fresh-dbc)
                      result (update-via-datomize dbc initial-value subsequent-value)
                      garbage (invalid-elements (db dbc))]
                  (when-not (= 0 (count garbage))
                    (print "invalid elements: ")
                    (pprint garbage))
                  (and (= [] garbage)
                       (equivalent? subsequent-value result)))))

(defspec quickcheck-update-via-datomize 30 prop-update-via-datomize)

(def prop-round-trip-via-edenize
  (prop/for-all [value edenizable-value]
                (let [dbc (fresh-dbc)
                      result (round-trip-via-edenize dbc value)
                      garbage (invalid-elements (db dbc))]
                  (when-not (= 0 (count garbage))
                    (print "invalid elements: ")
                    (pprint garbage))
                  (and (= [] garbage)
                       (equivalent? value result)))))

(defspec quickcheck-round-trip-via-edenize 30 prop-round-trip-via-edenize)

(def prop-update-via-edenize
  (prop/for-all [initial-value edenizable-value
                 subsequent-value edenizable-value]
                (let [dbc (fresh-dbc)
                      result (update-via-edenize dbc initial-value subsequent-value)
                      garbage (invalid-elements (db dbc))]
                  (when-not (= 0 (count garbage))
                    (print "invalid elements: ")
                    (pprint garbage))
                  (and (= [] garbage)
                       (equivalent? subsequent-value result)))))

(defspec quickcheck-update-via-edenize 30 prop-update-via-edenize)
