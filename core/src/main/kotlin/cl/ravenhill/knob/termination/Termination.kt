/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.termination

import cl.ravenhill.knob.problem.Problem
import cl.ravenhill.knob.repr.Solution

public fun interface Termination<T, P : Problem<T>> {
    public fun shouldStop(problem: P, solution: Solution<T>?): Boolean
}
