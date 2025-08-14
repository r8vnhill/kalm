/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.util.size

import cl.ravenhill.keen.exceptions.KeenException

/**
 * Represents domain-specific errors that can occur when constructing a [Size] instance.
 */
public sealed interface SizeError : KeenException {

    /**
     * Error indicating that a non-negative size was expected but not provided.
     *
     * @property actual The invalid size value.
     */
    public data class NonNegativeExpected(val actual: Int) : Exception(
        "Expected a non-negative size, but got $actual"
    ), SizeError

    /**
     * Error indicating that a strictly positive size was expected but not provided.
     *
     * @property actual The invalid size value.
     */
    public data class StrictlyPositiveExpected(val actual: Int) : Exception(
        "Expected a strictly positive size, but got $actual"
    ), SizeError

    /**
     * Exception indicating that two [Size] values were expected to match but do not.
     *
     * @property expected The [Size] value that was expected.
     * @property actual The [Size] value that was received but did not match the expectation.
     */
    public data class MatchingSizesExpected(val expected: Size, val actual: Size) :
        Exception("Expected size $expected, but got $actual"), SizeError
}