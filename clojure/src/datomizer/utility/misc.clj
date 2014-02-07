(ns datomizer.utility.misc
  "Junk drawer of useful functions."
  (:require [datomic.api :as d]))

(defn load-datoms-from-edn-resource-file [dbc filename]
  (with-open [r (java.io.PushbackReader. (clojure.java.io/reader (clojure.java.io/resource filename)))]
    (doseq [datoms (clojure.edn/read
                             {:readers *data-readers*}
                             r)]
      (d/transact dbc [datoms]))))

(defn first-matching
  "Return the first element of a collection matching a regular expression."
  [coll regexp]
  (first (filter #(re-matches regexp (str %)) coll)))
