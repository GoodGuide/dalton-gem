(ns datomizer.test-utility.db
  "Test database setup."
  (:require [datomic.api :as d]
            [datomizer.datomize.setup :refer [load-datomizer-functions
                                              load-datomizer-schema]]))

(defonce test-database (atom nil))

;; (def test-database-uri "datomic:dev://localhost:4334/datomizer-test")
(def test-database-uri "datomic:mem://datomizer-test")

(defn delete-test-database []
  (when @test-database
    (d/delete-database test-database-uri)
    (reset! test-database nil)))

(defn test-db-conn
  "Create a fresh database for the test"
  []
  (delete-test-database)
  (d/create-database test-database-uri)
  (let [conn (d/connect test-database-uri)]
    (load-datomizer-schema conn)
    (load-datomizer-functions conn)
    (reset! test-database conn)
    conn))
