/*
 * ====================================================
 * :utils:domain — Domain-specific models and utilities
 * ====================================================
 *
 * This module defines domain-level utilities such as `Size`, `HasSize`, and related error types.
 * Its purpose is to provide **type-safe, validated primitives** and **contracts** that higher-level Knob modules (e.g.,
 * math, core, examples) can build upon.
 *
 * ## Key points
 * - Applies the shared Knob library convention (`knob.library`).
 * - Enables static analysis (Detekt) and coverage (Kover).
 * - Exposes Arrow as a public API dependency for functional error handling.
 * - Uses the project’s dependency BOM to stay version-consistent across modules.
 */

plugins {
    // Applies Knob’s internal convention for library modules (Kotlin/JVM, publishing, etc.)
    id("knob.library")

    // Static analysis (Kotlin style/linting)
    alias(libs.plugins.detekt)

    // Code coverage instrumentation and reporting
    alias(libs.plugins.kover)
}

dependencies {
    // Aligns dependency versions using the house BOM (Kotest, Arrow, etc.)
    implementation(platform(projects.dependencyConstraints))

    // Exposes Arrow Core publicly since domain types (Size, HasSize) use Either, etc.
    api(libs.arrow.core)

    testImplementation(projects.utils.testCommons)

    // Adds Detekt’s formatting plugin (ktlint rules packaged as a Detekt plugin)
    detektPlugins(libs.detekt.formatting)
}
