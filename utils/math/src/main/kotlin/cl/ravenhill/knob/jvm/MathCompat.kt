/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.jvm

/**
 * Platform-specific math utilities.
 *
 * This object abstracts mathematical operations that may require platform-specific implementations, such as [fma],
 * which uses [Math.fma] on the JVM.
 *
 * On non-JVM platforms, these functions should be reimplemented to use the appropriate native or emulated equivalent.
 */
@JvmSpecific
public object MathCompat {

    /**
     * Returns `(a * b) + c`, computed with only one rounding error.
     *
     * Delegates to [Math.fma] on the JVM for hardware-accelerated precision.
     *
     * @see Math.fma
     */
    @Suppress("SpellCheckingInspection")
    @JvmStatic
    public fun fma(a: Double, b: Double, c: Double): Double =
        Math.fma(a, b, c) // Fused multiply add; not Fullmetal Alchemist
}
