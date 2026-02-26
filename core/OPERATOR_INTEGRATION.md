# Variance-Based Operator Integration Guide

This guide explains how the variance-based operator interfaces (`CovariantOperator`, `ContravariantOperator`, `InvariantOperator`) are integrated across the KALM codebase.

## Overview

The operator interfaces leverage Kotlin's declaration-site variance to provide type-safe composition patterns for solution-based algorithms.

### The Three Interfaces

1. **`CovariantOperator<out T>`** - Producers only
2. **`ContravariantOperator<in T>`** - Consumers only  
3. **`InvariantOperator<T>`** - Both producers and consumers

## Integration Points

### 1. Adapters (`cl.ravenhill.kalm.adapters`)

Bridge between functional programming patterns and the operator hierarchy:

- **`SolutionProducerAdapter<T>`** - Wraps `() -> T` as `CovariantOperator<T>`
- **`SolutionConsumerAdapter<T>`** - Wraps `(T) -> Unit` as `ContravariantOperator<T>`
- **`SolutionTransformerAdapter<T>`** - Wraps `(T) -> T` as `InvariantOperator<T>`

**Example:**
```kotlin
val randomProducer = SolutionProducerAdapter {
    Solution.of(Random.nextDouble())
}

val logger = SolutionConsumerAdapter<Solution<Double>> { solution ->
    println("Fitness: ${evaluate(solution)}")
}
```

### 2. Producer Operators (`cl.ravenhill.kalm.operators.producers`)

Concrete implementations of `CovariantOperator`:

- **`RandomSolutionProducer<T>`** - Generates random solutions within bounds
- **`ConstantSolutionProducer<T>`** - Returns a constant solution

**Variance Benefit:**
```kotlin
val doubleProducer: CovariantOperator<Solution<Double>> = RandomSolutionProducer(...)
val anyProducer: CovariantOperator<Solution<*>> = doubleProducer  // ✓ Covariance!
```

### 3. Consumer Operators (`cl.ravenhill.kalm.operators.consumers`)

Concrete implementations of `ContravariantOperator`:

- **`SolutionValidator<T>`** - Validates solutions against predicates
- **`StatisticsCollector<T>`** - Aggregates statistics from consumed solutions
- **`CachingEvaluator<T>`** - Memoizing evaluator (extends `SolutionEvaluator`)

**Variance Benefit:**
```kotlin
val numberValidator: ContravariantOperator<Solution<Number>> = SolutionValidator(...)
val doubleValidator: ContravariantOperator<Solution<Double>> = numberValidator  // ✓ Contravariance!
```

### 4. Composition Utilities (`cl.ravenhill.kalm.operators.composition`)

Combine operators into powerful abstractions:

#### **`OperatorPipeline<T>`**
Chains a producer to a consumer:
```kotlin
val pipeline = producer then consumer
pipeline.execute(iterations = 100)
```

#### **`BroadcastOperator<T>`**
Distributes consumed solutions to multiple consumers:
```kotlin
val broadcast = validator and logger and collector
broadcast.consume(solution)  // All three operators receive it
```

#### **`OperatorChain<T>`**
Sequences multiple transformations:
```kotlin
val chain = mutator1 andThen mutator2 andThen mutator3
chain.consume(initial)
val transformed = chain.produce()
```

## Relationship to Existing Knob Interfaces

The KALM variance interfaces complement (not replace) existing knob interfaces:

| Knob Interface | Variance Pattern | Notes |
|----------------|------------------|-------|
| `Objective<T>` | Contravariant (consumer) | Evaluates solutions |
| `Constraint<T>` | Contravariant (consumer) | Validates solutions |
| `Perturber<T>` | Invariant (transformer) | Mutates solutions |
| `Sampler<T>` | Covariant (producer)* | Produces from cohort |
| `Initializer<T>` | Covariant (producer)* | Creates initial cohort |

*These don't perfectly fit the pattern since they take parameters, but conceptually align.

## Practical Usage Examples

### Example 1: Evolutionary Algorithm with Validation

