/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package api.core.problem.constrained

import cl.ravenhill.knob.problem.constrained.InequalityConstraint
import cl.ravenhill.knob.repr.Solution
import cl.ravenhill.knob.utils.InequalityType

/**
 * Evaluates an [InequalityConstraint] using the given operator and expressions over a [Solution].
 *
 * @param f The left-hand side expression.
 * @param g The right-hand side expression.
 * @param solution The solution to evaluate.
 * @param op The inequality type to use (e.g., <, >, <=, >=).
 */
private fun <T> example1(
    f: (Solution<T>) -> Double,
    g: (Solution<T>) -> Double,
    solution: Solution<T>,
    op: InequalityType
) {
    // Construct the constraint using the given operator and expressions
    val constraint = InequalityConstraint(
        left = f,
        right = g,
        operator = op,
    )

    // Print the concrete evaluation of the inequality
    println("Constraint: ${constraint.left(solution)} ${op.symbol} ${constraint.right(solution)}")

    // Evaluate whether the constraint holds for the given solution
    val result = constraint(solution)

    // Print whether the constraint was satisfied
    println("Constraint satisfied: $result")
}

fun main() {
    // Try each inequality type with a sample solution
    for (type in InequalityType.entries) {
        example1(
            f = { it.first() },          // Left expression: first element
            g = { it.last() },           // Right expression: last element
            solution = Solution(1.0, 2.0, 3.0),
            op = type
        )
    }
}
