/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.problem.constrained

import cl.ravenhill.keen.repr.Solution
import cl.ravenhill.keen.util.InequalityType

/**
 * Represents an inequality constraint over a [Solution], evaluated using a specified [InequalityType].
 *
 * An [InequalityConstraint] defines two real-valued expressions, [left] and [right], over a solution.
 * It is satisfied if the relation `left(solution) <op> right(solution)` holds, where `<op>` is determined by the given
 * [type].
 *
 * This is useful for modeling inequality conditions in optimization problems, such as bounding variables, enforcing
 * resource limits, or expressing problem-specific feasibility conditions.
 *
 * ## Evaluation
 *
 * The constraint is evaluated based on the [type]:
 * - `<` if [InequalityType.LESS_THAN]
 * - `>` if [InequalityType.GREATER_THAN]
 * - `<=` if [InequalityType.LESS_THAN_OR_EQUAL]
 * - `>=` if [InequalityType.GREATER_THAN_OR_EQUAL]
 *
 * ## Usage
 *
 * Use the factory method in the [Companion] object to construct constraints:
 *
 * ```kotlin
 * val constraint = InequalityConstraint(
 *     left = { it[0] },
 *     right = { it[1] },
 *     operator = InequalityType.LESS_THAN
 * )
 * println(constraint(Solution(1.0, 2.0))) // true since 1.0 < 2.0
 * ```
 *
 * @param T The type of values in the evaluated [Solution].
 * @property type The relational operator used to compare [left] and [right].
 */
public interface InequalityConstraint<T> : Constraint<T> {

    public val type: InequalityType

    override fun invoke(solution: Solution<T>): Boolean = when (type) {
        InequalityType.LESS_THAN -> left(solution) < right(solution)
        InequalityType.GREATER_THAN -> left(solution) > right(solution)
        InequalityType.LESS_THAN_OR_EQUAL -> left(solution) <= right(solution)
        InequalityType.GREATER_THAN_OR_EQUAL -> left(solution) >= right(solution)
    }

    public companion object {
        /**
         * Creates an [InequalityConstraint] with the specified expressions and operator.
         *
         * @param left The left-hand side expression to evaluate.
         * @param right The right-hand side expression to evaluate.
         * @param operator The relational operator used to compare both expressions.
         * @return A new [InequalityConstraint] instance.
         */
        public operator fun <T> invoke(
            left: (Solution<T>) -> Double,
            right: (Solution<T>) -> Double,
            operator: InequalityType,
        ): InequalityConstraint<T> = object : InequalityConstraint<T> {
            override val type: InequalityType = operator
            override val left: (Solution<T>) -> Double = left
            override val right: (Solution<T>) -> Double = right
        }
    }
}
