/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.gen

import io.kotest.property.Arb
import io.kotest.property.arbitrary.double

/**
 * Produces an Arb (arbitrary generator) that generates random Double values within the range of -1,000,000 to 1,000,000
 * (inclusive). Non-finite values such as `Infinity`, `-Infinity`, and `NaN` are excluded.
 *
 * @return an arbitrary generator for finite Double values within the specified range.
 */
fun Arb.Companion.finiteDouble(): Arb<Double> = Arb
    .double(
        min = -1e6, max = 1e6,
        includeNonFiniteEdgeCases = false
    )