```kotlin
// Setup operators
val initializer = RandomSolutionProducer<Solution<Double>>(
    dimension = 10,
    lowerBound = -5.0,
    upperBound = 5.0
)

val validator = SolutionValidator<Solution<Double>>(
    predicate = { it.all { value -> value in -5.0..5.0 } }
)

val stats = StatisticsCollector<Solution<Double>>()

// Compose into pipeline
val pipeline = initializer then (validator and stats)

// Run
pipeline.execute(iterations = 1000)
println("Valid solutions: ${stats.consumedCount}")
```

### Example 2: Transformation Chain

```kotlin
val normalizer = SolutionTransformerAdapter<Solution<Double>> { sol ->
    val norm = sqrt(sol.sumOf { it * it })
    Solution.of(*sol.map { it / norm }.toTypedArray())
}

val clamper = SolutionTransformerAdapter<Solution<Double>> { sol ->
    Solution.of(*sol.map { it.coerceIn(-1.0, 1.0) }.toTypedArray())
}

val chain = normalizer andThen clamper
chain.consume(rawSolution)
val processed = chain.produce()
```

### Example 3: Broadcast Monitoring

```kotlin
val evaluator = CachingEvaluator<Solution<Double>> { solution ->
    // Expensive fitness calculation
    solution.sumOf { it * it }
}

val logger = SolutionConsumerAdapter<Solution<Double>> { sol ->
    println("Evaluating: ${sol.toList()}")
}

val monitor = logger and evaluator

// Use in algorithm
population.forEach { solution ->
    monitor.consume(solution)
}

println("Cache hits: ${evaluator.cacheHits}")
```

## Type Safety Benefits

### Covariance Enables Polymorphic Producers

```kotlin
fun collectSolutions(producer: CovariantOperator<Solution<*>>) {
    val solutions = List(100) { producer.produce() }
    // Works with any specific solution type
}

val doubleProducer: CovariantOperator<Solution<Double>> = ...
collectSolutions(doubleProducer)  // ✓ Safe
```

### Contravariance Enables Flexible Consumers

```kotlin
fun processSolutions(
    solutions: List<Solution<Int>>,
    consumer: ContravariantOperator<Solution<Int>>
) {
    solutions.forEach { consumer.consume(it) }
}

val numberConsumer: ContravariantOperator<Solution<Number>> = ...
processSolutions(intSolutions, numberConsumer)  // ✓ Safe
```

### Invariance Enforces Exact Type Matching

```kotlin
fun chain(
    first: InvariantOperator<Solution<Double>>,
    second: InvariantOperator<Solution<Double>>
): InvariantOperator<Solution<Double>> = first andThen second

val doubleOp: InvariantOperator<Solution<Double>> = ...
val numberOp: InvariantOperator<Solution<Number>> = ...

chain(doubleOp, numberOp)  // ✗ Compile error - must be exact type match
```

## Best Practices

1. **Choose the right variance:**
   - Producers only? Use `CovariantOperator`
   - Consumers only? Use `ContravariantOperator`
   - Need both? Use `InvariantOperator`

2. **Leverage composition:**
   - Use `then` for producer → consumer
   - Use `and` for multiple consumers
   - Use `andThen` for transformation chains

3. **Prefer adapters for simple cases:**
   - Wrap lambdas instead of creating new classes
   - Use adapters for one-off operators

4. **Document variance relationships:**
   - Explain why a specific variance was chosen
   - Show examples of safe type substitution

## Testing

Each operator type has corresponding test files demonstrating:
- Variance behavior
- Composition patterns
- Integration with existing knob types

See:
- `SolutionProducerAdapterTest.kt`
- `SolutionConsumerAdapterTest.kt`
- `OperatorCompositionTest.kt`

## Future Extensions

Potential enhancements:
1. Async operators (e.g., `suspend fun produce()`)
2. Reactive operators (Flow-based)
3. Parallel composition utilities
4. Operator metrics and profiling
5. Generic operator decorators (retry, circuit breaker, etc.)
