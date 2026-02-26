/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

package cl.ravenhill.kalm.adapters

import cl.ravenhill.kalm.operators.ContravariantOperator
import cl.ravenhill.knob.repr.Solution

/**
 * Adapts a lambda that consumes a [Solution] to the [ContravariantOperator] interface.
 *
 * This adapter allows any function `(Solution<*>) -> Unit` to be used wherever a
 * [ContravariantOperator] is expected, demonstrating the consumer pattern.
 *
 * ## Usage:
 * ```kotlin
 * val logger = SolutionConsumerAdapter<Solution<Double>> { solution ->
 *     println("Solution values: ${solution.toList()}")
 * }
 * logger.consume(someSolution)
 * ```
 *
 * This adapter is particularly useful for:
 * - Logging and debugging
 * - Validation and constraint checking
 * - Side effects (storing to database, sending metrics, etc.)
 *
 * @param T The solution type this adapter can consume.
 * @param consumer A lambda that processes a solution.
 */
class SolutionConsumerAdapter<in T : Solution<*>>(
    private val consumer: (T) -> Unit
) : ContravariantOperator<T> {
    
    override fun consume(solution: T) {
        consumer(solution)
    }
}
