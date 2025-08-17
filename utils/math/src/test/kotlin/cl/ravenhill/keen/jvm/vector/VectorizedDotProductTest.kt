/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.jvm.vector

import cl.ravenhill.keen.jvm.VectorOps
import cl.ravenhill.keen.matchers.shouldBeCloseTo
import cl.ravenhill.keen.matchers.shouldBeFinite
import io.kotest.core.spec.style.FreeSpec
import java.math.BigDecimal
import java.math.MathContext

class VectorizedDotProductTest : FreeSpec({

    val ref = ScalarDotProduct
    val oracle = BigDecimalDotProduct()

    fun makeArray(total: Int, off: Int, init: (Int) -> Double): DoubleArray =
        DoubleArray(total) { i -> init(i) }

    with(VectorOps) {
        "a dot product kernel" - {
            "when length is zero" - {
                "should return 0.0" {
                    (doubleArrayOf() dotProduct doubleArrayOf())
                        .shouldBeFinite()
                        .shouldBeCloseTo(0.0, absoluteTolerance = 0.0, relativeTolerance = 0.0)
                }
            }

            "when compared to scalar reference" - {
                TODO()
//            "should agree within a few ulps on random slices" {
//                checkAll(
//                    Arb.int(0..256), // n
//                    Arb.int(0..256), // aOff
//                    Arb.int(0..256), // bOff
//                    Arb.int(0..256)  // pad
//                ) { n, ao, bo, pad ->
//                    val len  = (n and 63)             // pequeño para runs rápidos
//                    val aOff = (ao and 63)
//                    val bOff = (bo and 63)
//                    val totalA = aOff + len + pad
//                    val totalB = bOff + len + pad
//
//                    val a = makeArray(totalA, aOff) { i -> i * 0.1 - 3.0 }
//                    val b = makeArray(totalB, bOff) { i -> sin(i * 0.05) }
//
//                    val got = with(VectorOps) { dotProduct(a, aOff, len, b, bOff) }
//                    val exp = ref.dotProduct(a, aOff, len, b, bOff)
//
//                    // ULP budget conservador (evita flakes por orden de sumas distintas)
//                    got.shouldBeCloseToUlps(exp, maxUlps = 32)
//                }
//            }
//
//            "should agree by mixed tolerance with magnitude-aware scaling" {
//                checkAll(
//                    Arb.int(1..256),
//                    Arb.int(0..64),
//                    Arb.int(0..64)
//                ) { len, aOff, bOff ->
//                    val pad = Random.nextInt(0, 32)
//                    val totalA = aOff + len + pad
//                    val totalB = bOff + len + pad
//
//                    val a = makeArray(totalA, aOff) { i -> (i - aOff) * 1e-3 }
//                    val b = makeArray(totalB, bOff) { i -> sin((i - bOff) * 1e-2) }
//
//                    val got = with(VectorOps) { dotProduct(a, aOff, len, b, bOff) }
//                    val exp = ref.dotProduct(a, aOff, len, b, bOff)
//
//                    val ulp = Math.ulp(exp).coerceAtLeast(1e-18)
//                    val absTol = 8.0 * ulp
//                    val relTol = 1e-12
//
//                    got.shouldBeCloseTo(exp, absoluteTolerance = absTol, relativeTolerance = relTol)
//                }
//            }
            }

            "when validated against high-precision oracle (small n)" - {
                TODO()
//            "should be close on adversarial cancellation inputs" {
//                checkAll(Arb.int(1..32)) { n ->
//                    // Alternan magnitudes grandes para inducir cancelación
//                    val a = DoubleArray(n) { if (it % 2 == 0) 1e16 else -1e16 }
//                    val b = DoubleArray(n) { 1.0 / (it + 1) }
//
//                    val got = with(VectorOps) { a dotProductKahan b }
//                    val exp = oracle.dotProductKahan(a, 0, n, b, 0)
//
//                    // Tolerancia algo más laxa para casos duros
//                    val ulp = Math.ulp(exp).coerceAtLeast(1e-18)
//                    val absTol = 64.0 * ulp
//                    val relTol = 1e-12
//
//                    got.shouldBeCloseTo(exp, absoluteTolerance = absTol, relativeTolerance = relTol)
//                }
//            }
            }

            "when stress-tested with sign/magnitude mixtures" - {
                TODO()
//            "should keep relative error bounded" {
//                checkAll(Arb.int(16..128)) { n ->
//                    val a = DoubleArray(n) { k ->
//                        val s = if (k % 3 == 0) -1.0 else 1.0
//                        s * when (k % 4) {
//                            0 -> 1e-12
//                            1 -> 1e-6
//                            2 -> 1.0
//                            else -> 1e6
//                        }
//                    }
//                    val b = DoubleArray(n) { k ->
//                        val s = if (k % 2 == 0) 1.0 else -1.0
//                        s * (1.0 / (1 + k))
//                    }
//
//                    val got = with(VectorOps) { a dotProductKahan b }
//                    val exp = ref.dotProductKahan(a, 0, n, b, 0)
//
//                    // Cota relativa (si exp ~ 0, cae a absoluta)
//                    val relTol = 5e-12
//                    val absTol = 1e-12 * (1.0 + abs(exp))
//                    (abs(got - exp) <= absTol || abs(got - exp) <= relTol * (1.0 + abs(exp))).shouldBeTrue()
//                }
//            }
            }
        }
    }
})

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

private fun checkBounds(size: Int, off: Int, len: Int) {
    require(off >= 0 && len >= 0 && off + len <= size) {
        "Slice out of bounds: off=$off len=$len size=$size"
    }
}

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
