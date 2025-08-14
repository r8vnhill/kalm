/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.util.size.gen

import arrow.core.Either
import cl.ravenhill.keen.util.size.Size
import cl.ravenhill.keen.util.size.SizeError
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map

/**
 * Produces an [Arb] that generates values representing either a [SizeError] or a valid [Size].
 * The generated sizes are constrained by the specified range.
 *
 * @param range The range of integer values to generate. Defaults to `0..1024`.
 * @return An [Arb] that produces [Either] instances, containing either [SizeError] on failure or a valid [Size].
 */
internal fun Arb.Companion.size(range: IntRange = 0..1024): Arb<Either<SizeError, Size>> = Arb
    .int(range)
    .map { Size(it) }

/**
 * Generates a random valid [Size] within the specified range.
 *
 * @param range The range of integers from which the size value is generated. Defaults to 0..1024.
 * @return An `Arb<Size>` that produces validated [Size] instances.
 * @throws [SizeError.NonNegativeExpected] if the generated integer is negative.
 */
internal fun Arb.Companion.validSize(range: IntRange = 0..1024): Arb<Size> = Arb
    .int(range)
    .map { Size.ofOrThrow(it) }
