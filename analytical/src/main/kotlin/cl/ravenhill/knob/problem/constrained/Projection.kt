/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.problem.constrained

import cl.ravenhill.knob.repr.Solution

public fun interface Projection<T> {
    public fun project(x: Solution<T>): Solution<T>
}
