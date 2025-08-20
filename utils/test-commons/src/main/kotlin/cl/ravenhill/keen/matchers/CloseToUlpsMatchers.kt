/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.matchers

import cl.ravenhill.keen.utils.ActualD
import cl.ravenhill.keen.utils.ExpectedD
import cl.ravenhill.keen.utils.requireFinite
import cl.ravenhill.keen.utils.requireNonNegative
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.should
import io.kotest.matchers.shouldNot
import java.lang.Double.doubleToRawLongBits
import java.util.Locale
import kotlin.math.abs

/**
 * Creates a [Matcher] for verifying whether an [ActualD] value is within a given number of **Units in the Last Place
 * (ULPs)** of an [ExpectedD] value.
 *
 * ULP-based comparison is often more precise than absolute/relative tolerances when comparing floating-point numbers,
 * since it measures the difference in representable machine-level values.
 * This is particularly useful for ensuring numerical stability in algorithms sensitive to rounding error.
 *
 * The check passes if the bitwise distance between the two values, after sign-aware ordering, does not exceed
 * `maxUlps`.
 *
 * ## References
 * 1. B. Dawson, “Comparing floating point numbers, 2012 edition,” Random ASCII - Tech Blog of Bruce Dawson.
 *   Accessed: Aug. 16, 2025. (Online).
 *   Available: https://randomascii.wordpress.com/2012/02/25/comparing-floating-point-numbers-2012-edition/
 *
 * @param expected The expected [Double] value to compare against.
 * @param maxUlps Maximum tolerated difference in ULPs (default: `4`).
 * @return A [Matcher] that checks if an [ActualD] [Double] is within `maxUlps` of [expected].
 * @throws IllegalArgumentException if `expected` is not finite or `maxUlps` is negative.
 * @see withinUlps
 */
fun beCloseToUlps(
    expected: ExpectedD,
    maxUlps: Long = 4L
): Matcher<ActualD> {
    expected.requireFinite()
    maxUlps.requireNonNegative("maxUlps")

    return Matcher { actual ->
        val passed = withinUlps(actual, expected, maxUlps)
        MatcherResult(
            passed = passed,
            failureMessageFn = {
                String.format(
                    Locale.ROOT,
                    "Expected %.8g to be within %d ULPs of %.8g",
                    actual,
                    maxUlps,
                    expected
                )
            },
            negatedFailureMessageFn = {
                String.format(
                    Locale.ROOT,
                    "Expected %.8g NOT to be within %d ULPs of %.8g",
                    actual,
                    maxUlps,
                    expected
                )
            }
        )
    }
}

/**
 * Asserts that this [Double] is close to [expected] within a given number of ULPs (units in last place).
 *
 * This matcher is designed for floating-point comparisons where strict equality is too brittle.
 * The tolerance is expressed in ULPs rather than absolute/relative difference, making it robust near boundaries, very
 * small values, or very large values.
 *
 * ## Usage:
 * ```kotlin
 * val x = 1.0 + Double.MIN_VALUE
 * x.shouldBeCloseToUlps(1.0, maxUlps = 10) // passes if within 10 ULPs of 1.0
 * ```
 *
 * @receiver The [Double] under test.
 * @param expected The target value to compare against.
 * @param maxUlps The maximum difference, expressed as a number of ULP steps allowed.
 * @return The same [Double], for fluent chaining.
 * @see beCloseToUlps
 */
fun Double.shouldBeCloseToUlps(
    expected: Double,
    maxUlps: Long
): Double = apply { this should beCloseToUlps(expected, maxUlps) }

/**
 * Asserts that this [Double] is close to [expected] within a default tolerance of 4 ULPs.
 *
 * Provides a concise infix form for common cases where a small tolerance is enough.
 *
 * ## Usage:
 * ```kotlin
 * val result = 0.1 + 0.2
 * result shouldBeCloseToUlps 0.3  // uses default maxUlps = 4
 * ```
 *
 * @receiver The [Double] under test.
 * @param expected The target value to compare against.
 * @return The same [Double], for fluent chaining.
 * @see beCloseToUlps
 */
