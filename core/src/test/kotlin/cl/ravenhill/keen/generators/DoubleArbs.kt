/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.generators

import cl.ravenhill.keen.util.size.Size
import io.kotest.property.Arb
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.doubleArray

/**
 * Creates an [Arb] that generates [DoubleArray] instances of an exact length.
 *
 * @param size Fixed number of elements in each generated array.
 * @param content Arbitrary used to generate each element of the array.
 * @return An [Arb] producing [DoubleArray] values of length [size].
 */
internal fun Arb.Companion.doubleArrayExact(size: Size, content: Arb<Double>): Arb<DoubleArray> =
    Arb.doubleArray(length = Arb.constant(size.toInt()), content = content)
