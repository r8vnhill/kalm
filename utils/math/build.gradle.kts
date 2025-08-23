/*
 * ======================================
 * :util:math — Math utilities for Knob
 * ======================================
 *
 * Provides efficient math utilities such as vectorized operations and numeric kernels. This module is JVM-exclusive by
 * design, as it depends on high-performance JVM numeric APIs (e.g., Math.fma, the Vector API).
 *
 * Knob itself is designed to be extensible and portable to multiplatform projects. However, modules like this one must
 * be re-implemented for each target platform, since they rely on low-level, JVM-specific performance primitives.
 *
 * In practice, this means:
 *  - Knob core and most modules are portable.
 *  - :util:math is tightly bound to the JVM runtime.
 */

plugins {
    id("knob.library")
    id("knob.jvm")
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

dependencies {
    // House BOM/platform for consistent versions (Kotest, etc.)
    implementation(platform(projects.dependencyConstraints))

    // Test: Kotest (core, assertions, property)
    testImplementation(projects.utils.testCommons)

    // Detekt formatting (ktlint rules packaged for Detekt)
    detektPlugins(libs.detekt.formatting)
}
