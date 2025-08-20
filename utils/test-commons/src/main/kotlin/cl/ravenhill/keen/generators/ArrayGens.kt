/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.generators

import cl.ravenhill.keen.utils.size.Size
import io.kotest.property.Arb
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.doubleArray

/**
 * Builds an [Arb] that always produces [DoubleArray] values of an **exact fixed size**.
 *
 * This is a convenience wrapper around [Arb.Companion.doubleArray], where the `length` is pinned to a constant taken
 * from the provided [Size]. The contents of the array are drawn from the given [content] generator.
 *
 * Useful in property tests when the array length must remain stable across samples (e.g., for boundary checks or
 * aggregate operations).
 *
 * ## Usage:
 * ```kotlin
 * val size = Size.ofOrThrow(5)
 * val elements = Arb.finiteDouble(lo = -1.0, hi = 1.0)
 *
 * checkAll(Arb.doubleArrayExact(size, elements)) { arr ->
 *   arr.shouldHaveSize(size)
 *   arr.forEach { it shouldBeInRange -1.0..1.0 }
 * }
 * ```
 *
 * @param size The fixed [Size] of each generated array.
 * @param content The [Arb] used to generate each element.
 * @return An [Arb] that yields [DoubleArray] instances of length [size].
 */
fun Arb.Companion.doubleArrayExact(size: Size, content: Arb<Double>): Arb<DoubleArray> =
    Arb.doubleArray(length = Arb.constant(size.toInt()), content = content)
