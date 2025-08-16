/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.matchers

import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.should
import io.kotest.matchers.shouldNot

/**
 * Asserts that this [Double] is finite (not `NaN`, not `+Infinity`, not `-Infinity`).
 *
 * @receiver the [Double] value to check.
 * @throws AssertionError if the value is not finite.
 */
internal fun Double.shouldBeFinite() = apply { this should beFinite() }

/**
 * Asserts that this [Double] is not finite (`NaN`, `+Infinity`, or `-Infinity`).
 *
 * @receiver the [Double] value to check.
 * @throws AssertionError if the value is finite.
 */
internal fun Double.shouldNotBeFinite() = apply { this shouldNot beFinite() }

/**
 * Creates a [Matcher] for [Double] values that checks whether they are finite.
 *
 * Finite values are those that are not `NaN` and not infinite.
 *
 * @return a [Matcher] that passes if the tested value is finite.
 */
internal fun beFinite(): Matcher<Double> = Matcher { actual ->
    MatcherResult(
        passed = actual.isFinite(),
        failureMessageFn = { "Expected $actual to be finite, but it is ${describe(actual)}." },
        negatedFailureMessageFn = { "Expected $actual not to be finite, but it is." }
    )
}

/**
 * Returns a string representation of special [Double] values for error messages.
 *
 * - `NaN` is displayed as `"NaN"`.
 * - `+Infinity` and `-Infinity` are displayed explicitly.
 * - Finite numbers are formatted to 6 decimal places.
 */
private fun describe(d: Double): String =
    when {
        d.isNaN() -> "NaN"
        d == Double.POSITIVE_INFINITY -> "+Infinity"
        d == Double.NEGATIVE_INFINITY -> "-Infinity"
        else -> "%.6f".format(d)
    }
