(ns datomizer.id-test
  (:require [datomizer.id :refer :all]
            [clojure.test :refer :all]
            [datomic.api :as d :refer [q db]]
            [datomizer.test-utility.db :refer [test-db-conn]]))

(def test-schema
  [{:db/id (d/tempid :db.part/db)
    :db/ident :test/external-id
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/identity
    :db/doc "A legacy numeric id"
    :db.install/_attribute :db.part/db}])

(defn load-id-test-schema [conn]
  (d/transact conn test-schema)
  conn)

(defn fresh-conn []
  (load-id-test-schema (test-db-conn)))

(deftest test-autoincrement
  (testing "when a previous id exists"
    (let [conn (fresh-conn)]
      @(d/transact conn [[:db/add (d/tempid :db.part/user) :test/external-id 23]])
      @(d/transact conn [[:dmzr/autoincrement (d/tempid :db.part/user) :test/external-id nil]])
      (is (= (q '[:find ?id :where [_ :test/external-id ?id]] (db conn)) #{[23] [24]}))))
  (testing "when no previous id exists"
    (let [conn (fresh-conn)]
      @(d/transact conn [[:dmzr/autoincrement (d/tempid :db.part/user) :test/external-id nil]])
      (is (= (q '[:find ?id :where [_ :test/external-id ?id]] (db conn)) #{[1]})))))
