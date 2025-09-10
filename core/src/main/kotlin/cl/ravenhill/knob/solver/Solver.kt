/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.solver

import cl.ravenhill.knob.problem.Problem
import cl.ravenhill.knob.repr.Solution

public fun interface Solver<T, P : Problem<T>> : (P) -> Solution<T> {
    override fun invoke(problem: P): Solution<T>    // Override Function1 invoke to rename `p1` to `problem`
}
