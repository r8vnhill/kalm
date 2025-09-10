/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.solver

import cl.ravenhill.knob.repr.Solution

public fun interface NeighborGenerator<T> {
    public fun generateNeighbor(solution: Solution<T>?): Solution<T>
}
