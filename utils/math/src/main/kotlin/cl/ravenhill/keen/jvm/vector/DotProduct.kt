/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.jvm.vector

import jdk.incubator.vector.DoubleVector
import jdk.incubator.vector.VectorOperators
import jdk.incubator.vector.VectorSpecies

/**
 * Dot product kernels for [DoubleArray]s.
 *
 * Let `a, b in RR^N`. The **dot product** is
 * ```typ
 * a · b = sum_(i=0)^(N-1) a_i b_i
 * ```
 *
 * This API provides two implementations:
 * - [dotProduct] — high-throughput SIMD accumulation (Vector API + FMA) followed by a horizontal sum.
 * - [dotProductKahan] — SIMD accumulation using **Kahan compensated summation** per lane for improved numerical
 *   stability when inputs have mixed magnitudes or alternating signs.
 *
 * ### Sliced (offset) variant
 * The sliced overloads operate on **contiguous subarrays** of `a` and `b`, starting at offsets `aOff` and `bOff`,
 * and spanning `N` elements.
 * Formally, for `0 <= N`, `0 <= aOff + N <= |a|`, `0 <= bOff + N <= |b|`, the computation is:
 *
 * ```typ
 * o_a = "offset into array a" \
 * o_b = "offset into array b" \
 * a[o_a..o_a+N) · b[o_b..o_b+N)
 *   = sum_(i=0)^(N-1) a_(o_a + i) × b_(o_b + i)
 * ```
 *
 * In other words, we take the slice `a[o_a], a[o_a+1], ..., a[o_a+N-1]` and multiply it elementwise with
 * `b[o_b], b[o_b+1], ..., b[o_b+N-1]`, then sum the products.
 *
 * ### Mapping to parameters
 * - `aOff` (`o_a` above) — starting index of the slice within `a`.
 * - `bOff` (`o_b` above) — starting index of the slice within `b`.
 * - `len` (`N` above) — number of elements to include from each slice.
 *
 * Preconditions (must be implemented by callers):
 * ```text
 * 0 <= aOff, 0 <= bOff, 0 <= len
 * aOff + len <= a.size
 * bOff + len <= b.size
 * ```
 *
 * ### Examples
 * - If `a = [2, 4, 6, 8]`, `b = [1, 3, 5, 7]`, `aOff = 1`, `bOff = 0`, `len = 3`, then
 *   `sum_(i=0)^2 a_(1+i) b_(0+i) = 4·1 + 6·3 + 8·5`.
 * - Calling the infix form `a dotProduct b` is equivalent to the sliced form with `aOff = 0`, `bOff = 0`, and
 *   `len = min(a.size, b.size)`.
 *
 * ### Notes on accuracy vs. throughput
 * - [dotProduct] is typically fastest and accurate enough in many cases.
 * - [dotProductKahan] is more robust for ill-conditioned inputs (large cancellations), at a small throughput cost, by
 *   maintaining per-lane `(sum, comp)` and performing a scalar Kahan reduction across lanes at the end.
 */
internal interface DotProduct {
    /** Computes `this · that` up to `min(|this|, |that|)`. */
    infix fun DoubleArray.dotProduct(that: DoubleArray): Double

    /** Computes `Σ_{i=0}^{len-1} a[aOff + i] * b[bOff + i]` on contiguous slices of `a` and `b`. */
    fun dotProduct(a: DoubleArray, aOff: Int, len: Int, b: DoubleArray, bOff: Int): Double

    /**
     * Computes `this · that` with Kahan compensation per SIMD lane.
     *
     * ## References
     * 1. W. Kahan, “Pracniques: further remarks on reducing truncation errors,” Commun. ACM, vol. 8, no. 1, p. 40,
     *   Jan. 1965, doi: 10.1145/363707.363723.
     */
    infix fun DoubleArray.dotProductKahan(that: DoubleArray): Double

