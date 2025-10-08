/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.ranking

import cl.ravenhill.knob.repr.Solution

public fun interface Oracle<T> {
    public infix fun Solution<T>.isBetterThan(other: Solution<T>): Boolean
}
