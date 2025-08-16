/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.jvm

/**
 * Marks an API as **JVM-specific**.
 *
 * Use this annotation to indicate that a function, property, or class depends on JVM-only APIs, behaviors, or
 * optimizations, and is therefore not directly portable to other Kotlin targets (e.g., JavaScript or Native).
 *
 * ## When to Use
 * - Code that calls `java.*` or `javax.*` APIs.
 * - Numeric computations using JVM-specific intrinsics or optimizations.
 * - APIs with JVM-specific overloads, annotations, or default parameter values.
 *
 * ## Portability Notes
 * For multiplatform projects, any element marked with `@JvmSpecific` should be replaced or reimplemented for non-JVM
 * targets. This annotation is retained in the binary output so that tools and build systems can detect
 * platform-specific code during analysis or compilation.
 *
 * You can use a library like [ClassGraph](https://github.com/classgraph/classgraph) or [ASM](https://asm.ow2.io) to
 * scan for this annotation in your compiled classes to identify JVM-specific code paths.
 *
 * ## Usage Example
 * ```kotlin
 * @JvmSpecific
 * fun fastMultiply(a: Double, b: Double): Double =
 *     Math.fma(a, b, 0.0) // JVM intrinsic
 * ```
 *
 * @see JvmStatic
 */
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CLASS
)
public annotation class JvmSpecific
