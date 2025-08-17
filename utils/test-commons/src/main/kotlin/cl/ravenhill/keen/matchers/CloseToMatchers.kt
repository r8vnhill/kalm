/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.matchers

import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.should
import io.kotest.matchers.shouldNot
import java.lang.Double.doubleToRawLongBits
import java.util.*
import kotlin.math.abs
import kotlin.math.max

/**
 * Represents the expected (reference or target) floating-point value in a comparison.
 *
 * This typealias is used to clarify intent when writing matchers or assertions, distinguishing between the "ground
 * truth" or desired value and the actual result.
 */
private typealias Expected = Double

/**
 * Represents the actual floating-point value observed during execution or testing.
 *
 * This typealias complements [Expected] by making comparison signatures self-documenting, helping readers quickly see
 * which argument is the test subject and which is the reference.
 */
private typealias Actual = Double

/**
 * Creates a [Matcher] for verifying whether an [Actual] value is close enough to an [Expected] value, within the given
 * absolute and relative tolerances.
 *
 * The check passes if:
 * ```
 * |actual - expected| <= max(absoluteTolerance, relativeTolerance * scale)
 * ```
 * where `scale = max(1.0, max(|actual|, |expected|))`.
 *
 * This design ensures robustness against both:
 * - **Small magnitudes** (absolute tolerance dominates).
 * - **Large magnitudes** (relative tolerance dominates).
 *
 * @param expected The expected [Double] value.
 * @param absoluteTolerance Maximum tolerated absolute difference (default: `1e-9`).
 * @param relativeTolerance Maximum tolerated relative error (default: `1e-12`).
 * @return A [Matcher] for comparing an [Actual] [Double] against [expected].
 * @throws IllegalArgumentException if `expected` is not finite or tolerances are negative.
 * @see closeEnough
 */
fun beCloseTo(
    expected: Expected,
    absoluteTolerance: Double = 1e-9,
    relativeTolerance: Double = 1e-12
): Matcher<Actual> {
    expected.requireFinite()
    absoluteTolerance.requireNonNegative("Absolute tolerance")
    relativeTolerance.requireNonNegative("Relative tolerance")

    return Matcher { actual ->
        val passed = actual.isFinite() && closeEnough(actual, expected, absoluteTolerance, relativeTolerance)
        val diff = abs(actual - expected)
        val scale = max(1.0, max(abs(actual), abs(expected)))
        MatcherResult(
            passed = passed,
            failureMessageFn = {
                String.format(
                    Locale.ROOT,
                    "Expected %.8g to be close to %.8g (|Δ|=%.3e) with absoluteTolerance=%.1e, " +
                            "relativeTolerance=%.1e (scale=%.3e)",
                    actual, expected, diff, absoluteTolerance, relativeTolerance, scale
                )
            },
            negatedFailureMessageFn = {
                String.format(
                    Locale.ROOT,
                    "Expected %.8g NOT to be close to %.8g with absoluteTolerance=%.1e, relativeTolerance=%.1e",
                    actual, expected, absoluteTolerance, relativeTolerance
                )
            }
        )
    }
}

/**
 * Creates a [Matcher] for verifying whether an [Actual] value is within a given number of **Units in the Last Place
 * (ULPs)** of an [Expected] value.
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
 * @return A [Matcher] that checks if an [Actual] [Double] is within `maxUlps` of [expected].
 * @throws IllegalArgumentException if `expected` is not finite or `maxUlps` is negative.
 * @see withinUlps
 */
fun beCloseToUlps(
    expected: Expected,
    maxUlps: Long = 4L
): Matcher<Actual> {
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
                    actual, maxUlps, expected
                )
            },
            negatedFailureMessageFn = {
                String.format(
                    Locale.ROOT,
                    "Expected %.8g NOT to be within %d ULPs of %.8g",
                    actual, maxUlps, expected
                )
            }
        )
    }
}

/**
 * Asserts that this [Actual] value is close to [expected] using mixed tolerances:
 * `|a - e| <= max(absoluteTolerance, relativeTolerance * scale)`, where `scale = max(1.0, max(|a|, |e|))`.
 *
 * @receiver The [Actual] value under test.
 * @param expected The [Expected] reference value.
 * @param absoluteTolerance Non-negative absolute tolerance (default `1e-9`).
 * @param relativeTolerance Non-negative relative tolerance (default `1e-12`).
 * @return The receiver [Actual], to allow fluent chaining.
 * @throws [IllegalArgumentException] if `expected` is not finite or tolerances are negative.
 * @see beCloseTo
 */
fun Actual.shouldBeCloseTo(
    expected: Expected,
    absoluteTolerance: Double = 1e-9,
    relativeTolerance: Double = 1e-12
): Actual = apply { this should beCloseTo(expected, absoluteTolerance, relativeTolerance) }

