/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.operator

import cl.ravenhill.knob.repr.Solution

public fun interface Perturber<T> {
    public fun perturb(solution: Solution<T>): Solution<T>
}
