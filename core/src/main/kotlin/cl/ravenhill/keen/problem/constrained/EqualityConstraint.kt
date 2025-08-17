/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.problem.constrained

import arrow.core.Either
import arrow.core.raise.either
import cl.ravenhill.keen.repr.Solution
import cl.ravenhill.keen.exceptions.InvalidThresholdException
import cl.ravenhill.keen.utils.EqualityThreshold
import kotlin.math.abs

/**
 * Represents an equality constraint over a [Solution], evaluated with a configurable tolerance.
 *
 * An [EqualityConstraint] defines two real-valued expressions, [left] and [right], over a solution.
 * It is satisfied if the absolute difference between both expressions is less than or equal to a given [threshold].
 *
 * This abstraction is useful for expressing approximate equality constraints in optimization problems, where
 * floating-point inaccuracies must be accounted for.
 *
 * ## Examples
 *
 * Complete examples can be found in the [examples](https://www.github.com/r8vnhill/keen/tree/main/examples/) directory,
 * which demonstrates how to use this constraint in practice.
 *
 * ### Example 1: Using the `with` context to create and evaluate a constraint
 *
 * ```kotlin
 * with(EqualityThreshold.default) {
 *     EqualityConstraint(left = f, right = g)
 *         .getOrNull() // Extracts the constraint if threshold validation passed
 *         ?.invoke(s)  // Evaluates the constraint over the solution
 *         ?.let { result ->
 *             // Print whether the constraint was satisfied (true/false)
 *             println("Constraint satisfied: $result")
 *         }
 * }
 * ```
 *
 * ### Example 2: Using Arrow's `either` block to handle potential threshold errors explicitly
 *
 * ```kotlin
 * either {
 *     // Build the constraint using the default threshold (1e-9).
 *     val constraint = EqualityConstraint.withDefaultThreshold(
 *         left = { it[0] },
 *         right = { it[1] }
 *     ).bind() // Extract the value or short-circuit if invalid
 *
 *     val result = constraint(Solution(1.0, 1.0000000001))
 *
 *     // Print the evaluation result
 *     println("Constraint satisfied: $result")
 * }
 * ```
 *
 * ### Example 3: Manually implementing an [EqualityConstraint] with a custom threshold
 *
 * This approach is not recommended for most use cases but can be useful for advanced scenarios.
 *
 * ```kotlin
 * val constraint = object : EqualityConstraint<Double> {
 *     override val threshold: EqualityThreshold = customThreshold
 *     override val left: (Solution<Double>) -> Double = { it[0] }
 *     override val right: (Solution<Double>) -> Double = { it[1] }
 *
 *     // Evaluates the constraint
 *     override fun invoke(solution: Solution<Double>) = ...
 * }
 * ```
 *
 * @param T The type of values held by the evaluated [Solution].
 * @property threshold The tolerance to use when comparing the left and right expressions.
 *   The constraint is satisfied if `abs(left - right) <= threshold`.
 */
public interface EqualityConstraint<T> : Constraint<T> {

    /** The allowed deviation between [left] and [right] for the constraint to be satisfied. */
    public val threshold: EqualityThreshold

    override fun invoke(solution: Solution<T>): Boolean =
        abs(left(solution) - right(solution)) <= threshold

    /**
     * Compares a [Double] against a [EqualityThreshold] for convenience in internal usage.
     */
    private operator fun Double.compareTo(other: EqualityThreshold) = when {
        this < other.value -> -1
        this > other.value -> 1
        else -> 0
    }

    public companion object {
        /**
         * Creates a new [EqualityConstraint] using the provided [left] and [right] expressions, within the context of a
         * validated [EqualityThreshold].
         *
         * This method returns an [Either] indicating whether the threshold is valid.
         *
         * @param left A function extracting a real value from a [Solution].
         * @param right A function extracting a real value from a [Solution].
         * @return An [EqualityConstraint] if the threshold is valid; otherwise an [InvalidThresholdException].
         */
        context(threshold: Either<InvalidThresholdException, EqualityThreshold>)
        public operator fun <T> invoke(
            left: (Solution<T>) -> Double,
            right: (Solution<T>) -> Double
        ): Either<InvalidThresholdException, EqualityConstraint<T>> = either {
            val boundThreshold = threshold.bind()
            object : EqualityConstraint<T> {
                override val threshold: EqualityThreshold = boundThreshold
                override val left: (Solution<T>) -> Double = left
                override val right: (Solution<T>) -> Double = right
            }
        }

        /**
         * Creates a new [EqualityConstraint] using the given [left] and [right] expressions and a default or custom
         * threshold.
         *
         * This method is a convenience overload that wraps the given [threshold] value into a [EqualityThreshold] and
         * automatically validates it.
         *
         * @param left A function extracting a real value from a [Solution].
         * @param right A function extracting a real value from a [Solution].
         * @param threshold The maximum allowed deviation between [left] and [right].
         *   Defaults to [EqualityThreshold.DEFAULT].
         * @return An [EqualityConstraint] if the threshold is valid; otherwise an [InvalidThresholdException].
         */
        public fun <T> withDefaultThreshold(
            left: (Solution<T>) -> Double,
            right: (Solution<T>) -> Double,
            threshold: Double = EqualityThreshold.DEFAULT
        ): Either<InvalidThresholdException, EqualityConstraint<T>> =
            with(EqualityThreshold(threshold)) {
                invoke(left, right)
            }
    }
}
