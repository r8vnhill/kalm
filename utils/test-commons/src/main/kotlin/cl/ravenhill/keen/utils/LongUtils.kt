/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.utils

/**
 * Ensures that the receiver [Long] value is **non-negative**.
 *
 * @receiver The [Long] value to validate.
 * @param name The name of the parameter being validated, used in error messages.
 * @throws IllegalArgumentException if the value is negative.
 */
internal fun Long.requireNonNegative(name: String) =
    require(this >= 0) { "$name must be non-negative, but was $this." }
