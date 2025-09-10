/*
 * Applies shared conventions and wires dependencies for this JVM/Kotlin module.
 *
 * ---------
 * Key ideas
 * ---------
 *  - Conventions live in the precompiled plugins (`knob.library`, `knob.jvm`).
 *  - A central constraints/BOM project (`:dependency-constraints`) defines versions.
 *  - Detekt runs with an extra ruleset (formatting).
 *
 * --------------------
 * Tips for maintainers
 * --------------------
 *  - Use `api` only if the types leak into this module’s public API (e.g., Arrow's `Either`); otherwise prefer
 *    `implementation`.
 *  - When adding math-intensive functionality, prefer depending on `:util:math` instead of duplicating numeric kernels
 *    locally.
 */

plugins {
    id("knob.library") // Project-wide library conventions (tests, publishing knobs, etc.)
    id("knob.jvm") // JVM/Kotlin toolchain + compiler defaults (property-driven Java version)
    alias(libs.plugins.detekt) // Static analysis (Detekt) for Kotlin sources
}

dependencies {
    // Import the project-local platform/BOM that pins versions for your house stack (e.g., Arrow, Kotest).
    // This does not add classes to the classpath; it only contributes *version constraints* to resolution.
    implementation(platform(projects.dependencyConstraints))

    // Depend on :core
    implementation(projects.core)

    // Attach Detekt’s formatting ruleset (ktlint rules packaged for Detekt).
    detektPlugins(libs.detekt.formatting.get().apply { "$group:$module:$version" })

    // Test dependencies
    testImplementation(projects.utils.testCommons)
}
