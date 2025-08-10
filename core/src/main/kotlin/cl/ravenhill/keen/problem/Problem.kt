/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.problem

import arrow.core.NonEmptyCollection
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import cl.ravenhill.keen.repr.Solution
import cl.ravenhill.keen.problem.constrained.Constraint

/**
 * Represents an optimization problem consisting of one or more objective functions and optional constraints.
 *
 * A [Problem] encapsulates all the components needed to evaluate the quality and feasibility of candidate solutions in
 * an optimization task.
 * Each problem must have at least one [Objective], and may optionally include a collection of [Constraint]s that
 * restrict the feasible solution space.
 *
 * ## Usage:
 *
 * The companion object provides two factory methods to construct a [Problem] using either a single objective or
 * multiple objectives via a [NonEmptyCollection].
 *
 * ### Example 1: Single-objective problem without constraints
 *
 * ```kotlin
 * val objective = Objective<Double> { it.sum() }
 * val problem = Problem(objective)
 * ```
 *
 * ### Example 2: Multi-objective problem with constraints
 * ```kotlin
 * val f1 = Objective<Double> { it.sum() }
 * val f2 = Objective<Double> { it.maxOrNull() ?: 0.0 }
 * val constraint = sphereConstraint(dims = 3)
 *
 * val problem = Problem(f1, f2, constraints = listOf(constraint))
 * ```
 *
 * Here, `sphereConstraint` is a function from the [benchmark](https://www.github.com/r8vnhill/keen-op/tree/main/benchmark)
 * module.
 *
 * ## Note:
 *
 * A collection of well-known problems can be found in the [benchmark](https://www.github.com/r8vnhill/keen-op/tree/main/benchmark)
 * module.
 *
 * @param T The type of the representation stored in the [Solution]s evaluated by this problem.
 * @property objectives The non-empty collection of objective functions to be optimized.
 * @property constraints The constraints that candidate solutions must satisfy.
 */
public interface Problem<T> {

    public val objectives: NonEmptyList<Objective<T>>
    public val constraints: Collection<Constraint<T>>

    public companion object {
        /**
         * Creates a [Problem] from a non-empty collection of objectives.
         *
         * @param objectives A non-empty collection of [Objective]s.
         * @param constraints Optional constraints that restrict the solution space.
         * @return A new [Problem] instance.
         */
        public operator fun <T> invoke(
            objectives: NonEmptyList<Objective<T>>,
            constraints: Collection<Constraint<T>> = listOf()
        ): Problem<T> = object : Problem<T> {
            override val objectives = objectives
            override val constraints = constraints
        }

        /**
         * Creates a [Problem] from one or more objectives.
         *
         * @param objective The primary [Objective].
         * @param objectives Additional objectives, if any.
         * @param constraints Optional constraints to be applied to the problem.
         * @return A new [Problem] instance.
         */
        public operator fun <T> invoke(
            objective: Objective<T>,
            vararg objectives: Objective<T>,
            constraints: Collection<Constraint<T>> = listOf()
        ): Problem<T> = Problem(nonEmptyListOf(objective, *objectives), constraints)
    }
}
