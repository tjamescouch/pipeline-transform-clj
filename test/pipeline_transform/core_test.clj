(ns pipeline-transform.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [pipeline-transform.core :as p]
            [clojure.core.async :as async]))

;; ---------------------------------------------------------------------------
;; Creation
;; ---------------------------------------------------------------------------

(deftest from-vector
  (is (= [1 2 3] (p/to-vec (p/from [1 2 3])))))

(deftest from-list
  (is (= [1 2 3] (p/to-vec (p/from '(1 2 3))))))

(deftest from-range
  (is (= [0 1 2 3 4] (p/to-vec (p/from (range 5))))))

(deftest from-empty
  (is (= [] (p/to-vec (p/from [])))))

;; ---------------------------------------------------------------------------
;; Transformation operators
;; ---------------------------------------------------------------------------

(deftest map-test
  (is (= [2 4 6] (p/to-vec (p/map (p/from [1 2 3]) #(* % 2))))))

(deftest map-type-change
  (is (= ["1" "2" "3"] (p/to-vec (p/map (p/from [1 2 3]) str)))))

(deftest filter-test
  (is (= [2 4] (p/to-vec (p/filter (p/from [1 2 3 4 5]) even?)))))

(deftest flat-map-test
  (is (= [1 10 2 20 3 30]
         (p/to-vec (p/flat-map (p/from [1 2 3]) #(vector % (* % 10)))))))

(deftest flat-test
  (is (= [1 2 3 4 5]
         (p/to-vec (p/flat (p/from [[1 2] [3 4] [5]]))))))

(deftest concat-test
  (is (= [1 2 3 4] (p/to-vec (p/concat (p/from [1 2]) [3 4])))))

(deftest for-each-side-effect
  (let [seen (atom [])]
    (is (= [1 2 3]
           (p/to-vec (p/for-each (p/from [1 2 3]) #(swap! seen conj %)))))
    (is (= [1 2 3] @seen))))

(deftest batch-test
  (is (= ['(1 2) '(3 4) '(5)]
         (p/to-vec (p/batch (p/from [1 2 3 4 5]) 2)))))

(deftest take-test
  (is (= [1 2 3] (p/to-vec (p/take (p/from [1 2 3 4 5]) 3)))))

(deftest skip-test
  (is (= [3 4 5] (p/to-vec (p/skip (p/from [1 2 3 4 5]) 2)))))

;; ---------------------------------------------------------------------------
;; Terminal operators
;; ---------------------------------------------------------------------------

(deftest find-test
  (is (= 4 (p/find (p/from [1 2 3 4 5]) #(> % 3)))))

(deftest find-none
  (is (nil? (p/find (p/from [1 2 3]) #(> % 10)))))

(deftest some-true
  (is (true? (p/some (p/from [1 2 3]) #(= % 2)))))

(deftest some-false
  (is (false? (p/some (p/from [1 2 3]) #(= % 5)))))

(deftest every-true
  (is (true? (p/every? (p/from [2 4 6]) even?))))

(deftest every-false
  (is (false? (p/every? (p/from [2 3 6]) even?))))

(deftest includes-true
  (is (true? (p/includes (p/from [1 2 3]) 2))))

(deftest includes-false
  (is (false? (p/includes (p/from [1 2 3]) 5))))

(deftest join-test
  (is (= "1, 2, 3" (p/join (p/from [1 2 3]) ", "))))

(deftest reduce-with-init
  (is (= 10 (p/reduce (p/from [1 2 3 4]) + 0))))

(deftest reduce-without-init
  (is (= 10 (p/reduce (p/from [1 2 3 4]) +))))

;; ---------------------------------------------------------------------------
;; Chaining
;; ---------------------------------------------------------------------------

(deftest chaining-filter-map-take
  (is (= [20 40 60]
         (-> (p/from [1 2 3 4 5 6 7 8 9 10])
             (p/filter even?)
             (p/map #(* % 10))
             (p/take 3)
             p/to-vec))))

(deftest chaining-skip-take
  (is (= [10 11 12 13 14]
         (-> (p/from (range 100))
             (p/skip 10)
             (p/take 5)
             p/to-vec))))

;; ---------------------------------------------------------------------------
;; Lazy evaluation
;; ---------------------------------------------------------------------------

(deftest lazy-evaluation
  (let [call-count (atom 0)
        pipeline (p/map (p/from [1 2 3 4 5])
                        (fn [x] (swap! call-count inc) (* x 2)))]
    ;; Not yet materialized
    (is (= 0 @call-count))
    ;; Now materialize
    (is (= [2 4 6 8 10] (p/to-vec pipeline)))
    (is (= 5 @call-count))))

(deftest early-exit-find
  (let [call-count (atom 0)]
    (is (= 3 (p/find (p/map (p/from [1 2 3 4 5])
                             (fn [x] (swap! call-count inc) x))
                      #(= % 3))))
    (is (<= @call-count 3))))

(deftest early-exit-some
  (let [call-count (atom 0)]
    (is (true? (p/some (p/map (p/from [1 2 3 4 5])
                              (fn [x] (swap! call-count inc) x))
                       #(= % 2))))
    (is (<= @call-count 2))))

;; ---------------------------------------------------------------------------
;; Infinite sequences
;; ---------------------------------------------------------------------------

(deftest infinite-take
  (is (= [0 1 2 3 4]
         (-> (p/from (range))    ; infinite
             (p/take 5)
             p/to-vec))))

(deftest infinite-map-filter-take
  (is (= [0 2 4 6 8]
         (-> (p/from (range))
             (p/filter even?)
             (p/take 5)
             p/to-vec))))

;; ---------------------------------------------------------------------------
;; Array equivalence
;; ---------------------------------------------------------------------------

(deftest array-equiv-map
  (let [source [1 2 3 4 5]
        expected (mapv #(* % 3) source)]
    (is (= expected (p/to-vec (p/map (p/from source) #(* % 3)))))))

(deftest array-equiv-filter-map
  (let [source [1 2 3 4 5 6 7 8 9 10]
        expected (->> source
                      (clojure.core/filter #(zero? (mod % 3)))
                      (clojure.core/map #(str "item-" %))
                      vec)]
    (is (= expected
           (-> (p/from source)
               (p/filter #(zero? (mod % 3)))
               (p/map #(str "item-" %))
               p/to-vec)))))

;; ---------------------------------------------------------------------------
;; Edge cases
;; ---------------------------------------------------------------------------

(deftest empty-find
  (is (nil? (p/find (p/from []) (constantly true)))))

(deftest empty-some
  (is (false? (p/some (p/from []) (constantly true)))))

(deftest empty-every
  ;; Vacuous truth
  (is (true? (p/every? (p/from []) (constantly false)))))

(deftest empty-reduce
  (is (= 0 (p/reduce (p/from []) + 0))))

;; ---------------------------------------------------------------------------
;; Async
;; ---------------------------------------------------------------------------

(deftest collect-async-test
  (let [ch (p/collect-async (p/from [1 2 3]))]
    (is (= [1 2 3] (async/<!! ch)))))

;; ---------------------------------------------------------------------------
;; Pipeline can be iterated multiple times
;; ---------------------------------------------------------------------------

(deftest multiple-iterations
  (let [pipeline (p/map (p/from [1 2 3]) #(* % 2))]
    (is (= [2 4 6] (p/to-vec pipeline)))
    (is (= [2 4 6] (p/to-vec pipeline)))))
