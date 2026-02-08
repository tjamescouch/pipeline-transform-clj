# pipeline

a type that wraps a source and accumulates transformation stages as lazy sequence operations. stages chain together via function composition. nothing executes until the pipeline is materialized.

## state

- stages: ordered list starting with a source collection/seq, followed by transformation functions
- each transformation wraps the previous realize-fn in a new lazy sequence operation

## capabilities

### creation

- creates a pipeline from vectors, lists, lazy seqs, ranges, byte arrays, or core.async channels
- channels are drained lazily via blocking reads

### transformation operators

these return a new pipeline for chaining. each appends a stage.

- map: transforms each item
- flat-map: transforms and flattens one level
- flat: flattens nested collections one level
- filter: keeps items matching a predicate
- concat: appends values or collections to the stream
- for-each: executes a side effect on each item, yields unchanged
- batch: groups consecutive items into vectors of a given size
- take: limits to the first N items, enables handling infinite sequences
- skip: skips the first N items
- inspect: prints each item to stderr with optional labels

### terminal operators

these trigger execution and return a value.

- to-vec: fully materializes to a vector
- find: returns first matching item or nil
- some: returns true if any item matches, early exit
- every?: returns true if all items match, early exit on first false
- includes: checks if a value exists using equality, early exit
- join: concatenates items into a string with a separator
- reduce: accumulates items into a single value

### async support

- to-chan: materializes pipeline onto a core.async channel
- collect-async: returns a channel that will receive the result vector

## interfaces

exposes:
- `(from source)` - factory function, the only way to create a Pipeline instance
- transformation functions that take and return a pipeline for threading
- terminal functions that consume the pipeline and return a value
- Seqable and IReduceInit protocol implementations

depends on:
- `clojure.core.async` (for channel-based sources and async terminals)

## invariants

- instances are created only via the `from` factory function
- no execution occurs until a terminal operator is called or the pipeline is seq'd
- all transformations produce lazy sequences (single-pass on-demand evaluation)
- the pipeline can be materialized multiple times; each materialization creates fresh lazy seq state
