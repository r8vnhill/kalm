/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.problem.objective

import cl.ravenhill.knob.repr.Solution

/**
 * Represents an objective function in an optimization problem.
 *
 * This is a functional interface that maps a [cl.ravenhill.knob.repr.Solution] of type [T] to a [Double] value representing its objective
 * score (e.g., fitness, cost, or utility).
 * Higher-level algorithms use this function to evaluate and compare candidate solutions.
 *
 * ## Usage:
 * This interface enables passing lambdas directly as objective functions:
 * ```kotlin
 * val objective: Objective<MyRepresentation> = Objective { solution ->
 *     solution.values.sum() // Minimize or maximize this value
 * }
 * ```
 *
 * @param T The type of the representation stored in the [cl.ravenhill.knob.repr.Solution].
 */
public fun interface Objective<T> : (Solution<T>) -> Double {

    /**
     * Evaluates the given [solution] and returns its objective value.
     *
     * @param solution The solution to evaluate.
     * @return A [Double] representing the solution's score.
     */
    override operator fun invoke(solution: Solution<T>): Double
}
