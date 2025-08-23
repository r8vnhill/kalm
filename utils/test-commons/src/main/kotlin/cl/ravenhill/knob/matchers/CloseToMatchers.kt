/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.matchers

import cl.ravenhill.knob.utils.ActualD
import cl.ravenhill.knob.utils.ExpectedD
import cl.ravenhill.knob.utils.requireFinite
import cl.ravenhill.knob.utils.requireNonNegative
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.should
import io.kotest.matchers.shouldNot
import java.util.*
import kotlin.math.abs
import kotlin.math.max

/**
 * Creates a [Matcher] for verifying whether an [ActualD] value is close enough to an [ExpectedD] value, within the
 * given absolute and relative tolerances.
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
 * @return A [Matcher] for comparing an [ActualD] [Double] against [expected].
 * @throws IllegalArgumentException if `expected` is not finite or tolerances are negative.
 * @see closeEnough
 */
fun beCloseTo(
    expected: ExpectedD,
    absoluteTolerance: Double = 1e-9,
    relativeTolerance: Double = 1e-12
): Matcher<ActualD> {
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
                    actual,
                    expected,
                    diff,
                    absoluteTolerance,
                    relativeTolerance,
                    scale
                )
            },
            negatedFailureMessageFn = {
                String.format(
                    Locale.ROOT,
                    "Expected %.8g NOT to be close to %.8g with absoluteTolerance=%.1e, relativeTolerance=%.1e",
                    actual,
                    expected,
                    absoluteTolerance,
                    relativeTolerance
                )
            }
        )
    }
}

/**
 * Asserts that this [ActualD] value is close to [expected] using mixed tolerances:
 * `|a - e| <= max(absoluteTolerance, relativeTolerance * scale)`, where `scale = max(1.0, max(|a|, |e|))`.
 *
 * @receiver The [ActualD] value under test.
 * @param expected The [ExpectedD] reference value.
 * @param absoluteTolerance Non-negative absolute tolerance (default `1e-9`).
 * @param relativeTolerance Non-negative relative tolerance (default `1e-12`).
 * @return The receiver [ActualD], to allow fluent chaining.
 * @throws [IllegalArgumentException] if `expected` is not finite or tolerances are negative.
 * @see beCloseTo
 */
fun ActualD.shouldBeCloseTo(
    expected: ExpectedD,
    absoluteTolerance: Double = 1e-9,
    relativeTolerance: Double = 1e-12
): ActualD = apply { this should beCloseTo(expected, absoluteTolerance, relativeTolerance) }

/**
 * Asserts that this [ActualD] value is **not** close to [expected] under the mixed tolerance rule used by
 * [shouldBeCloseTo].
 *
 * @receiver The [ActualD] value under test.
 * @param expected The [ExpectedD] reference value.
 * @param absoluteTolerance Non-negative absolute tolerance (default `1e-9`).
 * @param relativeTolerance Non-negative relative tolerance (default `1e-12`).
 * @return The receiver [ActualD], to allow fluent chaining.
 * @throws [IllegalArgumentException] if `expected` is not finite or tolerances are negative.
 * @see beCloseTo
 */
fun ActualD.shouldNotBeCloseTo(
    expected: ExpectedD,
    absoluteTolerance: Double = 1e-9,
    relativeTolerance: Double = 1e-12
): ActualD = apply { this shouldNot beCloseTo(expected, absoluteTolerance, relativeTolerance) }

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
 * @param actual The [ActualD] value being tested.
 * @param expected The [ExpectedD] reference value.
 * @param absoluteTolerance Non-negative fixed tolerance for small magnitudes.
 * @param relativeTolerance Non-negative tolerance proportional to magnitude.
 * @return `true` if [actual] and [expected] are close enough, `false` otherwise.
 * @throws [IllegalArgumentException] if tolerances are negative.
 */
private fun closeEnough(
    actual: ActualD,
    expected: ExpectedD,
    absoluteTolerance: Double,
    relativeTolerance: Double
): Boolean {
    val scale = max(1.0, max(abs(actual), abs(expected)))
    return abs(actual - expected) <= max(absoluteTolerance, relativeTolerance * scale)
}
