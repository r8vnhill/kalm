/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.problem.constrained

import cl.ravenhill.keen.repr.Solution

/**
 * Represents a generic constraint on a [Solution] that evaluates to a boolean.
 *
 * This sealed interface models both equality and inequality constraints as functions from [Solution] to [Boolean].
 * Constraints are evaluated by comparing the left-hand side ([left]) and right-hand side ([right]) expressions.
 *
 * ## Usage:
 * This interface is not meant to be used directly.
 * Prefer using [EqualityConstraint] or [InequalityConstraint].
 *
 * @property left The left-hand side expression of the constraint, evaluated per [Solution].
 * @property right The right-hand side expression of the constraint, evaluated per [Solution].
 * @param T The type of the solution being constrained.
 */
public sealed interface Constraint<T> : (Solution<T>) -> Boolean {
    public val left: (Solution<T>) -> Double
    public val right: (Solution<T>) -> Double
}
