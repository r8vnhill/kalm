/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.exceptions

import cl.ravenhill.keen.KeenException

/**
 * Exception thrown when a provided threshold value does not satisfy the required conditions.
 *
 * This exception is used to signal that a threshold value is not within the expected range or does not adhere to the
 * required inequality specifications.
 *
 * @param should A description of the condition that the threshold should satisfy.
 * @param threshold The actual threshold value that violated the condition.
 */
public class InvalidThresholdException(should: String, threshold: Double) :
    Exception("Threshold should $should, but was $threshold"), KeenException
