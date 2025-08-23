/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.jvm.vector.dot

import cl.ravenhill.knob.jvm.JvmSpecific
import cl.ravenhill.knob.utils.DoubleArraySlice
import jdk.incubator.vector.DoubleVector
import jdk.incubator.vector.VectorSpecies

/**
 * Base abstraction for vectorized dot product implementations using the JDK Vector API.
 *
 * This class provides shared utilities for concrete dot product implementations, including helper methods for handling
 * tails of arrays that are not aligned to the vector width.
 *
 * @property species The [VectorSpecies] used to create and manipulate [DoubleVector] instances.
 *   This determines the vector width and alignment used in computations.
 */
@JvmSpecific
internal open class DotProductBase(private val species: VectorSpecies<Double>) {

    /**
     * Represents a pair of subarray views that are processed together in vectorized operations.
     */
    protected typealias Views = Pair<DoubleArraySlice, DoubleArraySlice>

    /**
     * Loads a masked tail (if any) and combines its vectors.
     *
     * - Returns [onEmpty] when `next >= len`.
     * - Otherwise builds the mask and loads `va`/`vb` once, then calls [combine].
     *
     * Kept as an extension on [Views] to avoid passing multiple parameters explicitly.
     *
     * @param next The starting index of the tail.
     * @param len The total logical length of the slice.
     * @param onEmpty A function that is returned when no tail is present.
     * @param combine A function that consumes the loaded vectors `va` and `vb`.
     * @return The result of [combine] when a tail exists, or [onEmpty] otherwise.
     * @throws IllegalArgumentException if [next] is not within the range `0..len`.
     */
    protected inline fun <R> Views.maskedTail(
        next: Int,
        len: Int,
        crossinline onEmpty: () -> R,
        crossinline combine: (va: DoubleVector, vb: DoubleVector) -> R
    ): R {
        require(next in 0..len) { "next=$next must be in 0..$len" }
        if (next >= len) return onEmpty()

        val (a, b) = this
        val m = species.indexInRange(next, len)
        val va = DoubleVector.fromArray(species, a.arr, a.offset + next, m)
        val vb = DoubleVector.fromArray(species, b.arr, b.offset + next, m)
        return combine(va, vb)
    }
}
