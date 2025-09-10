/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.solver

import cl.ravenhill.knob.problem.GradientAwareProblem
import cl.ravenhill.knob.problem.constrained.Projection
import cl.ravenhill.knob.repr.Solution

public class SteepestDescent<T>(
    private val projection: Projection<T> = Projection { it },
) : AnalyticalSolver<T> {
    override fun invoke(problem: GradientAwareProblem<T>): Solution<T> {
        TODO("Not yet implemented")
    }
}
