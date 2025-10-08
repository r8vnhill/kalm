/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.ranking

import cl.ravenhill.knob.repr.Solution

public interface SolutionRanker<T> {
    public val winner: Solution<T>
}
