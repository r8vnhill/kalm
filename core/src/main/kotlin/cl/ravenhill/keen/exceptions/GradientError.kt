/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.exceptions

import cl.ravenhill.keen.repr.Gradient

/**
 * Represents domain-specific errors related to [Gradient] operations.
 *
 * This sealed class serves as the base for all gradient-related failures in the API.
 * Each error subtype carries contextual information about the failure, allowing callers to handle different cases
 * explicitly.
 *
 * All [GradientError] instances are [Exception]s and implement [KeenException], making them suitable for both
 * functional error handling (e.g., with `Either`) and exception-based flows.
 *
 * @param message A descriptive message of the error.
 * @param cause An optional cause for this error.
 */
public sealed class GradientError(message: String, cause: Throwable? = null) :
    Exception(message, cause), KeenException {

    /**
     * Error indicating that a gradient operation was attempted on incompatible gradients.
     *
     * This is typically thrown when two [Gradient] instances have different sizes, or otherwise cannot be combined in
     * the requested [operation].
     *
     * @property operation The name of the attempted operation (e.g., `"add"`, `"dotProduct"`).
     * @property self The first [Gradient] operand.
     * @property other The second operand, which may be of a different type.
     * @property cause An optional cause for this error.
     */
    public data class InvalidOperation(
        val operation: String,
        val self: Gradient,
        val other: Any,
        override val cause: Throwable? = null
    ) : GradientError(
        "Cannot perform operation '$operation' on gradients: $self and $other",
        cause
    )
}
