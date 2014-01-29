(ns datomizer.debug
  "Debugging tools.")

(defn dbp [x]
  (println x)
  (flush))

(defmacro dbg [& body]
  `(let [x# ~@body]
     (println (str "dbg: " (quote ~@body) "=" x#))
     (flush)
     x#))

(defmacro dbgv [& body]
  `(let [x# ~@body]
     (println (str "dbgv: " (quote ~@body) "=" (if (seq? x#) (vec x#) x# ) ))
     (flush)
     x#))
