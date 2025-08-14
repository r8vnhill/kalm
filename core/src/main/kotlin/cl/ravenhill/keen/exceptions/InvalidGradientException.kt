/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.exceptions

/**
 * Exception thrown when a gradient value does not meet the required conditions or expectations.
 *
 * This exception is typically used to indicate that the gradient-related calculations or parameters are invalid.
 *
 * @param message A detailed message explaining the reason for the exception.
 * @param cause The cause of the exception, which can be another exception that triggered this one (optional).
 */
public class InvalidGradientException(message: String, cause: Throwable? = null) : Exception(message, cause),
    KeenException
