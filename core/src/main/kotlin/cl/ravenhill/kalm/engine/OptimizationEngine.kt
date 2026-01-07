/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

package cl.ravenhill.kalm.engine

import cl.ravenhill.kalm.repr.Feature

interface OptimizationEngine<F> where F : Feature<*, F> {

    val objectiveFunction: (F) -> Double

    fun optimize(initialState: F): F
}
