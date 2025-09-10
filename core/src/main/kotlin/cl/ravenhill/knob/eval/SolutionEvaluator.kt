/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.eval

import cl.ravenhill.knob.repr.Solution

public fun interface SolutionEvaluator<T> {
    public fun evaluate(solution: Solution<T>): Double
}
