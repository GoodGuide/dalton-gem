(ns datomizer.datomize-test
  "Tests for datomize."
  (:use clojure.test
        datomizer.datomize
        datomizer.debug
        [datomic.api :as d :only (db q)]))

(def test-schema
  [{:db/id (d/tempid :db.part/db)
    :db/ident :test/map
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :db/doc "A reference attribute for testing marshalling"
    :db/isComponent true
    :ref/type :ref.type/map
    :db.install/_attribute :db.part/db}
   {:db/id (d/tempid :db.part/db)
    :db/ident :test/vector
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :db/doc "A reference attribute for testing marshalling"
    :db/isComponent true
    :ref/type :ref.type/vector
    :db.install/_attribute :db.part/db}])

(defonce test-database (atom nil))

(def test-database-uri "datomic:dev://localhost:4334/datomizer-test")
;; (def test-database-uri "datomic:mem://datomizer-test")

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
      (d/transact dbc test-schema)
      (reset! test-database dbc)
      dbc)))

(defn delete-test-database-fixture
  [test-fn]
  (try
    (test-fn)
    (finally
      (delete-test-database))))

(use-fixtures :once delete-test-database-fixture)

(defn round-trip-test
  "Test that a value is stored and retrieved from Datomic."
  [value]
  (let [dbc (fresh-dbc)
        collection-datoms (datomizer.datomize/datomize value)
        attribute (if (map? value) :test/map :test/vector)
        entity-datoms [{:db/id (d/tempid :db.part/user)
                        :db/doc "Test entity."
                        attribute collection-datoms}]
        tx-result @(d/transact dbc entity-datoms)]
    (let [query-result (q '[:find ?e :where [?e :db/doc "Test entity."]] (db dbc))
          entity (d/entity (db dbc) (ffirst query-result))
          data (undatomize entity)]
      (is (= 1 (count query-result)))
      (is (= value (attribute data))))))

(deftest test-datomize
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
    (round-trip-test [1 2 [11 22 33] 3])))

(deftest test-element-value-attribute
  (testing "with a String"
    (is (= :element.value/string (element-value-attribute "I'm a string!"))))
  (testing "with a vector"
    (is (= :element.value/vector (element-value-attribute [:a :vector]))))
  (testing "with an java ArrayList"
    (is (= :element.value/vector (element-value-attribute (java.util.ArrayList. [:an "arraylist"])))))
  (testing "with a clojure map"
    (is (= :element.value/map (element-value-attribute {:a "map"}))))
  (testing "with a java Map"
    (is (= :element.value/map (element-value-attribute (java.util.HashMap. {:a "hashmap"})))))
  (testing "with a Long"
    (is (= :element.value/long (element-value-attribute 23))))
  (testing "with a Float"
    (is (= :element.value/float (element-value-attribute (float 23.1)))))
  (testing "with a Double"
    (is (= :element.value/double (element-value-attribute 23.1))))
  (testing "with a Boolean"
    (is (= :element.value/boolean (element-value-attribute true))))
  (testing "with a Date"
    (is (= :element.value/instant (element-value-attribute (java.util.Date.)))))
  (testing "with a keyword"
    (is (= :element.value/keyword (element-value-attribute :keyword))))
  (testing "with a BigDecimal"
    (is (= :element.value/bigdec (element-value-attribute (java.math.BigDecimal. 23)))))
  (testing "with a BigInteger"
    (is (= :element.value/bigint (element-value-attribute (java.math.BigInteger. "23")))))
  (testing "with a byte array"
    (is (= :element.value/bytes (element-value-attribute (byte-array 1)))))
  (testing "with something unsupported"
    (is (thrown? java.lang.IllegalArgumentException (element-value-attribute (Object.))))))

(deftest test-element-value-attribute-db-fn
  (let [dbc (fresh-dbc)]
    (install-element-value-attribute-db-fn dbc)
    (is (= :element.value/string (d/invoke (db dbc) :element-value-attribute "foo")))
    (is (= :element.value/string (d/q '[:find ?e ?doc ?type :where [?e :db/doc ?doc]
                                        [(datomizer.datomize/element-value-attribute ?doc) ?type]] (db dbc))))
    ))


(deftest test-ref-type
  (testing "with an attribute representing a map"
    (let [dbc (fresh-dbc)
          tx-result @(d/transact dbc [{:db/id (d/tempid :db.part/user)
                                         :test/map :ref.map/empty}])
          entity-id (first (vals (:tempids tx-result)))
          entity (d/entity (db dbc) entity-id)]
      (is (= :ref.type/map (ref-type (db dbc) :test/map)))))

  (testing "with an attribute representing a vector"
    (let [dbc (fresh-dbc)
          tx-result @(d/transact dbc [{:db/id (d/tempid :db.part/user)
                                         :test/vector :ref.vector/empty}])
          entity-id (first (vals (:tempids tx-result)))
          entity (d/entity (db dbc) entity-id)]
      (is (= :ref.type/vector (ref-type (db dbc) :test/vector))))))


(deftest test-decode)
(deftest test-decode-elements)
(deftest test-undatomize)