/**
 * Asserts that this [Actual] value is **not** close to [expected] under the mixed tolerance rule used by
 * [shouldBeCloseTo].
 *
 * @receiver The [Actual] value under test.
 * @param expected The [Expected] reference value.
 * @param absoluteTolerance Non-negative absolute tolerance (default `1e-9`).
 * @param relativeTolerance Non-negative relative tolerance (default `1e-12`).
 * @return The receiver [Actual], to allow fluent chaining.
 * @throws [IllegalArgumentException] if `expected` is not finite or tolerances are negative.
 * @see beCloseTo
 */
fun Actual.shouldNotBeCloseTo(
    expected: Expected,
    absoluteTolerance: Double = 1e-9,
    relativeTolerance: Double = 1e-12
): Actual = apply { this shouldNot beCloseTo(expected, absoluteTolerance, relativeTolerance) }

/**
 * Asserts that this [Actual] value is within [maxUlps] **ULPs** of [expected].
 * ULPs (Units in the Last Place) compare values by the distance between their IEEE 754 representations after sign-aware
 * ordering.
 *
 * @receiver The [Actual] value under test.
 * @param expected The [Expected] reference value.
 * @param maxUlps Maximum allowed ULP distance (non-negative, default `4`).
 * @return The receiver [Actual], to allow fluent chaining.
 * @throws [IllegalArgumentException] if `expected` is not finite or `maxUlps` is negative.
 * @see beCloseToUlps
 */
fun Actual.shouldBeCloseToUlps(
    expected: Expected,
    maxUlps: Long = 4L
): Actual = apply { this should beCloseToUlps(expected, maxUlps) }

/**
 * Asserts that this [Actual] value is **not** within [maxUlps] **ULPs** of [expected].
 *
 * @receiver The [Actual] value under test.
 * @param expected The [Expected] reference value.
 * @param maxUlps Maximum allowed ULP distance (non-negative, default `4`).
 * @return The receiver [Actual], to allow fluent chaining.
 * @throws [IllegalArgumentException] if `expected` is not finite or `maxUlps` is negative.
 * @see beCloseToUlps
 */
fun Actual.shouldNotBeCloseToUlps(
    expected: Expected,
    maxUlps: Long = 4L
): Actual = apply { this shouldNot beCloseToUlps(expected, maxUlps) }

/**
 * Checks whether two floating-point numbers are "close enough" within specified absolute and relative tolerances.
 *
 * The comparison adapts to the scale of the values:
 * - At small magnitudes, [absoluteTolerance] dominates to ensure sensitivity when values are near zero.
 * - At larger magnitudes, [relativeTolerance] scales the allowed error proportional to the maximum magnitude of
 *   [actual] and [expected].
 *
 * Formally, the two numbers are considered close if:
 * ```
 * |actual - expected| ≤ max(absoluteTolerance, relativeTolerance * scale)
 * ```
 * where `scale = max(1.0, max(|actual|, |expected|))`.
 *
 * @param actual The [Actual] value being tested.
 * @param expected The [Expected] reference value.
 * @param absoluteTolerance Non-negative fixed tolerance for small magnitudes.
 * @param relativeTolerance Non-negative tolerance proportional to magnitude.
 * @return `true` if [actual] and [expected] are close enough, `false` otherwise.
 * @throws [IllegalArgumentException] if tolerances are negative.
 */
private fun closeEnough(
    actual: Actual,
    expected: Expected,
    absoluteTolerance: Double,
    relativeTolerance: Double
): Boolean {
    val scale = max(1.0, max(abs(actual), abs(expected)))
    return abs(actual - expected) <= max(absoluteTolerance, relativeTolerance * scale)
}

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
 * @param actual The tested [Actual] value.
 * @param expected The reference [Expected] value.
 * @param maxUlps The maximum allowed ULP distance (non-negative).
 * @return `true` if the ULP distance between [actual] and [expected] is ≤ [maxUlps]; `false` otherwise.
 */
private fun withinUlps(actual: Actual, expected: Expected, maxUlps: Long): Boolean {
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

/**
 * Ensures that the [Expected] value is finite.
 *
 * @receiver The [Expected] (reference) value being validated.
 * @throws [IllegalArgumentException] if the receiver is not finite.
 */
private fun Expected.requireFinite() =
    require(isFinite()) { "expected must be finite, but was $this." }

/**
 * Ensures that the receiver [Double] value is **non-negative**.
 *
 * @receiver The [Double] value to validate.
 * @param name The name of the parameter being validated, used in error messages.
 * @throws IllegalArgumentException if the value is negative.
 */
private fun Double.requireNonNegative(name: String) =
    require(this >= 0) { "$name must be non-negative, but was $this." }

/**
 * Ensures that the receiver [Long] value is **non-negative**.
 *
 * @receiver The [Long] value to validate.
 * @param name The name of the parameter being validated, used in error messages.
 * @throws IllegalArgumentException if the value is negative.
 */
private fun Long.requireNonNegative(name: String) =
    require(this >= 0) { "$name must be non-negative, but was $this." }
