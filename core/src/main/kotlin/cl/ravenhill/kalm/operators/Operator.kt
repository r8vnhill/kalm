/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

package cl.ravenhill.kalm.operators

import cl.ravenhill.knob.repr.Solution

/**
 * An operator that produces solutions without consuming them (covariant in [T]).
 *
 * The `out` variance modifier indicates this operator only produces values of type [T],
 * never consumes them. This enables type-safe assignment to less specific types:
 *
 * ## Covariance Example:
 * ```kotlin
 * interface Animal
 * class Dog : Animal
 *
 * val dogProducer: CovariantOperator<Solution<Dog>> = ...
 * val animalProducer: CovariantOperator<Solution<Animal>> = dogProducer  // ✓ Type-safe
 * ```
 *
 * This is safe because a "Dog producer" can always be used wherever an "Animal producer"
 * is expected—since all Dogs are Animals, producing a Dog means you've produced an Animal.
 *
 * ## Use Cases:
 * - Solution generators and factories
 * - Random solution initializers
 * - Cached solution providers
 * - Constant solution sources
 *
 * ## See Also:
 * - [ContravariantOperator] for consumer-only operators
 * - [InvariantOperator] for operators that both produce and consume
 *
 * @param T The type of solution this operator produces (covariant).
 */
interface CovariantOperator<out T : Solution<*>> {
    /**
     * Produces a new solution instance.
     *
     * @return A solution of type [T].
     */
    fun produce(): T
}

/**
 * An operator that consumes solutions without producing them (contravariant in [T]).
 *
 * The `in` variance modifier indicates this operator only consumes values of type [T],
 * never produces them. This enables type-safe assignment to more specific types:
 *
 * ## Contravariance Example:
 * ```kotlin
 * interface Animal
 * class Dog : Animal
 *
 * val animalConsumer: ContravariantOperator<Solution<Animal>> = ...
 * val dogConsumer: ContravariantOperator<Solution<Dog>> = animalConsumer  // ✓ Type-safe
 * ```
 *
 * This reversal is safe because if an operator can consume any Animal, it can certainly
 * consume a Dog (which is a specific kind of Animal). Contravariance on input positions!
 *
 * ## Use Cases:
 * - Solution evaluators and fitness functions
 * - Validators and constraint checkers
 * - Loggers and monitors
 * - Statistics collectors
 * - Side-effect handlers (database writes, metrics, etc.)
 *
 * ## See Also:
 * - [CovariantOperator] for producer-only operators
 * - [InvariantOperator] for operators that both produce and consume
 * - [cl.ravenhill.kalm.eval.SolutionEvaluator] for evaluation-specific consumers
 *
 * @param T The type of solution this operator consumes (contravariant).
 */
interface ContravariantOperator<in T : Solution<*>> {
    /**
     * Consumes a solution for processing.
     *
     * This method performs side effects (validation, logging, statistics, etc.)
     * but does not return a value.
     *
     * @param solution The solution to consume.
     */
    fun consume(solution: T)
}

/**
 * An operator that both produces and consumes solutions (invariant in [T]).
 *
 * Unlike covariant or contravariant operators, invariant operators have no variance
 * modifier on [T]. This means [T] must match exactly—no subtype substitution is allowed.
 *
 * ## Invariance Constraint:
 * ```kotlin
 * val doubleOp: InvariantOperator<Solution<Double>> = ...
 * val numberOp: InvariantOperator<Solution<Number>> = doubleOp  // ✗ Type error!
 * ```
 *
 * This restriction exists because the operator uses [T] in both input (contravariant)
 * and output (covariant) positions, which are incompatible variance requirements.
 *
 * ## Why Invariant Operators?
 * Despite the type flexibility trade-off, invariant operators enable:
 * - **Transformation pipelines**: consume a solution, transform it, produce the result
 * - **Stateful processing**: maintain internal state between consume/produce cycles
 * - **Bidirectional data flow**: operators that need both input and output capabilities
 *
 * ## Use Cases:
 * - Mutation operators (consume solution, produce mutated version)
 * - Crossover operators (consume parents, produce offspring)
 * - Local search operators (consume current solution, produce neighbor)
 * - Repair operators (consume invalid solution, produce repaired version)
 *
 * ## Usage Pattern:
 * ```kotlin
 * val mutator: InvariantOperator<Solution<Double>> = ...
 * 
 * mutator.consume(originalSolution)  // Transform and store internally
 * val mutated = mutator.produce()    // Retrieve transformed solution
 * ```
 *
 * ## See Also:
 * - [CovariantOperator] for producer-only operators (allows covariance)
 * - [ContravariantOperator] for consumer-only operators (allows contravariance)
 *
 * @param T The exact solution type (invariant—must match precisely).
 */
interface InvariantOperator<T : Solution<*>> : CovariantOperator<T>, ContravariantOperator<T>
