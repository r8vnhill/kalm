/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.utils

/**
 * Represents the expected (reference or target) floating-point value in a comparison.
 *
 * This typealias is used to clarify intent when writing matchers or assertions, distinguishing between the "ground
 * truth" or desired value and the actual result.
 */
internal typealias ExpectedD = Double

/**
 * Represents the actual floating-point value observed during execution or testing.
 *
 * This typealias complements [ExpectedD] by making comparison signatures self-documenting, helping readers quickly see
 * which argument is the test subject and which is the reference.
 */
internal typealias ActualD = Double

/**
 * Ensures that the [ExpectedD] value is finite.
 *
 * @receiver The [ExpectedD] (reference) value being validated.
 * @throws [IllegalArgumentException] if the receiver is not finite.
 */
internal fun ExpectedD.requireFinite() =
    require(isFinite()) { "expected must be finite, but was $this." }

/**
 * Ensures that the receiver [Double] value is **non-negative**.
 *
 * @receiver The [Double] value to validate.
 * @param name The name of the parameter being validated, used in error messages.
 * @throws IllegalArgumentException if the value is negative.
 */
internal fun Double.requireNonNegative(name: String) =
    require(this >= 0) { "$name must be non-negative, but was $this." }