    /**
     * Sliced Kahan-compensated dot product on contiguous subarrays of `a` and `b`.
     *
     * Computes `Σ_{i=0}^{len-1} a[aOff + i] * b[bOff + i]` with Kahan compensation.
     *
     * ## References
     * 1. W. Kahan, “Pracniques: further remarks on reducing truncation errors,” Commun. ACM, vol. 8, no. 1, p. 40,
     *   Jan. 1965, doi: 10.1145/363707.363723.
     */
    fun dotProductKahan(a: DoubleArray, aOff: Int, len: Int, b: DoubleArray, bOff: Int): Double
}

/**
 * JVM-specific implementation backed by the **JDK Vector API** (`jdk.incubator.vector`).
 *
 * ### Vector API primer (for maintainers)
 * - A **species** encodes the SIMD shape (element type and lane count).
 *   `SPECIES_PREFERRED` selects the widest supported vector on the running platform/CPU.
 * - Loading: `DoubleVector.fromArray(species, array, offset)` reads `lanes` doubles starting at `offset`.
 * - Masked loading: `fromArray(species, array, offset, mask)` reads a **partial** last block (tail).
 * - FMA: `va.fma(vb, acc) = va * vb + acc` (fused multiply-add).
 * - Horizontal reduction: `reduceLanes(ADD)` sums lanes to a scalar.
 *
 * ### Numerical notes
 * - `dotProduct` is fast and accurate on many inputs but still uses a single (vector) accumulator.
 * - `dotProductKahan` keeps **two** per-lane vectors: `sum` and `comp` (compensation).
 *   Each step applies Kahan’s update:
 *     1. `y  = prod - comp`
 *     2. `t  = sum + y`
 *     3. `comp' = (t - sum) - y`
 *     4. `sum'  = t`
 *   Finally, lanes are merged with a **scalar Kahan** to preserve compensation across lanes.
 */
