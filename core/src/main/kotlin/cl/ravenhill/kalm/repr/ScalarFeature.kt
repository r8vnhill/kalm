/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

package cl.ravenhill.kalm.repr

public data class ScalarFeature(public val x: Double) : Feature<Double, ScalarFeature> {

    override fun map(f: (Double) -> Double): ScalarFeature = copy(x = f(x))

    override fun <T2, F2> flatMap(f: (Double) -> F2): F2 where F2 : Feature<T2, F2> = f(x)
}
