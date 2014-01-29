(ns datomizer.debug
  "Debugging tools."
  (:require [clojure.pprint :refer [pprint]]))

(def ^:dynamic *debug* false)

(defn dbp [x]
  (println x)
  (flush))

(defmacro dbg [& body]
  `(let [x# ~@body]
     (when *debug*
       (print (str "dbg: " (quote ~@body) " = "))
       (pprint x#)
       (print "\n\n")
       (flush))
     x#))

(defmacro dbgv [& body]
  `(let [x# ~@body]
     (when *debug*
       (print (str "dbgv: " (quote ~@body) " = "))
       (pprint (if (seq? x#) (vec x#) x# ))
       (print "\n\n")
       (flush))
     x#))
