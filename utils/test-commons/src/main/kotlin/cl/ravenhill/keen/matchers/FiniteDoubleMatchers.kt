/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.matchers

import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.should
import io.kotest.matchers.shouldNot
import java.util.Locale

/**
 * Lazily-initialized [Matcher] for verifying that a [Double] is **finite**.
 *
 * ## Behavior
 * This matcher passes if and only if the tested value is:
 * - **Not [Double.NaN]**
 * - **Not positive infinity ([Double.POSITIVE_INFINITY])**
 * - **Not negative infinity ([Double.NEGATIVE_INFINITY])**
 *
 * In other words, only ordinary finite floating-point values succeed.
 */
private val isFiniteMatcher: Matcher<Double> by lazy {
    Matcher { actual ->
        val passed = actual.isFinite()
        MatcherResult(
            passed = passed,
            failureMessageFn = {
                "Expected ${actual.render()} to be finite, but it is not."
            },
            negatedFailureMessageFn = {
                "Expected ${actual.render()} not to be finite, but it is finite."
            }
        )
    }
}

/**
 * Creates a reusable [Matcher] for checking whether a [Double] value is **finite**.
 *
 * This is a convenience wrapper around [isFiniteMatcher], intended as the primary entrypoint when writing custom
 * assertions in tests.
 *
 * @return a [Matcher] that checks finiteness of [Double] values.
 */
fun beFinite(): Matcher<Double> = isFiniteMatcher

fun Double.shouldBeFinite(): Double = apply { this should beFinite() }

fun Double.shouldNotBeFinite(): Double = apply { this shouldNot beFinite() }

internal fun Double.render(): String = when {
    isNaN() -> "NaN"
    isInfinite() && this > 0.0 -> "Infinity"
    isInfinite() && this < 0.0 -> "-Infinity"
    else -> String.format(Locale.ROOT, "%.6f", this)
}
