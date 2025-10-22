/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package api.core.solver

import cl.ravenhill.knob.repr.Solution
import cl.ravenhill.knob.benchmark.sphereProblem
import cl.ravenhill.knob.problem.Problem
import cl.ravenhill.knob.solver.Solver

/**
 * Demonstrates solving a simple optimization problem using a custom solver.
 *
 * The problem used here is the Sphere problem, which aims to minimize the sum of squares of the input values.
 * In this case, we use a 1-dimensional version: `{typ} $f(x) = x^2$`, with `{typ} $x$` in the range [-10, 10].
 *
 * The solver here is a brute-force approach that evaluates all candidate solutions in the range and returns the one
 * with the minimum objective value.
 */
fun example1() {
    // Define the optimization problem (minimize $x^2$ for $x in RR$)
    val problem = sphereProblem()

    // Generate candidates: x = -10, -8, ..., 0, ..., 10
    val candidates = (-10..10 step 2)
        .map { Solution.of(it.toDouble()) }

    // Define a naive solver that evaluates a fixed set of candidate solutions
    val solver: Solver<Double, Problem<Double>> = Solver { problem ->
        // Solver logic: pick the solution with the minimum objective value
        candidates
            .minByOrNull { candidate ->
                // Evaluate the first (and only) objective function
                problem.objectives.first().invoke(candidate)
            }
            ?: Solution.of(Double.POSITIVE_INFINITY) // Fallback in case the list was empty (not possible here)
    }

    // Run the solver to find the best solution
    val bestSolution = solver(problem)

    // Print the result
    println("Best solution found: ${bestSolution.toList()}")
}

fun main() {
    example1()
}
