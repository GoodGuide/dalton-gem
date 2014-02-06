(ns datomizer.utility
  "Junk drawer of useful functions."
  (:require [datomic.api :as d]))

(defn load-datoms-from-edn-resource-file [dbc filename]
  (with-open [r (java.io.PushbackReader. (clojure.java.io/reader (clojure.java.io/resource filename)))]
    (doseq [datoms (clojure.edn/read
                             {:readers *data-readers*}
                             r)]
      (d/transact dbc [datoms]))))
