# constraints

## language

- clojure 1.12+
- lazy sequences for core pipeline evaluation
- core.async for channel-based sources and async terminal operators

## tooling

- deps.edn with clojure cli for dependency management and running
- cognitect test-runner for testing
- cljfmt for formatting

## style

- single source file (src/pipeline_transform/core.clj)
- single namespace with deftype for Pipeline
- no external css, no ui
- functional: no mutation of user data, stages are append-only internally
- threading-macro friendly API: transformation functions take pipeline as first arg

## distribution

- published as clojars package
- standard deps.edn lib

## testing

- functional tests verify operator behavior matches core sequence equivalents
- laziness tests verify deferred evaluation
- early-exit tests verify short-circuit behavior
