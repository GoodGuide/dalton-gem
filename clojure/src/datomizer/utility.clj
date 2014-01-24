(ns datomizer.utility
  "Junk drawer of useful functions."
  (:use [datomic.api :as d :only (db q)]))

(defn load-datoms-from-edn-resource-file [dbc filename]
  (with-open [r (java.io.PushbackReader. (clojure.java.io/reader (clojure.java.io/resource filename)))]
    (doseq [datoms (clojure.edn/read
                             {:readers *data-readers*}
                             r)]
      (d/transact dbc [datoms]))))
