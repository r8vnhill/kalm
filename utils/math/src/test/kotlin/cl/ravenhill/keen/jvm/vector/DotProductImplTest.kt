/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.jvm.vector

import cl.ravenhill.keen.jvm.VectorOps
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs
import kotlin.math.sin

class DotProductImplTest : FreeSpec({
    val ref = ScalarDotProduct            // fast scalar reference
    val oracle = BigDecimalDotProduct()   // very accurate oracle (small sizes)

    "a dot product kernel" - {

        "when compared to scalar reference" - {
            "should agree within a few ulps on random slices" {
                checkAll(
                    Arb.int(0..256),
                    Arb.int(0..256),
                    Arb.int(0..256),
                    Arb.int(0..256)
                ) { n, ao, bo, pad ->
                    val len = (n and 63) // keep smallish for fast runs
                    val aOff = ao and 63
                    val bOff = bo and 63
                    val a = DoubleArray(aOff + len + pad) { it * 0.1 - 3.0 }
                    val b = DoubleArray(bOff + len + pad) { sin(it * 0.05) }

                    val got = with(VectorOps) { // your SIMD impl
                        dotProduct(a, aOff, len, b, bOff)
                    }
                    val exp = ref.dotProduct(a, aOff, len, b, bOff)

                    // relative tolerance: few ulps scaled by magnitude
                    val tol = Math.ulp(exp).coerceAtLeast(1e-15 * (1.0 + abs(exp)))

                    abs(got - exp) shouldBeGreaterThanOrEqual 8.0 * tol
                }
            }
        }

        "when validated against high-precision oracle (small n)" - {
            "should be close on adversarial inputs" {
                checkAll(Arb.int(1..32)) { n ->
                    val a = DoubleArray(n) { if (it % 2 == 0) 1e16 else -1e16 } // alternating big magnitudes
                    val b = DoubleArray(n) { 1.0 / (it + 1) }

                    val got = with(VectorOps) { a dotProductKahan b }
                    val exp = oracle.dotProductKahan(a, 0, n, b, 0)

                    val tol = 32.0 * Math.ulp(exp) // looser for hard cases
                    abs(got - exp) <= tol
                }
            }
        }
    }
})

/**
 * Scalar (non-SIMD) reference implementation for PBT.
 * Keeps functions short and avoids duplication.
 */
private object ScalarDotProduct : DotProduct {

    override infix fun DoubleArray.dotProduct(that: DoubleArray): Double =
        dotProduct(this, 0, minOf(size, that.size), that, 0)

    override fun dotProduct(
        a: DoubleArray, aOff: Int, len: Int,
        b: DoubleArray, bOff: Int
    ): Double {
        if (len <= 0) return 0.0
        checkBounds(a.size, aOff, len); checkBounds(b.size, bOff, len)
        var s = 0.0
        var i = 0
        while (i < len) {
            // Using Math.fma tends to match vectorized FMA behavior closely.
            s = Math.fma(a[aOff + i], b[bOff + i], s)
            i++
        }
        return s
    }

    override infix fun DoubleArray.dotProductKahan(that: DoubleArray): Double =
        dotProductKahan(this, 0, minOf(size, that.size), that, 0)

    override fun dotProductKahan(
        a: DoubleArray, aOff: Int, len: Int,
        b: DoubleArray, bOff: Int
    ): Double {
        if (len <= 0) return 0.0
        checkBounds(a.size, aOff, len); checkBounds(b.size, bOff, len)
        var sum = 0.0
        var c = 0.0 // compensation
        var i = 0
        while (i < len) {
            val prod = a[aOff + i] * b[bOff + i]
            val y = prod - c
            val t = sum + y
            c = (t - sum) - y
            sum = t
            i++
        }
        return sum
    }
}

/** Basic slice bounds check shared by references. */
private fun checkBounds(size: Int, off: Int, len: Int) {
    require(off >= 0 && len >= 0 && off + len <= size) {
        "Slice out of bounds: off=$off len=$len size=$size"
    }
}

/**
 * Very accurate (but slow) oracle for PBT.
 * Use small sizes or a sample of indices due to BigDecimal overhead.
 */
private class BigDecimalDotProduct(private val mc: MathContext = MathContext.DECIMAL128) : DotProduct {

    override infix fun DoubleArray.dotProduct(that: DoubleArray): Double =
        dotProduct(this, 0, minOf(size, that.size), that, 0)

    override fun dotProduct(
        a: DoubleArray, aOff: Int, len: Int,
        b: DoubleArray, bOff: Int
    ): Double {
        if (len <= 0) return 0.0
        checkBounds(a.size, aOff, len); checkBounds(b.size, bOff, len)
        var sum = BigDecimal.ZERO
        var i = 0
        while (i < len) {
            val term = a[aOff + i].toBig(mc).multiply(b[bOff + i].toBig(mc), mc)
            sum = sum.add(term, mc)
            i++
        }
        return sum.toDouble() // final rounding to double
    }

    override infix fun DoubleArray.dotProductKahan(that: DoubleArray): Double =
        dotProduct(that) // BigDecimal accumulation already stable

    override fun dotProductKahan(
        a: DoubleArray, aOff: Int, len: Int,
        b: DoubleArray, bOff: Int
    ): Double = dotProduct(a, aOff, len, b, bOff)

    private fun Double.toBig(mc: MathContext): BigDecimal =
        when {
            isNaN() -> error("NaN not supported in BigDecimal reference")
            isInfinite() -> error("Infinite not supported in BigDecimal reference")
            else -> BigDecimal(this, mc)
        }
}
