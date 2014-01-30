(ns datomizer.datomize-test
  "Tests for datomize."
  (:require [simple-check.core :as sc]
            [simple-check.generators :as gen]
            [simple-check.properties :as prop]
            [simple-check.clojure-test :as ct :refer (defspec)]
            [clojure.pprint :refer (pprint)])
  (:use clojure.test
        datomizer.datomize
        datomizer.debug
        [datomic.api :as d :only (db q)])
  (:import (java.util Date)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Fixtures

(def test-schema
  [{:db/id (d/tempid :db.part/db)
    :db/ident :test/map
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :db/doc "A reference attribute for testing map marshalling"
    :db/isComponent true
    :ref/type :ref.type/map
    :db.install/_attribute :db.part/db}
   {:db/id (d/tempid :db.part/db)
    :db/ident :test/vector
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :db/doc "A reference attribute for testing vector marshalling"
    :db/isComponent true
    :ref/type :ref.type/vector
    :db.install/_attribute :db.part/db}
   {:db/id (d/tempid :db.part/db)
    :db/ident :test/value
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
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

(defn delete-test-database-fixture
  [test-fn]
  (try
    (test-fn)
    (finally
      (delete-test-database))))

(use-fixtures :once delete-test-database-fixture)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Round-Trip Testing

(defn attribute-for-value
  "Determine the correct reference type for a value."
  [value]
  (cond (map? value) :test/map
        (vector? value) :test/vector
        :else :test/value))

(defn store-test-entity [dbc value]
  (let [entity {:db/id (d/tempid :db.part/user)
                :db/doc "Test entity."
                (attribute-for-value value) value}
        entity-datoms (datomize (db dbc) entity)]
    @(d/transact dbc entity-datoms)))

(defn round-trip
  "Store, then retrieve a value to/from Datomic."
  [value]
  (let [dbc (fresh-dbc)]
    (store-test-entity dbc value)
    (let [query-result (q '[:find ?e :where [?e :db/doc "Test entity."]] (db dbc))
          entity (d/entity (db dbc) (ffirst query-result))
          data (undatomize entity)]
      ((attribute-for-value value) data))))

(defn round-trip-test
  "Test that a value is stored and retrieved from Datomic."
  [value]
  (is (= value (round-trip value))))

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
    (round-trip-test [1 2 [11 22 33] 3]))
  (testing "a value"
    (round-trip-test :a)))

#_(deftest test-update
  (testing "map update"
    (binding [*debug* true]
      (let [original-data {:same "stays the same", :old "is retracted", :different "gets changed"}
            update-data {:same "stays the same", :new "is added", :different "see, now different!"}
            dbc (fresh-dbc)
            tempid  (d/tempid :db.part/user -1)
            add-tx-result @(d/transact dbc (datomize (db dbc) {:db/id tempid :test/map original-data}))
            entity-id (d/resolve-tempid (db dbc) (:tempids add-tx-result) tempid)]
        (d/transact dbc (datomize (db dbc) {:db/id entity-id :test/map update-data}))
        (is (= update-data (:test/map (undatomize (d/entity (db dbc) entity-id)))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Unit Testing

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


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Round-Trip Generative Testing


(def gen-long
  (gen/fmap long gen/nat))

(def ^:private gen-bigint* (gen/such-that identity
                                          (gen/fmap #(when (pos? (count %))
                                                       (BigInteger. ^bytes %))
                                                    gen/bytes)))

(def gen-bigint (gen/fmap bigint gen-bigint*))

(def gen-bigdec (gen/fmap (fn [[unscaled-val scale]]
                            (BigDecimal. ^BigInteger unscaled-val ^int scale))
                          (gen/tuple gen-bigint* gen/int)))

(def gen-double
  (gen/such-that
   identity
   (gen/fmap
    (fn [[^long s1 s2 e]]
      (let [neg? (neg? s1)
            s1 (str (Math/abs s1))
            ; this creates odd strings '1.+e5', but JDK and JS parse OK
            numstr (str (if neg? "-" "")(first s1) "." (subs s1 1)
                        (when (not (zero? s2)) s2)
                        "e" e)
            num (Double/parseDouble numstr) ]
        ; TODO use Number.isNaN once we're not using phantomjs for testing :-X
        (when-not (or (Double/isNaN num)
                      (Double/isInfinite num))
          num)))
    (gen/tuple
     ; significand, broken into 2 portions, sign on the left
     (gen/choose -179769313 179769313) (gen/choose 0 48623157)
     ; exponent range
     (gen/choose java.lang.Double/MIN_EXPONENT java.lang.Double/MAX_EXPONENT)))))

(def gen-float
  (gen/such-that
   identity
   (gen/fmap
    (fn [[^long s e]]
      (let [neg? (neg? s)
            s (str (Math/abs s))
            numstr (str (if neg? "-" "") (first s) "." (subs s 1) "e" e)
            num (Float/parseFloat numstr) ]
        ; TODO use Number.isNaN once we're not using phantomjs for testing :-X
        (when-not (or (Float/isNaN num)
                      (Float/isInfinite num))
          num)))
    (gen/tuple
     ; significand
     (gen/choose -2097152  2097151)
     ; exponent range
     (gen/choose java.lang.Float/MIN_EXPONENT java.lang.Float/MAX_EXPONENT)))))

(def gen-date (gen/fmap #(java.util.Date. %) gen/nat))

(def datomizable-type
  (gen/one-of [gen/string-ascii gen-long gen-float gen-double gen/boolean gen-date gen/keyword gen-bigdec gen-bigint* gen/bytes]))

(defn container-type-keyword-keys
  [inner-type]
  (gen/one-of [(gen/vector inner-type)
               (gen/map gen/keyword inner-type)]))

(defn sized-container-keyword-keys
  [inner-type]
  (fn [size]
    (if (zero? size)
      inner-type
      (gen/one-of [inner-type
               (container-type-keyword-keys (gen/resize (quot size 2) (gen/sized (sized-container-keyword-keys inner-type))))]))))

(def datomizable-value
  (gen/sized (sized-container-keyword-keys datomizable-type)))

(def prop-round-trip
  (prop/for-all [value datomizable-value]
                (= value (round-trip value))))

(defspec quickcheck-round-trip
  30
  prop-round-trip)
