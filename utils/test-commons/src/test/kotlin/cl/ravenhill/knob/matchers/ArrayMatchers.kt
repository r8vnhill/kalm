/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.matchers

import cl.ravenhill.knob.utils.size.Size
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.should
import io.kotest.matchers.shouldNot

/**
 * Core size matcher builder for any subject whose size can be measured.
 *
 * ## Usage:
 * ```kotlin
 * val matcher = haveSize<CharArray>(expected = 4) { it.size }
 * charArrayOf('a','b','c','d') should matcher
 * ```
 *
 * @param T Subject type.
 * @param expected Expected size as [Int].
 * @param measure Function that returns the subject size.
 * @return A [Matcher] that checks equality against [expected].
 */
private inline fun <T> haveSize(expected: Int, crossinline measure: (T) -> Int): Matcher<T> =
    Matcher { actual ->
        val actualSize = measure(actual)
        MatcherResult(
            actualSize == expected,
            { "Expected size $expected but was $actualSize." },
            { "Expected size to differ from $expected, but was $actualSize." }
        )
    }

/** Exact size matcher for [DoubleArray] using domain type [Size].
 *
 * ## Usage:
 * ```kotlin
 * val n: Size = Size.ofOrThrow(3)
 * doubleArrayOf(1.0, 2.0, 3.0) should haveSize(n)
 * ```
 *
 * @param size Expected size as [Size].
 * @return A [Matcher] for [DoubleArray] size.
 */
internal fun haveSize(size: Size): Matcher<DoubleArray> =
    haveSize(size.toInt()) { it.size }

/**
 * Asserts that this [DoubleArray] has the given [Size].
 *
 * ## Usage:
 * ```kotlin
 * val n = Size.ofOrThrow(2)
 * doubleArrayOf(1.0, 2.0) shouldHaveSize n
 * ```
 *
 * @receiver The array under test.
 * @param size Expected size.
 * @return The same array (for fluent chaining).
 */
internal infix fun DoubleArray.shouldHaveSize(size: Size): DoubleArray =
    apply { this should haveSize(size) }

/**
 * Asserts that this [DoubleArray] does **not** have the given [Size].
 *
 * ## Usage:
 * ```kotlin
 * val n = Size.ofOrThrow(3)
 * doubleArrayOf(1.0, 2.0) shouldNotHaveSize n
 * ```
 *
 * @receiver The array under test.
 * @param size Size that must not match.
 * @return The same array (for fluent chaining).
 */
internal infix fun DoubleArray.shouldNotHaveSize(size: Size): DoubleArray =
    apply { this shouldNot haveSize(size) }
