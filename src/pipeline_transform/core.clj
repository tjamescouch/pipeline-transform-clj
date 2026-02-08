(ns pipeline-transform.core
  "Lazy-evaluated, composable data processing pipelines.

  Provides array-like operators for creating pipelines that chain
  transformation stages. Nothing executes until a terminal operator
  is called — all transformations are lazy."
  (:refer-clojure :exclude [map filter concat take some every? find reduce])
  (:require [clojure.core.async :as async]))

;; ---------------------------------------------------------------------------
;; Pipeline type
;; ---------------------------------------------------------------------------

(deftype Pipeline [realize-fn]
  clojure.lang.Seqable
  (seq [_] (clojure.core/seq (realize-fn)))

  clojure.lang.IReduceInit
  (reduce [_ f init]
    (clojure.core/reduce f init (realize-fn))))

(defmethod print-method Pipeline [^Pipeline p ^java.io.Writer w]
  (.write w (str "#<Pipeline>")))

;; ---------------------------------------------------------------------------
;; Creation
;; ---------------------------------------------------------------------------

(defn from
  "Create a pipeline from a source: vector, list, lazy seq, range, or
  core.async channel (will be drained via <!!).

  This is the only way to create a Pipeline instance."
  [source]
  (cond
    (instance? (Class/forName "[B") source)
    (Pipeline. (fn [] (clojure.core/map #(byte-array [%]) (seq source))))

    (satisfies? clojure.core.async.impl.protocols/ReadPort source)
    (Pipeline. (fn [] (let [ch source]
                        (lazy-seq
                         (let [v (async/<!! ch)]
                           (when (clojure.core/some? v)
                             (cons v (lazy-seq
                                      (let [realize (fn realize []
                                                      (lazy-seq
                                                       (let [v (async/<!! ch)]
                                                         (when (clojure.core/some? v)
                                                           (cons v (realize))))))]
                                        (realize))))))))))

    :else
    (Pipeline. (fn [] (seq source)))))

;; ---------------------------------------------------------------------------
;; Transformation operators — return a new Pipeline, append a stage
;; ---------------------------------------------------------------------------

(defn map
  "Transform each item. Returns a new lazy pipeline."
  [^Pipeline pipeline f]
  (let [parent-fn (.realize_fn pipeline)]
    (Pipeline. (fn [] (clojure.core/map f (parent-fn))))))

(defn flat-map
  "Transform each item into a collection and flatten one level."
  [^Pipeline pipeline f]
  (let [parent-fn (.realize_fn pipeline)]
    (Pipeline. (fn [] (clojure.core/mapcat f (parent-fn))))))

(defn flat
  "Flatten nested collections one level."
  [^Pipeline pipeline]
  (let [parent-fn (.realize_fn pipeline)]
    (Pipeline. (fn [] (clojure.core/mapcat seq (parent-fn))))))

(defn filter
  "Keep only items matching the predicate."
  [^Pipeline pipeline pred]
  (let [parent-fn (.realize_fn pipeline)]
    (Pipeline. (fn [] (clojure.core/filter pred (parent-fn))))))

(defn concat
  "Append values or a collection to the end of the pipeline."
  [^Pipeline pipeline other]
  (let [parent-fn (.realize_fn pipeline)]
    (Pipeline. (fn [] (clojure.core/concat (parent-fn) other)))))

(defn for-each
  "Execute a side effect on each item. Items pass through unchanged."
  [^Pipeline pipeline f]
  (let [parent-fn (.realize_fn pipeline)]
    (Pipeline. (fn [] (clojure.core/map (fn [item] (f item) item) (parent-fn))))))

(defn batch
  "Group consecutive items into vectors of `size`. Last batch may be smaller."
  [^Pipeline pipeline size]
  (let [parent-fn (.realize_fn pipeline)]
    (Pipeline. (fn [] (clojure.core/partition-all size (parent-fn))))))

(defn take
  "Limit to the first `n` items. Enables handling infinite sequences."
  [^Pipeline pipeline n]
  (let [parent-fn (.realize_fn pipeline)]
    (Pipeline. (fn [] (clojure.core/take n (parent-fn))))))

(defn skip
  "Skip the first `n` items."
  [^Pipeline pipeline n]
  (let [parent-fn (.realize_fn pipeline)]
    (Pipeline. (fn [] (clojure.core/drop n (parent-fn))))))

(defn inspect
  "Print each item to *err* with an optional label. Items pass through unchanged."
  ([^Pipeline pipeline]
   (inspect pipeline nil))
  ([^Pipeline pipeline label]
   (let [parent-fn (.realize_fn pipeline)]
     (Pipeline. (fn [] (clojure.core/map
                        (fn [item]
                          (binding [*out* *err*]
                            (if label
                              (println (str "[" label "]") (pr-str item))
                              (println (pr-str item))))
                          item)
                        (parent-fn)))))))

;; ---------------------------------------------------------------------------
;; Terminal operators — trigger execution, return a value
;; ---------------------------------------------------------------------------

(defn to-vec
  "Fully materialize the pipeline into a vector."
  [^Pipeline pipeline]
  (vec ((.realize_fn pipeline))))

(defn find
  "Return the first item matching the predicate, or nil. Early exit."
  [^Pipeline pipeline pred]
  (clojure.core/first (clojure.core/filter pred ((.realize_fn pipeline)))))

(defn some
  "Return true if any item matches the predicate. Early exit."
  [^Pipeline pipeline pred]
  (boolean (clojure.core/some pred ((.realize_fn pipeline)))))

(defn every?
  "Return true if all items match the predicate. Early exit on first false."
  [^Pipeline pipeline pred]
  (clojure.core/every? pred ((.realize_fn pipeline))))

(defn includes
  "Check if a value exists using equality. Early exit."
  [^Pipeline pipeline value]
  (some pipeline #(= % value)))

(defn join
  "Concatenate items into a string with a separator."
  [^Pipeline pipeline separator]
  (clojure.string/join separator ((.realize_fn pipeline))))

(defn reduce
  "Accumulate items into a single value.
  With 2 args: (reduce pipeline f) — uses first item as initial value.
  With 3 args: (reduce pipeline f init) — uses provided initial value."
  ([^Pipeline pipeline f]
   (clojure.core/reduce f ((.realize_fn pipeline))))
  ([^Pipeline pipeline f init]
   (clojure.core/reduce f init ((.realize_fn pipeline)))))

;; ---------------------------------------------------------------------------
;; Async terminal operators — return core.async channels
;; ---------------------------------------------------------------------------

(defn to-chan
  "Materialize the pipeline onto a core.async channel. Returns the channel."
  [^Pipeline pipeline]
  (async/to-chan! (to-vec pipeline)))

(defn collect-async
  "Materialize the pipeline asynchronously. Returns a channel that will
  receive the result vector and then close."
  [^Pipeline pipeline]
  (let [ch (async/chan 1)]
    (async/go
      (async/>! ch (to-vec pipeline))
      (async/close! ch))
    ch))