internal class DotProductImpl(
    private val species: VectorSpecies<Double>,
    /** Number of lanes for the chosen species (SIMD width). */
    private val lanes: Int
) : DotProduct {

    override infix fun DoubleArray.dotProduct(that: DoubleArray): Double =
        dotProduct(this, 0, minOf(this.size, that.size), that, 0)

    override fun dotProduct(
        a: DoubleArray, aOff: Int, len: Int,
        b: DoubleArray, bOff: Int
    ): Double {
        if (len <= 0) return 0.0
        checkBounds(a.size, aOff, len)
        checkBounds(b.size, bOff, len)

        // Vectorized accumulation over full-width blocks.
        val (next, acc0) = blockReduce(
            a, aOff, b, bOff,
            len = len,
            acc0 = DoubleVector.zero(species)
        ) { va, vb, acc -> va.fma(vb, acc) }

        // Process the (possibly partial) masked tail, then horizontally sum SIMD lanes.
        return accumulateMaskedTail(a, aOff, b, bOff, next, len, acc0)
            .reduceLanes(VectorOperators.ADD)
    }

    override infix fun DoubleArray.dotProductKahan(that: DoubleArray): Double =
        dotProductKahan(this, 0, minOf(this.size, that.size), that, 0)

    override fun dotProductKahan(
        a: DoubleArray, aOff: Int, len: Int,
        b: DoubleArray, bOff: Int
    ): Double {
        if (len <= 0) return 0.0
        checkBounds(a.size, aOff, len)
        checkBounds(b.size, bOff, len)

        val zero = DoubleVector.zero(species)
        val state0 = KahanState(sum = zero, comp = zero)

        // Vectorized Kahan over full-width blocks.
        val (next, state1) = kahanBlockReduce(
            a, aOff, b, bOff,
            len = len,
            state0 = state0
        )

        // Apply one masked Kahan step to the tail (if any), then horizontally reduce with scalar Kahan.
        val state2 = kahanMaskedTail(a, aOff, b, bOff, next = next, len = len, state = state1)
        return horizontalKahan(state2.sum, state2.comp)
    }

    /**
     * Applies one masked Kahan update for the final **partial** block (if `next < len`).
     *
     * Loads using a mask so we keep the final work vectorized instead of falling back to scalar.
     * `len` is the total range length from the current start (i.e., we index `[0, len)`).
     */
    private fun kahanMaskedTail(
        a: DoubleArray, aOff: Int,
        b: DoubleArray, bOff: Int,
        next: Int, len: Int,
        state: KahanState
    ): KahanState {
        require(next in 0..len)
        if (next >= len) return state

        val m = species.indexInRange(next, len)
        val va = DoubleVector.fromArray(species, a, aOff + next, m)
        val vb = DoubleVector.fromArray(species, b, bOff + next, m)
        val prod = va.mul(vb)
        return kahanUpdate(state.sum, state.comp, prod) // consistent (sum, comp) ordering
    }

    /**
     * Vectorized Kahan over full-width blocks (no mask), returning the next index and state.
     * Complexity: `O(len)`. No heap allocations on the hot path; JIT typically scalarizes `KahanState`.
     */
    private fun kahanBlockReduce(
        a: DoubleArray, aOff: Int,
        b: DoubleArray, bOff: Int,
        len: Int,
        state0: KahanState
    ): Pair<Int, KahanState> {
        var i = 0
        var sum = state0.sum
        var comp = state0.comp
        val limit = len - (len % lanes)

        while (i < limit) {
            val va = DoubleVector.fromArray(species, a, aOff + i)
            val vb = DoubleVector.fromArray(species, b, bOff + i)
            val prod = va.mul(vb)
            val (s, c) = kahanUpdate(sum, comp, prod)
            sum = s; comp = c
            i += lanes
        }
        return i to KahanState(sum, comp)
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
     * Reduces full-width SIMD blocks with a custom step `(va, vb, acc) -> acc'`.
     * The final partial block is **not** handled here.
     */
    private inline fun blockReduce(
        a: DoubleArray, aOff: Int,
        b: DoubleArray, bOff: Int,
        len: Int,
        acc0: DoubleVector,
        crossinline step: (DoubleVector, DoubleVector, DoubleVector) -> DoubleVector
    ): Acc {
        var i = 0
        var acc = acc0
        val limit = len - (len % lanes)
        while (i < limit) {
            val va = DoubleVector.fromArray(species, a, aOff + i)
            val vb = DoubleVector.fromArray(species, b, bOff + i)
            acc = step(va, vb, acc)
            i += lanes
        }
        return Acc(i, acc)
    }

    /**
     * Processes the **masked tail** (if any) with FMA: `acc' = va * vb + acc`.
     * No-op when `next >= len`.
     */
    private fun accumulateMaskedTail(
        a: DoubleArray, aOff: Int,
        b: DoubleArray, bOff: Int,
        next: Int, len: Int,
        acc0: DoubleVector
    ): DoubleVector {
        require(next >= 0 && len >= 0 && next <= len)
        if (next >= len) return acc0

        val m = species.indexInRange(next, len)
        val va = DoubleVector.fromArray(species, a, aOff + next, m)
        val vb = DoubleVector.fromArray(species, b, bOff + next, m)
        return va.fma(vb, acc0)
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

    /** Basic slice bounds check: `[off, off + len)` must lie within `[0, size)`. */
    private fun checkBounds(size: Int, off: Int, len: Int) {
        require(off >= 0 && len >= 0 && off + len <= size) {
            "Slice out of bounds: off=$off len=$len size=$size"
        }
    }

    /** Loop result for block reduction (next index, vector accumulator). */
    private data class Acc(val index: Int, val sum: DoubleVector)

    /** Kahan per-lane state (sum and compensation). */
    private data class KahanState(val sum: DoubleVector, val comp: DoubleVector)
}
