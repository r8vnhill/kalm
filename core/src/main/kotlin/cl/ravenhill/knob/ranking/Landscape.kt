/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.ranking

import cl.ravenhill.knob.repr.Solution

public interface A<T> {
    public val peak: Solution<T>
    public val rest: A<T>
}

