/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.utils.size

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import cl.ravenhill.knob.utils.size.Size.Companion.fromNonNegative
import cl.ravenhill.knob.utils.size.Size.Companion.invoke
import cl.ravenhill.knob.utils.size.Size.Companion.ofOrNull
import cl.ravenhill.knob.utils.size.Size.Companion.ofOrThrow
import cl.ravenhill.knob.utils.size.Size.Companion.strictlyPositive

/**
 * Value type that represents a **non-negative** size.
 *
 * A [Size] wraps an underlying [Int] and guarantees the invariant `value >= 0` for all instances created through its
 * validated factories. Use the companion factories to construct instances according to your needs:
 *
 * - [invoke] / [fromNonNegative] return an [arrow.core.Either] with a typed [SizeError] on failure.
 * - [strictlyPositive] enforces `value > 0`.
 * - [ofOrNull] returns `null` on invalid input (Java/Kotlin friendly).
 * - [ofOrThrow] throws [SizeError.NonNegativeExpected] on invalid input (Java friendly).
 *
 * Arithmetic and comparisons operate on the underlying value. Note that [plus] does **not** check for overflow; prefer
 * validating external bounds when summing large values.
 *
 * ## Usage:
 *
 * ### Example 1: Non-negative construction with Either
 * ```kotlin
 * // Right(Size(10))
 * val ok: Either<SizeError, Size> = Size(10)
 *
 * // Left(SizeError.NonNegativeExpected(-1))
 * val bad = Size(-1)
 * ```
 *
 * ### Example 2: Strictly positive / null / exception
 * ```kotlin
 * val s1 = Size.strictlyPositive(5)       // Right(…)
 * val s2: Size? = Size.ofOrNull(-3)       // null
 * val s3 = Size.ofOrThrow(7)              // returns Size(7) or throws InvalidSizeException
 * ```
 *
 * @property value The underlying non-negative [Int] (invariant: `value >= 0`).
 * @property isStrictlyPositive `true` if `value > 0`.
 */
@JvmInline
public value class Size private constructor(public val value: Int) : Comparable<Size> {

    public val isStrictlyPositive: Boolean
        get() = value > 0

    public companion object {

        /**
         * Canonical zero size.
         */
        @JvmStatic
        public val zero: Size = Size(0)

        /**
         * Validated constructor alias for [fromNonNegative].
         *
         * @param n Candidate [Int] value.
         * @return [arrow.core.Either] with [Size] on success or [SizeError] on failure.
         */
        @JvmStatic
        public operator fun invoke(n: Int): Either<SizeError, Size> =
            fromNonNegative(n)

        /**
         * Builds a [Size] if `n >= 0`; otherwise returns a typed error.
         *
         * @param n Candidate [Int] value.
         * @return [arrow.core.Either] with [Size] or [SizeError.NonNegativeExpected].
         */
        @JvmStatic
        public fun fromNonNegative(n: Int): Either<SizeError, Size> = either {
            ensure(n >= 0) {
                SizeError.NonNegativeExpected(n)
            }
            Size(n)
        }

        /**
         * Builds a [Size] if `n > 0`; otherwise returns a typed error.
         *
         * @param n Candidate [Int] value.
         * @return [arrow.core.Either] with [Size] or [SizeError.StrictlyPositiveExpected].
         */
        @JvmStatic
        public fun strictlyPositive(n: Int): Either<SizeError, Size> = either {
            ensure(n > 0) {
                SizeError.StrictlyPositiveExpected(n)
            }
            Size(n)
        }

        /**
         * Returns a [Size] if `n >= 0`; otherwise `null`.
         *
         * @param n Candidate [Int] value.
         * @return A [Size] or `null` when invalid.
         */
        @JvmStatic
        public fun ofOrNull(n: Int): Size? =
            if (n >= 0) Size(n)
            else null

        /**
         * Returns a [Size] if `n >= 0`; otherwise throws [SizeError.NonNegativeExpected].
         *
         * @param n Candidate [Int] value.
         * @return A validated [Size].
         * @throws [SizeError.NonNegativeExpected] when `n < 0`.
         */
        @Throws(SizeError.NonNegativeExpected::class)
        @JvmStatic
        public fun ofOrThrow(n: Int): Size =
            if (n >= 0) Size(n)
            else throw SizeError.NonNegativeExpected(n)
    }

    /**
     * Compares this size with [other] by the underlying [value].
     *
     * @param other The size to compare to.
     * @return A negative number, zero, or a positive number as this is less than, equal to, or greater than [other].
     */
    override fun compareTo(other: Size): Int = compareValuesBy(this, other) { it.value }

    /**
     * Adds two sizes by summing their underlying values.
     *
     * ## Note
     * This operation does **not** check for integer overflow. If you expect values near [Int.MAX_VALUE], validate
     * bounds before adding.
     *
     * @param other The addend size.
     * @return A new [Size] whose [value] is the sum of both operands.
     */
    public operator fun plus(other: Size): Size = Size(value + other.value)

    /**
     * Exposes the underlying [Int] value.
     *
     * @return The underlying non-negative [Int].
     */
    public fun toInt(): Int = value
}
