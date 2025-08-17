/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.benchmark

import cl.ravenhill.keen.problem.Objective
import cl.ravenhill.keen.problem.Problem
import cl.ravenhill.keen.problem.constrained.Constraint
import cl.ravenhill.keen.problem.constrained.InequalityConstraint
import cl.ravenhill.keen.repr.Solution
import cl.ravenhill.keen.utils.InequalityType
import kotlin.math.pow

public val sphereObjective: Objective<Double> = Objective { solution: Solution<Double> ->
    solution.sumOf { it.pow(2) }
}

public fun sphereProblem(constraints: List<Constraint<Double>> = listOf()): Problem<Double> = Problem(
    objective = sphereObjective,
    constraints = constraints,
)

/**
 * Builds a spherical inequality constraint for a subset of the solution's dimensions.
 *
 * This constraint enforces that the sum of squares of the first [dims] variables in a solution is less than or equal to
 * the square of the specified [radius].
 * Geometrically, this corresponds to keeping the solution within a hypersphere of given radius centered at the origin.
 *
 * This is commonly used as a feasibility constraint in optimization problems where solutions must lie within a bounded
 * Euclidean region.
 *
 * ## Mathematical form:
 *
 * ```
 * x₁² + x₂² + ... + x_d² ≤ r²
 * ```
 * where `d = dims` and `r = radius`.
 *
 * @param dims The number of dimensions to include in the sum.
 *   Must be ≤ the size of the solution.
 * @param radius The radius of the sphere.
 *   Defaults to 1.0.
 * @return An [InequalityConstraint] that checks if the point lies within the specified sphere.
 */
public fun sphereConstraint(dims: Int, radius: Double = 1.0): InequalityConstraint<Double> = InequalityConstraint(
    left = { it.take(dims).sumOf { x -> x.pow(2) } },
    right = { radius.pow(2) },
    operator = InequalityType.LESS_THAN_OR_EQUAL
)