infix fun Double.shouldBeCloseToUlps(expected: Double): Double =
    this.shouldBeCloseToUlps(expected, 4L)

/**
 * Asserts that this [ActualD] value is **not** within [maxUlps] **ULPs** of [expected].
 *
 * @receiver The [ActualD] value under test.
 * @param expected The [ExpectedD] reference value.
 * @param maxUlps Maximum allowed ULP distance (non-negative, default `4`).
 * @return The receiver [ActualD], to allow fluent chaining.
 * @throws [IllegalArgumentException] if `expected` is not finite or `maxUlps` is negative.
 * @see beCloseToUlps
 */
fun ActualD.shouldNotBeCloseToUlps(
    expected: ExpectedD,
    maxUlps: Long = 4L
): ActualD = apply { this shouldNot beCloseToUlps(expected, maxUlps) }

/**
 * Normalizes signed zero so that both `+0.0` and `-0.0` are treated as `+0.0`.
 *
 * IEEE-754 encodes `+0.0` and `-0.0` with different bit patterns, which can falsely inflate ULP distances near zero.
 * Normalizing zeros ensures that equality and ULP computations treat both as the same value.
 *
 * @param x A [Double] that may be `+0.0` or `-0.0`.
 * @return `0.0` if `x` is either signed zero; otherwise returns `x` unchanged.
 */
private fun normalizeZero(x: Double): Double = if (x == 0.0) 0.0 else x

/**
 * Maps IEEE-754 [Double] raw bits to a **monotonically increasing** signed integer domain suitable for ULP distance
 * calculations.
 *
 * In the raw bit space, negative numbers compare “backwards” relative to their numeric order.
 * This mapping (adapted from standard ULP-comparison techniques) produces a total order consistent with numeric
 * ordering, enabling simple integer differences to represent ULP distances.
 *
 * Formally, for a 64-bit pattern `bits`:
 * - If `bits` encodes a negative number (`bits < 0` as a signed long), return `Long.MIN_VALUE - bits`.
 * - Otherwise return `Long.MIN_VALUE + bits`.
 *
 * @param bits Raw 64-bit encoding from `Double.doubleToRawLongBits`.
 * @return A signed [Long] that preserves numeric order for ULP distance math.
 */
private fun orderedBits(bits: Long): Long =
    if (bits < 0) Long.MIN_VALUE - bits else Long.MIN_VALUE + bits

/**
 * Compares two floating-point numbers for approximate equality by their distance in **Units in the Last Place (ULPs)**.
 *
 * Unlike absolute/relative tolerances, this method measures how many representable [Double] values lie between [actual]
 * and [expected].
 * It:
 *
 * 1. **Normalizes signed zeros** so `+0.0 ≡ -0.0`.
 * 2. Reinterprets both values’ IEEE-754 encodings with [doubleToRawLongBits].
 * 3. Applies [orderedBits] to get a **monotone integer order** across signs.
 * 4. Takes the absolute difference of those integers as the ULP distance.
 *
 * The check passes iff:
 * ```
 * ulpDistance(actual, expected) ≤ maxUlps
 * ```
 *
 * ## Note
 * Non-finite inputs (NaN, ±∞) are **not** comparable via ULPs; this function returns `false` in those cases.
 *
 * @param actual The tested [ActualD] value.
 * @param expected The reference [ExpectedD] value.
 * @param maxUlps The maximum allowed ULP distance (non-negative).
 * @return `true` if the ULP distance between [actual] and [expected] is ≤ [maxUlps]; `false` otherwise.
 */
private fun withinUlps(actual: ActualD, expected: ExpectedD, maxUlps: Long): Boolean {
    if (!actual.isFinite() || !expected.isFinite()) return false

    val a = normalizeZero(actual)
    val e = normalizeZero(expected)

    val ab = doubleToRawLongBits(a)
    val eb = doubleToRawLongBits(e)

    val oa = orderedBits(ab)
    val oe = orderedBits(eb)

    val dist = abs(oa - oe)
    return dist <= maxUlps
}
