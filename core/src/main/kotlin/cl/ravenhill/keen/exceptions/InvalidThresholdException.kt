/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.exceptions

public class InvalidThresholdException(should: String, threshold: Double) :
    Exception("Threshold should $should, but was $threshold"), KeenException
