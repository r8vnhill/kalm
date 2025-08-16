/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.platform

import jdk.incubator.vector.DoubleVector
import jdk.incubator.vector.VectorOperators

@JvmSpecific
public object VectorOps {
    // A preferred species is a species of maximal bit-size for the platform.
    private val species = DoubleVector.SPECIES_PREFERRED
    private val lanes = species.length()

    public infix fun DoubleArray.dotProduct(that: DoubleArray): Double {
        val elementCount = minOf(this.size, that.size)

        val zero = DoubleVector.zero(species)
        val (nextIndex: Int, vecAcc: DoubleVector) = accumulateBlocks(elementCount, that, initialAcc = zero)

        return reduceDotTail(vecAcc, nextIndex, elementCount, that)
    }

    /**
     * Computes the dot product using **compensated summation** (Kahan) to reduce rounding error.
     *
     * This implementation accumulates `{typst} sum_(i=1)^n a_i * b_i` with a running compensation term that cancels
     * part of the rounding error at each step.
     * It is typically **more accurate** than naive summation—especially for long vectors, mixed magnitudes, or
     * ill-conditioned inputs.
     *
     * ## When to use
     * - When inputs contain **widely varying magnitudes** or alternating signs, where naive accumulation suffers from
     *   large cancellation error.
     * - When **deterministic, higher-accuracy** accumulation is preferred over peak throughput.
     *
     * ## Notes
     * - Complexity is `{typst} O(min(|this|, |that|))`.
     * - If your platform provides FMA, a vectorized FMA-based dot can be both fast and accurate, but
     *   order-of-operations still differs; results may vary by a few ulps.
     *
     * ## References
     * - Kahan, W. (1965). *Pracniques: Further Remarks on Reducing Truncation Errors*. **Communications of the ACM**, 8(1), 40. DOI: 10.1145/363707.363723
     * - Higham, N. J. (2002). *Accuracy and Stability of Numerical Algorithms* (2nd ed.). SIAM.
     * - Goldberg, D. (1991). *What Every Computer Scientist Should Know About Floating-Point Arithmetic*. **ACM Computing Surveys**, 23(1), 5–48. DOI: 10.1145/103162.103163
     * - Ogita, T., Rump, S. M., & Oishi, S. (2005). *Accurate Sum and Dot Product*. **SIAM Journal on Scientific Computing**, 26(6), 1955–1988.
     * - Langlois, P., & Louvet, N. (2008). *Detecting Numerical Instabilities with Kahan’s Algorithm*. **Theoretical Computer Science**, 407(1–3), 42–53.
     *
     * @param that The second vector in the inner product.
     * @return The Kahan-compensated dot product `Σ (this[i] * that[i])` up to `min(size(this), size(that))`.
     */
    public infix fun DoubleArray.dotProductKahan(that: DoubleArray): Double {
        val n = minOf(this.size, that.size)
        var sum = 0.0
        var c = 0.0 // compensation for lost low-order bits
        var i = 0
        while (i < n) {
            val prod = this[i] * that[i]
            val y = prod - c
            val t = sum + y
            c = (t - sum) - y
            sum = t
            i++
        }
        return sum
    }

    private fun DoubleArray.accumulateBlocks(
        elementCount: Int,
        vectorB: DoubleArray,
        initialAcc: DoubleVector
    ): BlockAccumulation {
        var index = 0
        var acc = initialAcc

        // Process in vector-width chunks; leave tail (if any) to a scalar loop.
        // Example: if count=103 and lanes=8, limit=96 ⇒ process [startIndex, 96) here.
        val limit = elementCount - (elementCount % lanes)

        while (index < limit) {
            val partA = DoubleVector.fromArray(species, this, index)
            val partB = DoubleVector.fromArray(species, vectorB, index)
            acc = partA.fma(partB, acc)     // acc += partA * partB
            index += lanes
        }
        return BlockAccumulation(index, acc)
    }

    private fun DoubleArray.reduceDotTail(
        vecAcc: DoubleVector,
        nextIndex: Int,
        elementCount: Int,
        vectorB: DoubleArray
    ): Double {
        var acc = vecAcc.reduceLanes(VectorOperators.ADD)

        var idx = nextIndex
        while (idx < elementCount) {
            acc = MathCompat.fma(this[idx], vectorB[idx], acc)
            idx++
        }
        return acc
    }

    private data class BlockAccumulation(val nextIndex: Int, val accumulator: DoubleVector)
}
