/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.util.size.gen

import cl.ravenhill.keen.util.size.HasSize
import cl.ravenhill.keen.util.size.Size
import io.kotest.property.Arb
import io.kotest.property.arbitrary.map

/**
 * Generates an arbitrary instance of [HasSize] with its [Size] determined by the given [Arb] of [Size].
 *
 * @param size An [Arb] that generates valid [Size] instances. Defaults to [validSize], which produces random,
 *   non-negative [Size] values.
 * @return An [Arb] that generates instances of [HasSize] with a specified [Size].
 */
internal fun Arb.Companion.hasSize(size: Arb<Size> = validSize()): Arb<HasSize> =
    size.map {
        object : HasSize {
            override val size: Size = it
        }
    }
