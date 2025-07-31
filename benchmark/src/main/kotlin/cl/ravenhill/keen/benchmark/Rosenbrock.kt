/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.benchmark

import cl.ravenhill.keen.problem.Objective
import cl.ravenhill.keen.problem.Problem
import cl.ravenhill.keen.problem.constrained.Constraint
import kotlin.math.pow

/**
 * The Rosenbrock objective function.
 *
 * This function is a classic benchmark in optimization, defined as:
 *
 * ```
 * f(x) = Σᵢ [100 * (xᵢ₊₁ - xᵢ²)² + (1 - xᵢ)²]
 * ```
 *
 * It features a narrow, curved valley leading to the global minimum at `(1, 1, ..., 1)`, making it challenging for
 * optimization algorithms due to its non-convexity and ill-conditioning.
 *
 * @return An [Objective] instance representing the Rosenbrock function.
 */
public fun rosenbrock(): Objective<Double> = Objective { solution ->
    solution.zipWithNext { x, y ->
        100 * (y - x.pow(2)).pow(2) + (1 - x).pow(2)
    }.sum()
}

/**
 * Creates a [Problem] instance using the Rosenbrock objective function.
 *
 * This utility provides an easy way to configure the Rosenbrock problem with optional constraints.
 *
 * @param constraints A collection of [Constraint]s to enforce on candidate solutions.
 * @return A [Problem] configured with the Rosenbrock objective and the given constraints.
 */
public fun rosenbrockProblem(constraints: Collection<Constraint<Double>> = listOf()): Problem<Double> =
    Problem(rosenbrock(), constraints = constraints)
