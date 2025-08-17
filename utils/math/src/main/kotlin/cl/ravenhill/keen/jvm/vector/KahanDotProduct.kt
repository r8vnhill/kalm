/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.jvm.vector

import cl.ravenhill.keen.utils.DoubleArraySlice
import cl.ravenhill.keen.utils.requireSliceInBounds
import jdk.incubator.vector.DoubleVector
import jdk.incubator.vector.VectorSpecies

internal interface KahanDotProduct {
    infix fun DoubleArray.dotProductKahan(that: DoubleArray): Double =
        dotProductKahan(this, 0, minOf(this.size, that.size), that, 0)

    fun dotProductKahan(a: DoubleArray, aOff: Int, len: Int, b: DoubleArray, bOff: Int): Double
}

internal class VectorizedKahanDotProduct(private val species: VectorSpecies<Double>, lanes: Int) : KahanDotProduct {
    override fun dotProductKahan(
        a: DoubleArray, aOff: Int, len: Int,
        b: DoubleArray, bOff: Int
    ): Double {
        if (len <= 0) return 0.0
        requireSliceInBounds(a.size, aOff, len)
        requireSliceInBounds(b.size, bOff, len)

        val zero = DoubleVector.zero(species)
        val state0 = KahanState(sum = zero, comp = zero)

        val viewA = DoubleArraySlice(a, aOff)
        val viewB = DoubleArraySlice(b, bOff)

        // Vectorized Kahan over full-width blocks.
        val (next, state1) = kahanBlockReduce(viewA, viewB, len, state0)

        // Apply one masked Kahan step to the tail (if any), then horizontally reduce with scalar Kahan.
        val state2 = kahanMaskedTail(viewA, DoubleArraySlice(b, bOff), next, len, state = state1)
        return horizontalKahan(state2.sum, state2.comp)
    }

    /**
     * Vectorized Kahan over full-width blocks (no mask), returning the next index and state.
     * Complexity: `O(len)`. No heap allocations on the hot path; JIT typically scalarizes `KahanState`.
     */
    private fun kahanBlockReduce(
        a: DoubleArraySlice,
        b: DoubleArraySlice,
        len: Int,
        state0: KahanState
    ): Pair<Int, KahanState> {
        var i = 0
        var sum = state0.sum
        var comp = state0.comp
        val limit = len - (len % lanes)

        while (i < limit) {
            val va = DoubleVector.fromArray(species, a.arr, a.offset + i)
            val vb = DoubleVector.fromArray(species, b.arr, b.offset + i)
            val prod = va.mul(vb)
            val (s, c) = kahanUpdate(sum, comp, prod)
            sum = s; comp = c
            i += lanes
        }
        return i to KahanState(sum, comp)
    }


    /**
     * Applies one masked Kahan update for the final **partial** block (if `next < len`).
     *
     * Loads using a mask so we keep the final work vectorized instead of falling back to scalar.
     * `len` is the total range length from the current start (i.e., we index `[0, len)`).
     */
    private fun kahanMaskedTail(
        a: DoubleArraySlice,
        b: DoubleArraySlice,
        next: Int,
        len: Int,
        state: KahanState
    ): KahanState =
        (a to b).maskedTail(next, len, onEmpty = { state }) { va, vb ->
            val prod = va.mul(vb)
            kahanUpdate(state.sum, state.comp, prod)
        }

    /**
     * One Kahan update (per lane):
     * - `y  = prod - comp`
     * - `t  = sum + y`
     * - `comp' = (t - sum) - y`
     * - `sum'  = t`
     */
    private fun kahanUpdate(
        sum: DoubleVector,
        comp: DoubleVector,
        prod: DoubleVector
    ): KahanState {
        val y = prod.sub(comp)
        val t = sum.add(y)
        val newComp = t.sub(sum).sub(y)
        return KahanState(sum = t, comp = newComp)
    }


    /**
     * Horizontal Kahan reduction of two vectors `(sumV, compV)` into a scalar.
     * We compute `Σ_k (sumV_k + compV_k)` using scalar Kahan to preserve compensation across lanes.
     */
    private fun horizontalKahan(sumV: DoubleVector, compV: DoubleVector): Double {
        var s = 0.0
        var c = 0.0
        var k = 0
        while (k < lanes) {
            val laneTotal = sumV.lane(k) + compV.lane(k)
            val y = laneTotal - c
            val t = s + y
            c = (t - s) - y
            s = t
            k++
        }
        return s
    }

    /** Kahan per-lane state (sum and compensation). */
    private data class KahanState(val sum: DoubleVector, val comp: DoubleVector)
}
