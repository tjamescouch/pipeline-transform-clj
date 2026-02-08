# execution

how pipelines are created, built, and materialized.

## flow

1. user calls `(from source)` with a vector, list, lazy seq, range, byte array, or core.async channel
2. the factory creates a pipeline instance with the source wrapped in a realize-fn
3. user chains transformation operators (map, filter, etc.) which return a new pipeline with the stage appended
4. nothing executes during steps 2-3 (lazy evaluation)
5. user calls a terminal operator (to-vec, find, etc.) or seqs the pipeline
6. the realize-fn composes all stages: stage zero feeds stage one, which feeds stage two, and so on
7. items flow on demand through the full chain via lazy sequences
8. early-exit operators (find, some, every?, includes) stop evaluation on match

## failure modes

- errors in transformation functions propagate naturally as exceptions
- errors are deferred: a pipeline with a throwing map does not throw until materialized
- if a terminal operator exits early (e.g. find matches item 2 of 5), items 3-5 are never processed and their errors never surface
