/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.problem.objective

import cl.ravenhill.knob.repr.Gradient
import cl.ravenhill.knob.repr.Solution


/**
 * Represents an objective function that is differentiable.
 *
 * A [DifferentiableObjective] is an extension of [Objective] that not only evaluates the value of a solution but also
 * exposes its gradient.
 * This makes it suitable for optimization methods that rely on gradient information, such as gradient descent or
 * quasi-Newton methods.
 *
 * ## Usage:
 * ```kotlin
 * // A quadratic objective f(x) = Σ xᵢ²
 * object QuadraticObjective : DifferentiableObjective<Double> {
 *     override fun gradient(at: Solution<Double>): Gradient =
 *         Gradient.of(
 *
 *     override fun invoke(solution: Solution<Double>): Double =
 *         solution.sumOf { it * it }
 * }
 * ```
 *
 * @param T The type of elements that make up a [Solution].
 */
public interface DifferentiableObjective<T> : Objective<T> {

    /**
     * Computes the gradient of the objective at a given solution.
     *
     * @param at The [Solution] at which to evaluate the gradient.
     * @return A [Gradient] representing the derivative information of this objective.
     */
    public fun gradient(at: Solution<T>): Gradient
}

/**
 * Wraps a non-differentiable [Objective] into a [DifferentiableObjective] by supplying an explicit gradient function.
 *
 * This adapter is useful when you already have a plain objective but want to attach derivative information dynamically,
 * enabling the use of gradient-based optimization algorithms without rewriting the objective.
 *
 * @param gradient A function that computes the [Gradient] at a given [Solution].
 * @return A [DifferentiableObjective] that delegates evaluation to the original [Objective] and provides the given
 *   gradient function.
 *
 * ## Usage:
 * ```kotlin
 * // A simple linear objective f(x) = Σ xᵢ with gradient g(x) = [1, 1, ..., 1]
 * val linearObjective = Objective<Double> { solution -> solution.sum() }
 * val differentiable = linearObjective.toDifferentiable { _ -> Gradient.of(1.0, 1.0, 1.0) }
 *
 * val sol = Solution.of(1.0, 2.0, 3.0)
 * println(differentiable(sol))        // → 6.0
 * println(differentiable.gradient(sol)) // → Gradient [1.0, 1.0, 1.0]
 * ```
 */
public fun <T> Objective<T>.toDifferentiable(
    gradient: (at: Solution<T>) -> Gradient
): DifferentiableObjective<T> =
    object : DifferentiableObjective<T> {
        override fun gradient(at: Solution<T>): Gradient = gradient(at)
        override fun invoke(solution: Solution<T>): Double = this@toDifferentiable(solution)
    }
