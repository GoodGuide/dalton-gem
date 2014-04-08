(ns datomizer.id
  "Autoincrement numeric id."
  (:require [datomic.api :refer [q]]
            [datomizer.utility.debug :refer [dbg]]))

(defn autoincrement [db datomic-id numeric-id-attribute value]
  (let [numeric-id (or value
                       (+ 1 (or
                             (ffirst (q '[:find (max ?id)
                                          :in $ ?numeric-id-attribute
                                          :where [_ ?numeric-id-attribute ?id]]
                                        db
                                        numeric-id-attribute))
                             0)))]
    [[:db/add datomic-id numeric-id-attribute numeric-id]]))
