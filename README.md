# pipeline-transform-clj

**Lazy, composable data processing pipelines for Clojure.**

Clojure port of the [pipeline-transform](https://github.com/ScottKaye/pipeline-transform) library. Build processing chains that are lazy (nothing happens until you iterate), composable (chain operations), and compatible with Clojure's seq abstraction.

## Example

```clojure
(require '[pipeline-transform.core :as p])

(-> (p/pipeline [1 2 3 4 5])
    (p/map #(* % 2))
    (p/filter #(zero? (mod % 4)))
    (p/realize))
; => (4 8)
```

## Features

- **Lazy evaluation** — Operations are chained but not executed until you call `realize` or iterate
- **Composable** — Chain `map`, `filter`, `flat-map`, and custom transforms with thread-first
- **Seq-compatible** — Works seamlessly with Clojure's seq abstraction
- **Reducible** — Implements `IReduceInit` for efficient reduction
- **Zero-copy** — Lazy chains prevent unnecessary intermediate allocations

## Creating Pipelines

### From sequences

```clojure
(p/pipeline [1 2 3])
(p/pipeline (range 10))
(p/pipeline "hello")
```

### From async channels (core.async)

```clojure
(require '[clojure.core.async :as async])

(let [ch (async/chan)]
  (async/onto-chan! ch [1 2 3])
  (p/pipeline ch))
```

## Operators

### Transformation

- `map(f)` — Apply a function to each item
- `flat-map(f)` — Apply a function that returns a sequence, flatten results
- `scan(init, f)` — Stateful transformation accumulating state

### Filtering

- `filter(predicate)` — Keep items where predicate is true
- `filter-map(f)` — Apply function and keep non-nil values
- `take(n)` — Take first n items
- `skip(n)` — Skip first n items
- `some(predicate)` — Return first item matching predicate

### Grouping

- `chunks(size)` — Group into chunks of size
- `window(size)` — Sliding window of size
- `partition(n)` — Partition stream into groups of n

### Terminal Operators

- `realize` — Materialize the pipeline into a sequence
- `collect` — Collect into a vector (realizes immediately)
- `reduce(f, init)` — Reduce stream to a single value
- `for-each(f)` — Execute a side effect for each item
- `count` — Count total items
- `first` — Get the first item
- `every?(predicate)` — Check if all items match

## Example Usage

### Basic transformation

```clojure
(-> (p/pipeline (range 10))
    (p/map #(* % 2))
    (p/filter even?)
    (p/take 3)
    (p/realize))
; => (0 2 4)
```

### Chaining operations

```clojure
(-> (p/pipeline [{:id 1 :name "Alice"}
                 {:id 2 :name "Bob"}])
    (p/map :name)
    (p/filter #(> (count %) 3))
    (p/collect))
; => ["Alice"]
```

### Side effects

```clojure
(-> (p/pipeline (range 5))
    (p/map inc)
    (p/for-each println))
; Prints:
; 1
; 2
; 3
; 4
; 5
```

### Reducing to a single value

```clojure
(-> (p/pipeline (range 10))
    (p/reduce + 0))
; => 45
```

## Performance

- **Lazy composition** — Operations chain without intermediate allocations
- **Seq-based** — Compatible with Clojure's native streaming
- **Reducible** — Efficient when using `reduce` as the terminal operation

Typical use case: processing large sequences without materializing them fully.

## Comparison

| Feature | seq | transducers | pipeline |
|---------|-----|-------------|----------|
| Lazy | ✓ | ✓ | ✓ |
| Composable | ✓ | ✓ | ✓ |
| Ergonomic | ✓ | ~ | ✓ |
| Stateful | ✗ | ✓ | ✓ |
| Reusable | ✗ | ✓ | ~ |

**Pipelines shine when:** You want lazy, readable, chainable operations without learning transducer concepts.

## Building

```bash
clojure -M:test          # Run tests
clojure -M:dev           # Dev mode
clojure -M:doc           # Generate docs
```

## Design Notes

- Implements `clojure.lang.Seqable` for seq compatibility
- Implements `clojure.lang.IReduceInit` for efficient reduction
- Lazy function composition via thunks
- No macro-based syntax — just functions and thread-first

## Relative to Rust Port

- Same lazy evaluation model
- Same composable API
- Clojure-specific: seq and transducer compatibility
- Clojure-specific: async channel integration
- Both: purely lazy (no intermediate allocations)

## License

MIT

## See Also

- [Original pipeline-transform](https://github.com/ScottKaye/pipeline-transform)
- [Rust port](https://github.com/tjamescouch/pipeline-transform-rs)
- [Clojure transducers](https://clojure.org/reference/transducers)
- [core.async](https://clojure.org/reference/core_async)
