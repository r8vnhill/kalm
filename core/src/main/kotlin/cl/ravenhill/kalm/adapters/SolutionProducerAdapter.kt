/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

package cl.ravenhill.kalm.adapters

import cl.ravenhill.kalm.operators.CovariantOperator
import cl.ravenhill.knob.repr.Solution

/**
 * Adapts a lambda that produces a [Solution] to the [CovariantOperator] interface.
 *
 * This adapter allows any function `() -> Solution<T>` to be used wherever a
 * [CovariantOperator] is expected, demonstrating the producer pattern.
 *
 * ## Usage:
 * ```kotlin
 * val randomSolutionProducer = SolutionProducerAdapter {
 *     Solution.of(Random.nextDouble())
 * }
 * val solution: Solution<Double> = randomSolutionProducer.produce()
 * ```
 *
 * @param T The type parameter of the solution (e.g., Double, Int).
 * @param producer A lambda that produces a solution.
 */
class SolutionProducerAdapter<T : Solution<*>>(
    private val producer: () -> T
) : CovariantOperator<T> {
    
    override fun produce(): T = producer()
}
