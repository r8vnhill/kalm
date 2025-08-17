/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.utils.size.matchers

import cl.ravenhill.keen.utils.size.HasSize
import cl.ravenhill.keen.utils.size.Size
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.should

/**
 * Creates a [Matcher] that verifies whether the size of a [HasSize] is exactly equal to [expected].
 *
 * The comparison is performed against the `Int` value of [HasSize.size].
 * Failure messages include both the raw `Int` size and the [Size] representation for clarity.
 *
 * @param expected the expected size as a raw [Int].
 * @return a [Matcher] that succeeds if the actual size equals [expected].
 */
internal fun haveSize(expected: Int): Matcher<HasSize> =
    Matcher { actual ->
        val actualInt = actual.size.toInt()
        MatcherResult(
            passed = actualInt == expected,
            failureMessageFn = {
                "Expected size == $expected, but was $actualInt (actual Size=${actual.size})."
            },
            negatedFailureMessageFn = {
                "Expected size != $expected, but it was $actualInt."
            }
        )
    }

/**
 * Creates a [Matcher] that verifies whether the size of a [HasSize] is exactly equal to [expected].
 *
 * This overload accepts a [Size] instance directly, which is internally compared by converting it to an [Int] and
 * delegating to [haveSize] for consistency.
 *
 * @param expected the expected size as a [Size].
 * @return a [Matcher] that succeeds if the actual size equals [expected].
 */
internal fun haveSize(expected: Size): Matcher<HasSize> = haveSize(expected.toInt())

/**
 * Asserts that this [HasSize] has exactly [expected] elements.
 *
 * This is a fluent infix variant of [haveSize] that can be chained with other assertions.
 *
 * @param expected the expected size as a raw [Int].
 * @return the same [HasSize] instance for fluent chaining.
 */
internal infix fun HasSize.shouldHaveSize(expected: Int): HasSize =
    apply { this should haveSize(expected) }

/**
 * Asserts that this [HasSize] has exactly [expected] elements.
 *
 * This is a fluent infix variant of [haveSize] that can be chained with other assertions.
 *
 * @param expected the expected size as a [Size].
 * @return the same [HasSize] instance for fluent chaining.
 */
internal infix fun HasSize.shouldHaveSize(expected: Size): HasSize =
    apply { this should haveSize(expected) }
