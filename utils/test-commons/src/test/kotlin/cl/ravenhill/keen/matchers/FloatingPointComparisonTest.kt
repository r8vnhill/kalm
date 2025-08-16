/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.matchers

import cl.ravenhill.keen.generators.finiteDouble
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.Exhaustive
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.longs
import java.lang.Double.doubleToRawLongBits
import java.lang.Double.longBitsToDouble
import java.lang.Double.max
import kotlin.math.abs
import kotlin.math.nextDown
import kotlin.math.nextUp

// ---------- small helpers (reusable) ----------

// Tolerance generators (strictly non-negative).
private val arbAbsTol = Arb.double(0.0..1e-3, includeNonFiniteEdgeCases = false).map { it.coerceAtLeast(0.0) }
private val arbRelTol = Arb.double(0.0..1e-6, includeNonFiniteEdgeCases = false).map { it.coerceAtLeast(0.0) }

// ---- helpers that mirror the matcher exactly ----

private fun normalizeZero(x: Double): Double = if (x == 0.0) 0.0 else x

private fun orderedBits(bits: Long): Long =
    if (bits < 0) Long.MIN_VALUE - bits else Long.MIN_VALUE + bits

/** Unsigned ULP distance consistent with the matcher. */
private fun ulpDistanceMatcher(a0: Double, b0: Double): ULong {
    val a = normalizeZero(a0)
    val b = normalizeZero(b0)
    if (!a.isFinite() || !b.isFinite()) return ULong.MAX_VALUE

    val oa = orderedBits(doubleToRawLongBits(a)).toULong()
    val ob = orderedBits(doubleToRawLongBits(b)).toULong()
    return if (oa >= ob) oa - ob else ob - oa
}

private fun withinUlpsMatcher(a: Double, b: Double, maxUlps: Long): Boolean {
    if (maxUlps < 0) return false
    val dist = ulpDistanceMatcher(a, b)
    return dist <= maxUlps.toULong()
}

// Avoid zero-cross for “steps == maxUlps” passing properties.
private fun nonNegative(x: Double) = if (x < 0.0) -x else x

private fun Double.stepUlpsUp(n: Int): Double {
    var x = this
    repeat(n) { x = x.nextUp() }
    return x
}

private fun ordered(bits: Long): Long =
    if (bits >= 0) bits or Long.MIN_VALUE else bits.inv()

private fun unordered(ord: Long): Long =
    if (ord < 0) ord and Long.MAX_VALUE else ord.inv()

private fun shiftByUlpsExact(x: Double, ulps: Long): Double {
    val xn   = normalizeZero(x)
    val bits = doubleToRawLongBits(xn)
    val o    = ordered(bits)

    // Small shifts (<= 10) in your tests won’t overflow |o + ulps|.
    val targetO = o + ulps

    val targetBits = unordered(targetO)
    return longBitsToDouble(targetBits)
}

