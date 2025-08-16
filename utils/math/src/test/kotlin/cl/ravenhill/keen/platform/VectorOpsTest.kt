/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.platform

class VectorOpsTest {
}

/**
 * Scalar reference for dot product, intended for property-based tests.
 *
 * - Processes elements left-to-right up to `min(this.size, that.size)`.
 * - Uses [MathCompat.fma] for consistency with production code.
 *
 * ## Usage:
 * ```kotlin
 * val ref = a.dotProductRef(b)
 * val fast = a.dotProduct(b)
 * nearlyEquals(ref, fast) shouldBe true
 * ```
 *
 * @param that Other vector.
 * @return Sum of pairwise products.
 */
public infix fun DoubleArray.dotProductRef(that: DoubleArray): Double {
    val n = minOf(this.size, that.size)
    var acc = 0.0
    var i = 0
    while (i < n) {
        acc = MathCompat.fma(this[i], that[i], acc) // acc += a[i] * b[i]
        i++
    }
    return acc
}

public infix fun DoubleArray.dotProductKahan(that: DoubleArray): Double {
    val n = minOf(this.size, that.size)
    var sum = 0.0
    var c = 0.0 // compensation
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
