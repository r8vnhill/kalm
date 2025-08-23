/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.generators

import io.kotest.property.Arb
import io.kotest.property.arbitrary.double

/** Creates an [Arb] that generates only **finite** [Double] values within the given range.
 *
 * Unlike [Arb.Companion.double], this excludes non-finite edge cases such as `NaN`, `+Infinity`, and `-Infinity`.
 * This makes it suitable for property tests where only real, bounded numeric values are valid.
 *
 * ## Usage:
 * ```kotlin
 * // Default range: [-1e6, 1e6]
 * checkAll(Arb.finiteDouble()) { d ->
 *   require(d.isFinite()) // always true
 * }
 *
 * // Custom range
 * checkAll(Arb.finiteDouble(lo = 0.0, hi = 10.0)) { d ->
 *   d shouldBeGreaterThanOrEqual 0.0
 *   d shouldBeLessThanOrEqual 10.0
 * }
 * ```
 *
 * @param lo The inclusive lower bound of the generated range (default: `-1e6`).
 * @param hi The inclusive upper bound of the generated range (default: `1e6`).
 * @return An [Arb] producing finite [Double] values within `[lo, hi]`.
 */
fun Arb.Companion.finiteDouble(lo: Double = -1e6, hi: Double = 1e6): Arb<Double> = Arb
    .double(
        min = lo,
        max = hi,
        includeNonFiniteEdgeCases = false
    )
