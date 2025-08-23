/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.jvm

import cl.ravenhill.knob.jvm.vector.DotProduct
import cl.ravenhill.knob.jvm.vector.VectorizedDotProduct
import cl.ravenhill.knob.jvm.vector.L2Norm
import cl.ravenhill.knob.jvm.vector.VectorizedL2Norm
import jdk.incubator.vector.DoubleVector

// A preferred species is a species of maximal bit-size for the platform.
private val species = DoubleVector.SPECIES_PREFERRED
private val lanes = species.length()

/**
 * JVM-specific façade for vectorized dot products.
 *
 * This object implements [DotProduct] by **delegating** to a [VectorizedDotProduct] that is configured with the platform’s
 * **preferred SIMD shape** (`SPECIES_PREFERRED`) and its lane count.
 * The delegation means all methods in [DotProduct] are available directly on `VectorOps` without boilerplate.
 *
 * ## What it does
 * - Exposes:
 *   - [dotProduct] — high-throughput SIMD dot using FMA + horizontal reduce.
 *   - [dotProductKahan] — SIMD **Kahan-compensated** dot for improved numerical stability.
 * - Uses the JDK **Vector API** (`jdk.incubator.vector`) and is therefore **JVM-only**.
 *
 * ### Species & lanes
 * - `species` is the platform-preferred SIMD species for `double` (e.g., AVX2/AVX-512 on x86, SVE/NEON on ARM).
 * - `lanes = species.length()` gives the number of doubles processed per SIMD step.
 * - Picking the preferred species lets HotSpot/JIT choose the widest hardware vector available.
 *
 * ### Requirements
 * - JDK with the [Vector API](https://docs.oracle.com/en/java/javase/24/docs/api/jdk.incubator.vector/jdk/incubator/vector/Vector.html)
 *
 * ### Thread-safety
 * - Stateless and **thread-safe**; methods operate only on provided arrays.
 *
 * ### Examples
 * ```kotlin
 * val a = doubleArrayOf(2.0, 4.0, 6.0, 8.0)
 * val b = doubleArrayOf(1.0, 3.0, 5.0, 7.0)
 *
 * // Full arrays (up to min size)
 * val d1 = VectorOps.run { a dotProduct b }
 *
 * // Slice-based (offset + length)
 * val d2 = VectorOps.dotProduct(a, aOff = 1, len = 3, b, bOff = 0) // 4*1 + 6*3 + 8*5
 *
 * // Numerically tougher inputs: use Kahan
 * val d3 = with(VectorOps) { a dotProductKahan b }
 * ```
 *
 * @see jdk.incubator.vector.DoubleVector
 */
@JvmSpecific
public object VectorOps : DotProduct by VectorizedDotProduct(species, lanes), L2Norm by VectorizedL2Norm(species, lanes)
