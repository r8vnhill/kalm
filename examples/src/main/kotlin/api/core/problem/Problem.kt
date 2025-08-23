/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package api.core.problem

import arrow.core.nonEmptyListOf
import arrow.core.raise.either
import arrow.core.recover
import cl.ravenhill.knob.KnobException
import cl.ravenhill.knob.repr.Solution
import cl.ravenhill.knob.problem.Objective
import cl.ravenhill.knob.problem.Problem
import cl.ravenhill.knob.problem.constrained.EqualityConstraint
import cl.ravenhill.knob.problem.constrained.InequalityConstraint
import cl.ravenhill.knob.utils.EqualityThreshold
import cl.ravenhill.knob.utils.InequalityType

/**
 * ## Formal problem definition:
 *
 * Given a solution vector `{typ} $x = (x_1, x_2, x_3, x_4)$`, define two objectives:
 *
 * 1. `{typ} $f_1(x) = sum_(i = 1)^4 x_i$`
 * 2. `{typ} $f_2(x) = product_(i = 1)^4 x_i$`
 *
 * And two constraints:
 *
 * 1. Equality: `{typ} $sum_(i = 1)^4 x_i approx 10$` with tolerance `{typ} $epsilon$`
 * 2. Inequality: `{typ} $product_(i = 1)^4 x_i <= 100$`
 *
 * ### Symbolically:
 *
 * ```typ
 * $
 * "Find"           & x in RR^4 \
 * "to evaluate"    & (f_1(x), f_2(x)) \
 * "subject to"     & abs(sum_(i = 1)^4 x_i - 10) <= epsilon \
 *                  & product_(i = 1)^4 x_i <= 100
 * $
 * ```
 *
 * In our code, we set `{typ} $epsilon = 10^(-9)$` (the default `EqualityThreshold`).
 */
private fun example1() = either {
    // Use the default equality threshold $epsilon = 10^(-9)$
    with(EqualityThreshold.default) {
        // Build the Problem with two objectives and two constraints:
        val problem = Problem(
            objectives = nonEmptyListOf(
                // $f_1(x) = x_1 + x_2 + x_3 + x_4$
                Objective { it.reduce { acc, v -> acc + v } },
                // $f_2(x) = x_1 * x_2 * x_3 * x_4$
                Objective { it.reduce { acc, v -> acc * v } }
            ),
            constraints = nonEmptyListOf(
                // $abs(sum_(i = 1)^4 x_i - 10) <= epsilon$
                EqualityConstraint(
                    left  = { it.reduce { acc, v -> acc + v } },
                    right = { 10.0 }
                ).bind(),
                // $product_(i = 1)^4 x_i <= 100$
                InequalityConstraint(
                    left     = { it.reduce { acc, v -> acc * v } },
                    right    = { 100.0 },
                    operator = InequalityType.LESS_THAN_OR_EQUAL
                )
            )
        )

        // Sample solution $x = (1, 2, 3, 4)$
        val solution = Solution(1.0, 2.0, 3.0, 4.0)

        // Evaluate objectives: $(f_1, f_2)$
        val result = problem.objectives.let { (sumObj, prodObj) ->
            sumObj(solution) to prodObj(solution)
        }

        // Check all constraints
        val constraintsSatisfied = problem.constraints.all { it(solution) }

        println("Objectives: $result")
        println("Constraints satisfied: $constraintsSatisfied")
    }
}

fun main() {
    example1()
        .recover<_, KnobException, _> { println("Error: ${it.message}") }
}
