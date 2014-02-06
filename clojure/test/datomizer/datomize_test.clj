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

(defn seq-if-byte-array [x]
  (if (instance? byte-array-class x) (seq x) x))

(defn walk-wrapping-byte-arrays
  "Return a copy of a data structure with all byte-arrays wrapped in seqs (for comparison of contents)."
  [data]
  (clojure.walk/postwalk seq-if-byte-array data))

(defn equivalent? [expected actual]
  "Compare with = (wrapping byte arrays in seqs to check them by equivalence instead of id)."
  (cond
   (or (nil? actual) (nil? expected)) (and (nil? expected) (nil? actual))
   (coll? expected) (apply = (map walk-wrapping-byte-arrays [expected actual]))
   (instance? byte-array-class expected) (and (instance? byte-array-class actual)
                                          (= (seq expected) (seq actual)))
   :else (= expected actual)))

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
  (gen/one-of [datomizable-type (gen/sized (sized-container-keyword-keys datomizable-type))]))

(def prop-round-trip
  (prop/for-all [value datomizable-value]
                (let [dbc (fresh-dbc)
                      result (round-trip dbc value)
                      garbage (invalid-elements (db dbc))]
                  (when-not (= 0 (count garbage))
                    (print "invalid elements: ")
                    (pprint garbage))
                  (and (= [] garbage)
                       (equivalent? value result)))))

(def prop-update
  (prop/for-all [initial-value datomizable-value
                 subsequent-value datomizable-value]
                (let [dbc (fresh-dbc)
                      result (update dbc initial-value subsequent-value)
                      garbage (invalid-elements (db dbc))]
                  (when-not (= 0 (count garbage))
                    (print "invalid elements: ")
                    (pprint garbage))
                  (and (= [] garbage)
                       (equivalent? subsequent-value result)))))

(defspec quickcheck-round-trip
  30
  prop-round-trip)

(defspec quickcheck-update
  30
  prop-update)
