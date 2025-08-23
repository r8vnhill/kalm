/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.utils

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

internal fun requireSlicesInBounds(vararg slices: DoubleArraySlice, length: Int) {
    for (slice in slices) {
        require(slice.offset >= 0 && length >= 0 && slice.offset + length <= slice.arr.size) {
            "Slice out of bounds: $slice, length=$length"
        }
    }
}

public data class DoubleArraySlice(val arr: DoubleArray, val offset: Int) {

    public operator fun get(index: Int): Double = arr[index + offset]

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DoubleArraySlice

        if (offset != other.offset) return false
        if (!arr.contentEquals(other.arr)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = offset
        result = 31 * result + arr.contentHashCode()
        return result
    }
}
