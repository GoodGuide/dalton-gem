(ns datomizer.edenize.decode
  "Decode edenized data."
  (:require [datomizer.utility.debug :refer [dbg]]
            [datomizer.utility.misc :refer [ref-type]]))

(defn unedenize-attribute [entity key]
  (let [value (get entity (str key))]
    (if (= :dmzr.ref.type/edn (ref-type (.db entity) key))
      (clojure.edn/read-string value)
      value)))

(defn unedenize
  "Decode an edenized entity."
  [entity]
  (->> (conj (keys entity) :db/id)
       (mapcat (fn [key] [key (unedenize-attribute entity key)]))
       (apply hash-map )))
