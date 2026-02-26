/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

package cl.ravenhill.kalm.adapters

import cl.ravenhill.kalm.operators.InvariantOperator
import cl.ravenhill.knob.repr.Solution

/**
 * Adapts a transformation function to the [InvariantOperator] interface.
 *
 * This adapter bridges the gap between functional transformation patterns and the
 * operator interface hierarchy. It maintains an internal state (the latest solution)
 * that can be consumed and produced.
 *
 * ## Invariance Explained:
 * Unlike covariant (producer-only) or contravariant (consumer-only) operators,
 * an invariant operator must be able to both produce AND consume solutions of
 * exactly type [T]. This restriction enables type-safe bidirectional operations.
 *
 * ## Usage:
 * ```kotlin
 * val mutator = SolutionTransformerAdapter<Solution<Double>> { solution ->
 *     Solution.of(solution[0] + Random.nextGaussian())
 * }
 * 
 * mutator.consume(originalSolution)  // Stores transformed result
 * val mutated = mutator.produce()     // Retrieves transformed solution
 * ```
 *
 * @param T The exact solution type (both input and output).
 * @param transform A function that maps a solution to another solution of the same type.
 */
class SolutionTransformerAdapter<T : Solution<*>>(
    private val transform: (T) -> T
) : InvariantOperator<T> {
    
    private var currentSolution: T? = null
    
    override fun consume(solution: T) {
        currentSolution = transform(solution)
    }
    
    override fun produce(): T = currentSolution
        ?: error("No solution has been consumed yet. Call consume() before produce().")
}
