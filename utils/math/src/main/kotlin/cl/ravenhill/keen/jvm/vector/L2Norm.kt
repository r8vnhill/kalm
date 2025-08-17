/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.jvm.vector

import jdk.incubator.vector.DoubleVector
import jdk.incubator.vector.VectorOperators
import jdk.incubator.vector.VectorSpecies
import kotlin.math.sqrt

public interface L2Norm {
    public fun DoubleArray.squaredL2Norm(): Double
    public fun DoubleArray.l2Norm(): Double
}

internal class VectorizedL2Norm(
    private val species: VectorSpecies<Double>,
    private val lanes: Int
) : L2Norm {

    override fun DoubleArray.squaredL2Norm(): Double {
        val n = size
        if (n == 0) return 0.0

        var i = 0
        var acc = DoubleVector.zero(species)
        val limit = n - (n % lanes)

        while (i < limit) {
            val v = DoubleVector.fromArray(species, this, i)
            acc = v.fma(v, acc) // acc += v * v
            i += lanes
        }

        if (i < n) {
            val m = species.indexInRange(i, n)
            val v = DoubleVector.fromArray(species, this, i, m)
            acc = v.fma(v, acc)
        }

        return acc.reduceLanes(VectorOperators.ADD)
    }

    override fun DoubleArray.l2Norm(): Double =
        sqrt(this.squaredL2Norm())
}
