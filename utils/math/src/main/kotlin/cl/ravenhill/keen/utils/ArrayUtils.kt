/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.utils

/**
 * Validates that a slice `[offset, offset + length)` lies within `[0, size)`.
 *
 * @throws IllegalArgumentException if the slice is out of bounds.
 */
internal fun requireSliceInBounds(size: Int, offset: Int, length: Int) {
    require(offset >= 0 && length >= 0 && offset + length <= size) {
        "Slice out of bounds: offset=$offset, length=$length, size=$size"
    }
}

/**
 * Represents a view into a subarray of a larger `DoubleArray` without making a copy.
 *
 * @property arr The underlying array being viewed.
 * @property offset The offset within the array where the subarray begins.
 */
@Suppress("ArrayInDataClass")
internal data class DoubleArraySlice(val arr: DoubleArray, val offset: Int)
