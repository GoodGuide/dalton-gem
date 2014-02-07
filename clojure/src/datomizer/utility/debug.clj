(ns datomizer.utility.debug
  "Debugging tools."
  (:require [clojure.pprint :refer [pprint]]))

(def ^:dynamic *debug* true)

(defn dbgp [x]
  (println x)
  (flush))

(defmacro dbg [& body]
  `(let [x# ~@body]
     (when *debug*
       (with-bindings {#'clojure.pprint/*print-miser-width* 120
                       #'clojure.pprint/*print-right-margin* 160}
         (print (str "dbg: " (quote ~@body) " = "))
         (pprint x#)
         (print "\n")
         (flush)))
     x#))

(defmacro dbgv [& body]
  `(let [x# ~@body]
     (when *debug*
       (print (str "dbgv: " (quote ~@body) " = "))
       (pprint (if (seq? x#) (vec x#) x# ))
       (print "\n")
       (flush))
     x#))
