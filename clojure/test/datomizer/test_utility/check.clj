(ns datomizer.test-utility.check
  "Tests for datomize."
  (:require [clojure.pprint :refer [pprint]]
            [clojure.test :refer :all]
            [datomic.api :as d :refer [db]]
            [datomizer.datomize.decode :refer :all]
            [datomizer.datomize.encode :refer :all]
            [datomizer.datomize.setup :refer :all]
            [datomizer.datomize.validation :refer :all]
            [datomizer.utility.byte-array :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]))

(def gen-long
  (gen/fmap long gen/nat))

(def ^:private gen-bigint* (gen/such-that identity
                                          (gen/fmap #(when (pos? (count %))
                                                       (BigInteger. ^bytes %))
                                                    gen/bytes)))

(def gen-bigint (gen/fmap bigint gen-bigint*))

(def gen-bigdec (gen/fmap (fn [[unscaled-val scale]]
                            (BigDecimal. ^BigInteger unscaled-val ^int scale))
                          (gen/tuple gen-bigint* gen/int)))

;; Derived from https://gist.github.com/cemerick/7599452
(def gen-double
  (gen/such-that
   identity
   (gen/fmap
    (fn [[^long s1 s2 e]]
      (let [neg? (neg? s1)
            s1 (str (Math/abs s1))
            ; this creates odd strings '1.+e5', but JDK and JS parse OK
            numstr (str (if neg? "-" "")(first s1) "." (subs s1 1)
                        (when (not (zero? s2)) s2)
                        "e" e)
            num (Double/parseDouble numstr) ]
        (when-not (or (Double/isNaN num)
                      (Double/isInfinite num))
          num)))
    (gen/tuple
     ; significand, broken into 2 portions, sign on the left
     (gen/choose -179769313 179769313) (gen/choose 0 48623157)
     ; exponent range
     (gen/choose java.lang.Double/MIN_EXPONENT java.lang.Double/MAX_EXPONENT)))))

(def gen-float
  (gen/such-that
   identity
   (gen/fmap
    (fn [[^long s e]]
      (let [neg? (neg? s)
            s (str (Math/abs s))
            numstr (str (if neg? "-" "") (first s) "." (subs s 1) "e" e)
            num (Float/parseFloat numstr) ]
        ; TODO use Number.isNaN once we're not using phantomjs for testing :-X
        (when-not (or (Float/isNaN num)
                      (Float/isInfinite num))
          num)))
    (gen/tuple
     ; significand
     (gen/choose -2097152  2097151)
     ; exponent range
     (gen/choose java.lang.Float/MIN_EXPONENT java.lang.Float/MAX_EXPONENT)))))

(def gen-date (gen/fmap #(java.util.Date. %) gen/nat))

(def datomizable-type
  (gen/one-of [gen/string-ascii gen-long gen-float gen-double gen/boolean gen-date gen/keyword gen-bigdec gen-bigint* gen/bytes]))

(def edenizable-type
  ;; byte-arrays are not supported.
  ;; floats come back as doubles.
  (gen/one-of [gen/string-ascii gen-long gen-double gen/boolean gen-date gen/keyword gen-bigdec gen-bigint*]))

(defn container-type-keyword-keys
  [inner-type]
  (gen/one-of [(gen/vector inner-type)
               (gen/map gen/keyword inner-type)]))

(defn sized-container-keyword-keys
  [inner-type]
  (fn [size]
    (if (zero? size)
      inner-type
      (gen/one-of [inner-type
               (container-type-keyword-keys (gen/resize (quot size 2) (gen/sized (sized-container-keyword-keys inner-type))))]))))

(def datomizable-value
  (gen/one-of [datomizable-type (gen/sized (sized-container-keyword-keys datomizable-type))]))

(def edenizable-value
  (gen/one-of [edenizable-type (gen/sized (sized-container-keyword-keys edenizable-type))]))
