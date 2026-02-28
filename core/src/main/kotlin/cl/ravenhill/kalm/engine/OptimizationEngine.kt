/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

package cl.ravenhill.kalm.engine

import cl.ravenhill.kalm.repr.Feature

public interface OptimizationEngine<F> where F : Feature<*, F> {

    public val objectiveFunction: (F) -> Double

    public fun optimize(initialState: F): F
}
