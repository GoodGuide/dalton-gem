(ns datomizer.utility.byte-array
  "Functions for handling byte-arrays more gracefully.")

(defn seq-if-byte-array [x]
  (if (instance? (Class/forName "[B") x) (seq x) x))

(defn walk-wrapping-byte-arrays
  "Return a copy of a data structure with all byte-arrays wrapped in seqs (for comparison of contents)."
  [data]
  (clojure.walk/postwalk seq-if-byte-array data))

(defn equivalent? [expected actual]
  "Compare with = (wrapping byte arrays in seqs to check them by equivalence instead of id)."
  (cond
   (or (nil? actual) (nil? expected)) (and (nil? expected) (nil? actual))
   (coll? expected) (apply = (map walk-wrapping-byte-arrays [expected actual]))
   (instance? (Class/forName "[B") expected) (and (instance? (Class/forName "[B") actual)
                                                  (= (seq expected) (seq actual)))
   :else (= expected actual)))
