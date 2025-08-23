/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package api.benchmark

import cl.ravenhill.knob.repr.Solution
import cl.ravenhill.knob.benchmark.rosenbrockProblem
import cl.ravenhill.knob.benchmark.sphereConstraint

fun main() {
    val problem = rosenbrockProblem(listOf(sphereConstraint(dims = 3)))
    val solution = Solution(1.0, 1.0, 1.0) // A candidate solution
    val fitness = problem.objectives.first().invoke(solution)

    println("Rosenbrock fitness for solution ${solution.toList()}: $fitness")
}
