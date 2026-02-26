# KALM Operators Package

Variance-based operator interfaces and implementations for type-safe solution manipulation.

## Package Structure

```
cl.ravenhill.kalm.operators/
├── Operator.kt                        # Core variance interfaces
├── adapters/                          # Functional → Operator adapters
│   ├── SolutionProducerAdapter.kt    # () -> T → CovariantOperator<T>
│   ├── SolutionConsumerAdapter.kt    # (T) -> Unit → ContravariantOperator<T>
│   └── SolutionTransformerAdapter.kt # (T) -> T → InvariantOperator<T>
├── producers/                         # CovariantOperator implementations
│   ├── RandomSolutionProducer.kt     # Generate random solutions
│   └── ConstantSolutionProducer.kt   # Return constant solution
├── consumers/                         # ContravariantOperator implementations
│   ├── SolutionValidator.kt          # Validate solutions
│   ├── StatisticsCollector.kt        # Collect solution statistics
│   └── CachingEvaluator.kt           # Memoizing evaluator
└── composition/                       # Operator composition utilities
    ├── OperatorPipeline.kt           # producer then consumer
    ├── BroadcastOperator.kt          # consumer1 and consumer2
    └── OperatorChain.kt              # operator1 andThen operator2
```

## Quick Start

### 1. Using Adapters

```kotlin
// Wrap lambdas as operators
val producer = SolutionProducerAdapter { 
    Solution.of(Random.nextDouble()) 
}

val logger = SolutionConsumerAdapter<Solution<Double>> { solution ->
    println("Solution: ${solution.toList()}")
}

// Compose them
val pipeline = producer then logger
pipeline.execute(100)
```

### 2. Using Concrete Operators

```kotlin
// Create a random solution generator
val generator = RandomSolutionProducer<Double>(
    dimension = 10,
    lowerBound = -5.0,
    upperBound = 5.0
)

// Create a validator
val validator = SolutionValidator<Solution<Double>>(
    predicate = { it.all { value -> value.isFinite() } }
)

// Create a statistics collector
val stats = StatisticsCollector<Solution<Double>>()

// Broadcast to multiple consumers
val broadcast = validator and stats
(generator then broadcast).execute(1000)

println("Valid: ${validator.isValid()}, Count: ${stats.consumedCount}")
```

### 3. Transformation Chains

```kotlin
val normalize = SolutionTransformerAdapter<Solution<Double>> { solution ->
    val norm = sqrt(solution.sumOf { it * it })
    Solution.of(*solution.map { it / norm }.toTypedArray())
}

val clamp = SolutionTransformerAdapter<Solution<Double>> { solution ->
    Solution.of(*solution.map { it.coerceIn(-1.0, 1.0) }.toTypedArray())
}

val pipeline = normalize andThen clamp
pipeline.consume(rawSolution)
val processed = pipeline.produce()
```

## Variance Guide

### Covariance (`out T`) - Producers

**Interface:** `CovariantOperator<out T>`  
**Pattern:** Only produces values of type `T`

```kotlin
val specific: CovariantOperator<Solution<Dog>> = ...
val general: CovariantOperator<Solution<Animal>> = specific  // ✓ Safe upcast
```

**Use when:** Generating, creating, or providing solutions

### Contravariance (`in T`) - Consumers

**Interface:** `ContravariantOperator<in T>`  
**Pattern:** Only consumes values of type `T`

```kotlin
val general: ContravariantOperator<Solution<Animal>> = ...
val specific: ContravariantOperator<Solution<Dog>> = general  // ✓ Safe downcast
```

**Use when:** Evaluating, validating, logging, or processing solutions

### Invariance (no variance) - Transformers

**Interface:** `InvariantOperator<T>`  
**Pattern:** Both produces and consumes `T`

```kotlin
val exact: InvariantOperator<Solution<Double>> = ...
val wrong: InvariantOperator<Solution<Number>> = exact  // ✗ Must match exactly
```

**Use when:** Mutating, transforming, or modifying solutions

## Integration with Knob Package

The variance interfaces complement existing knob types:

| Knob Type | Variance Equivalent | Notes |
|-----------|---------------------|-------|
| `Objective<T>` | `ContravariantOperator<T>` | Consumes solutions for evaluation |
| `Constraint<T>` | `ContravariantOperator<T>` | Consumes solutions for validation |
| `Perturber<T>` | `InvariantOperator<T>` | Transforms solutions |
| `SolutionEvaluator<T>` | `ContravariantOperator<T>` | Already extends it! |

## Examples

See [OPERATOR_INTEGRATION.md](../OPERATOR_INTEGRATION.md) for comprehensive examples and patterns.

## API Reference

All interfaces and classes have detailed KDoc. Key methods:

- `CovariantOperator.produce(): T` - Generate a solution
- `ContravariantOperator.consume(solution: T)` - Process a solution
- `InvariantOperator` - Combines both produce() and consume()

### Extension Functions

- `producer then consumer` → `OperatorPipeline<T>`
- `consumer1 and consumer2` → `BroadcastOperator<T>`
- `operator1 andThen operator2` → `OperatorChain<T>`

## Testing

Run operator tests:
```bash
./gradlew :core:test --tests "*Operator*" --tests "*Adapter*"
```

## Contributing

When adding new operators:
1. Choose the appropriate variance (covariant/contravariant/invariant)
2. Document why that variance was chosen
3. Show examples of safe type substitution
4. Add unit tests demonstrating variance behavior

## Further Reading

- [Kotlin Variance Documentation](https://kotlinlang.org/docs/generics.html#variance)
- [Declaration-site vs Use-site Variance](https://kotlinlang.org/docs/generics.html#declaration-site-variance)
- Core module integration guide: [OPERATOR_INTEGRATION.md](../OPERATOR_INTEGRATION.md)