class FloatingPointComparisonTest : FreeSpec({

    // ---------- beCloseTo (mixed absolute/relative) ----------

    "a mixed tolerance matcher (beCloseTo)" - {

        "when values are identical" - {
            "should pass with default tolerances" {
                checkAll(Arb.finiteDouble()) { v -> v.shouldBeCloseTo(v) }
            }
            "should pass with zero tolerances" {
                checkAll(Arb.finiteDouble()) { v ->
                    v.shouldBeCloseTo(v, absoluteTolerance = 0.0, relativeTolerance = 0.0)
                }
            }
        }

        "when values differ less than absolute tolerance near zero" {
            1e-12.shouldBeCloseTo(0.0, absoluteTolerance = 1e-9, relativeTolerance = 0.0)
        }

        "when values differ but relative tolerance covers the error at large magnitude" {
            val e = 1_000_000.0
            val a = e * (1.0 + 5e-7) // 0.5 ppm
            a.shouldBeCloseTo(e, absoluteTolerance = 1e-9, relativeTolerance = 1e-6)
        }

        "when values differ beyond both tolerances should fail" {
            beCloseTo(1.0, absoluteTolerance = 1e-9, relativeTolerance = 1e-9)
                .test(1.1).passed().shouldBeFalse()
        }

        "when using the negative matcher should pass for far values" {
            10.0.shouldNotBeCloseTo(12.0, absoluteTolerance = 1e-6, relativeTolerance = 1e-6)
        }

        "when expected is not finite should throw" - {
            withData(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY) { bad ->
                shouldThrow<IllegalArgumentException> { beCloseTo(bad) }
            }
        }

        "when tolerances are negative should throw" {
            shouldThrow<IllegalArgumentException> { beCloseTo(0.0, absoluteTolerance = -1e-9) }
            shouldThrow<IllegalArgumentException> { beCloseTo(0.0, relativeTolerance = -1e-9) }
        }

        "property: passes iff rule holds (generated tolerances)" {
            checkAll(Arb.finiteDouble(), Arb.finiteDouble(), arbAbsTol, arbRelTol) { a, e, absTol, relTol ->
                val scale = max(1.0, max(abs(a), abs(e)))
                val rule = abs(a - e) <= max(absTol, relTol * scale)
                beCloseTo(e, absTol, relTol).test(a).passed() shouldBe rule
            }
        }
    }

    // ---------- beCloseToUlps (ULP distance) ----------

    "a ULP matcher (beCloseToUlps)" - {

        "when values differ by exactly 1 ULP" - {
            "should pass with maxUlps >= 1" {
                val e = 1.0
                e.nextUp().shouldBeCloseToUlps(e, maxUlps = 1)
            }
            "should fail with maxUlps = 0" {
                val e = 1.0
                beCloseToUlps(e, maxUlps = 0).test(e.nextUp()).passed().shouldBeFalse()
            }
        }

        "when values differ by multiple ULPs" - {
            "should fail if maxUlps is smaller than the distance" {
                checkAll(Exhaustive.longs(1L..10L), Arb.finiteDouble()) { maxUlps, base ->
                    val moved = base.stepUlpsUp((maxUlps + 1).toInt())
                    moved.shouldNotBeCloseToUlps(base, maxUlps = maxUlps)
                }
            }
            "should pass if maxUlps covers the distance (no zero-cross)" {
                checkAll(Exhaustive.longs(0L..10L), Arb.finiteDouble()) { maxUlps, raw ->
                    val base = nonNegative(raw)
                    val moved = base.stepUlpsUp(maxUlps.toInt())
                    moved.shouldBeCloseToUlps(base, maxUlps = maxUlps)
                }
            }
        }

        "when comparing across the sign boundary should handle signed zeros" {
            (-0.0).shouldBeCloseToUlps(0.0, maxUlps = 1)
        }

        "when expected is not finite should throw" - {
            withData(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY) { bad ->
                shouldThrow<IllegalArgumentException> { beCloseToUlps(bad) }
            }
        }

        "when maxUlps is negative should throw" {
            checkAll(Arb.long(min = Long.MIN_VALUE, max = -1)) { bad ->
                shouldThrow<IllegalArgumentException> { beCloseToUlps(1.0, maxUlps = bad) }
            }
        }

        "property: matcher agrees with computed ULP distance (zero-aware)" {
            checkAll(
                Exhaustive.longs(0L..10L),
                Arb.finiteDouble(),
                Arb.finiteDouble()
            ) { maxUlps, a, b ->
                val expected = withinUlpsMatcher(a, b, maxUlps)
                beCloseToUlps(b, maxUlps).test(a).passed() shouldBe expected
            }
        }

        "should pass if maxUlps covers the distance (no zero-cross)" {
            checkAll(Exhaustive.longs(0L..10L), Arb.finiteDouble()) { maxUlps, raw ->
                val base = if (raw < 0.0) -raw else raw  // avoid crossing zero
                val moved = generateSequence(base) { it.nextUp() }.drop(maxUlps.toInt()).first()
                moved.shouldBeCloseToUlps(base, maxUlps)
            }
        }

        "should fail if maxUlps is smaller than the distance (exact shifting)" {
            checkAll(Exhaustive.longs(1L..10L), Arb.finiteDouble()) { maxUlps, base ->
                val moved = shiftByUlpsExact(base, maxUlps + 1) // exact endpoint distance = maxUlps + 1
                moved.shouldNotBeCloseToUlps(base, maxUlps)
            }
        }
    }

    // ---------- sanity checks ----------

    "an overall sanity check" - {
        "should agree with a simple absolute bound when scale=1" {
            0.0.shouldBeCloseTo(5e-10, absoluteTolerance = 1e-9, relativeTolerance = 0.0)
        }
        "should reflect the computed scale term" {
            val e = 1e6
            val a = e + 2.0
            val rel = 1e-6
            e.shouldNotBeCloseTo(a, absoluteTolerance = 1e-9, relativeTolerance = rel)
        }
        "should treat two successive nextUp steps as ~2 ULPs around 1.0" {
            val e = 1.0
            val a = e.nextUp().nextUp()
            e.shouldBeCloseToUlps(a, maxUlps = 2)
            e.shouldNotBeCloseToUlps(a, maxUlps = 1)
        }
        "should treat nextDown as symmetric to nextUp for ULP comparison" {
            val e = 1.0
            val a = e.nextDown()
            e.shouldBeCloseToUlps(a, maxUlps = 1)
        }
    }
})
