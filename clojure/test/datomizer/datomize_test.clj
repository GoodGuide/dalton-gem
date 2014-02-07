(ns datomizer.datomize-test
  "Tests for datomize."
  (:require [clojure.pprint :refer [pprint]]
            [clojure.test :refer :all]
            [datomic.api :as d :refer [db]]
            [datomizer.datomize.decode :refer :all]
            [datomizer.datomize.encode :refer :all]
            [datomizer.datomize.setup :refer [load-datomizer-schema]]
            [datomizer.datomize.validation :refer :all]
            [datomizer.test-utility.check :refer [marshalable-value]]
            [datomizer.utility.byte-array :refer :all]
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
    :ref/type :ref.type/map
    :db.install/_attribute :db.part/db}
   {:db/id (d/tempid :db.part/db)
    :db/ident :test/vector
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :db/unique :db.unique/value
    :db/doc "A reference attribute for testing vector marshalling"
    :db/isComponent true
    :ref/type :ref.type/vector
    :db.install/_attribute :db.part/db}
   {:db/id (d/tempid :db.part/db)
    :db/ident :test/value
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/value
    :db/doc "A reference attribute for testing variant marshalling"
    :db/isComponent true
    :ref/type :ref.type/value
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
      (load-datomizer-test-schema dbc)
      (reset! test-database dbc)
      dbc)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Round-Trip Testing

(defn test-attribute
  "Determine the correct reference type for a value."
  [value]
  (cond (map? value) :test/map
        (vector? value) :test/vector
        :else :test/value))

(defn store-test-entity [dbc value & {:keys [id]}]
  (let [id (or id (d/tempid :db.part/user))
        entity-map {:db/id id
                    :db/doc "Test entity."
                    (test-attribute value) value}
        entity-datoms (datomize (db dbc) entity-map)
        tx-result @(d/transact dbc entity-datoms)
        entity-id (if (number? id) id (d/resolve-tempid (:db-after tx-result) (:tempids tx-result) id))
        entity (d/entity (:db-after tx-result) entity-id)]
    (d/touch entity)))

(defn round-trip
  "Store, then retrieve a value to/from Datomic."
  [dbc value]
  (let [entity (store-test-entity dbc value)
        data (undatomize entity)]
    ((test-attribute value) data)))

(defn round-trip-test
  "Test that a value is stored and retrieved from Datomic."
  [value]
  (is (equivalent? value (round-trip (fresh-dbc) value))))

(deftest test-datomize
  (testing "of a number"
    (round-trip-test 23))
  (testing "of a nil"
    (round-trip-test nil))
  (testing "of an empty map"
    (round-trip-test {}))
  (testing "a map with one pair"
    (round-trip-test {:a 1}))
  (testing "a map with multiple pairs"
    (round-trip-test {:a 1 :b 2}))
  (testing "a nested map"
    (round-trip-test {:a 1 :z {:aa 1 :bb 2 :cc {:aaa 1 :bbb 2}}}))
  (testing "an empty vector"
    (round-trip-test []))
  (testing "a vector with one element"
    (round-trip-test [1]))
  (testing "a vector with many elements"
    (round-trip-test [1 2 3]))
  (testing "a nested vector"
    (round-trip-test [1 2 [11 22 33] 3]))
  (testing "a keyword value"
    (round-trip-test :a))
  (testing "a byte array value"
    (round-trip-test (byte-array [(byte 1) (byte 2)]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Update tests

(defn update [dbc initial-value subsequent-value]
  (let [initial-entity (store-test-entity dbc initial-value)
        result-entity (store-test-entity dbc subsequent-value :id (:db/id initial-entity))]
    ((test-attribute subsequent-value) (undatomize result-entity))))

(defn update-test
  "Test that a value stored in Datomic can be updated (without creating malformed elements)."
  [initial-value subsequent-value]
  (let [dbc (fresh-dbc)]
    (is (equivalent? subsequent-value (update dbc initial-value subsequent-value)))
    (is (= [] (invalid-elements (db dbc))))))

(deftest test-update

  (testing "map update"
    (update-test {:same "stays the same", :old "is retracted", :different "gets changed" :nested {:a 1 :b 2 :c 3}}
                 {:same "stays the same", :new "is added", :different "see, now different!" :nested {:a 1 :b 4 :d 5} }))

  (testing "updating a byte-array"
    (update-test (byte-array [(byte 11) (byte 22)])
                 (byte-array [(byte 33) (byte 44)])))

  (testing "updating a map with nil values"
    (update-test {}
                 {:0 nil})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Unit Testing

(deftest test-attribute-for-value
  (testing "with a String"
    (is (= :element.value/string (attribute-for-value "I'm a string!"))))
  (testing "with a vector"
    (is (= :element.value/vector (attribute-for-value [:a :vector]))))
  (testing "with an java ArrayList"
    (is (= :element.value/vector (attribute-for-value (java.util.ArrayList. [:an "arraylist"])))))
  (testing "with a clojure map"
    (is (= :element.value/map (attribute-for-value {:a "map"}))))
  (testing "with a java Map"
    (is (= :element.value/map (attribute-for-value (java.util.HashMap. {:a "hashmap"})))))
  (testing "with a Long"
    (is (= :element.value/long (attribute-for-value 23))))
  (testing "with a Float"
    (is (= :element.value/float (attribute-for-value (float 23.1)))))
  (testing "with a Double"
    (is (= :element.value/double (attribute-for-value 23.1))))
  (testing "with a Boolean"
    (is (= :element.value/boolean (attribute-for-value true))))
  (testing "with a Date"
    (is (= :element.value/instant (attribute-for-value (java.util.Date.)))))
  (testing "with a keyword"
    (is (= :element.value/keyword (attribute-for-value :keyword))))
  (testing "with a BigDecimal"
    (is (= :element.value/bigdec (attribute-for-value (java.math.BigDecimal. 23)))))
  (testing "with a BigInteger"
    (is (= :element.value/bigint (attribute-for-value (java.math.BigInteger. "23")))))
  (testing "with a byte array"
    (is (= :element.value/bytes (attribute-for-value (byte-array 1)))))
  (testing "with something unsupported"
    (is (thrown? java.lang.IllegalArgumentException (attribute-for-value (Object.))))))

(deftest test-ref-type
  (testing "with an attribute representing a map"
    (let [dbc (fresh-dbc)
          tx-result @(d/transact dbc [{:db/id (d/tempid :db.part/user)
                                       :test/map {:db/id (d/tempid :db.part/user)
                                                  :ref/empty true}}])
          entity-id (first (vals (:tempids tx-result)))
          entity (d/entity (db dbc) entity-id)]
      (is (= :ref.type/map (ref-type (db dbc) :test/map)))))

  (testing "with an attribute representing a vector"
    (let [dbc (fresh-dbc)
          tx-result @(d/transact dbc [{:db/id (d/tempid :db.part/user)
                                         :test/vector {:db/id (d/tempid :db.part/user)
                                                  :ref/empty true}}])
          entity-id (first (vals (:tempids tx-result)))
          entity (d/entity (db dbc) entity-id)]
      (is (= :ref.type/vector (ref-type (db dbc) :test/vector))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Generative Testing

(def prop-round-trip
  (prop/for-all [value marshalable-value]
                (let [dbc (fresh-dbc)
                      result (round-trip dbc value)
                      garbage (invalid-elements (db dbc))]
                  (when-not (= 0 (count garbage))
                    (print "invalid elements: ")
                    (pprint garbage))
                  (and (= [] garbage)
                       (equivalent? value result)))))

(def prop-update
  (prop/for-all [initial-value marshalable-value
                 subsequent-value marshalable-value]
                (let [dbc (fresh-dbc)
                      result (update dbc initial-value subsequent-value)
                      garbage (invalid-elements (db dbc))]
                  (when-not (= 0 (count garbage))
                    (print "invalid elements: ")
                    (pprint garbage))
                  (and (= [] garbage)
                       (equivalent? subsequent-value result)))))

(defspec quickcheck-round-trip 30 prop-round-trip)

(defspec quickcheck-update 30 prop-update)
