/*
 * Applies shared conventions and wires dependencies for this JVM/Kotlin module.
 *
 * ---------
 * Key ideas
 * ---------
 *  - Conventions live in your precompiled plugins (`keen.library`, `keen.jvm`).
 *  - A central constraints/BOM project (`:dependency-constraints`) defines versions.
 *  - Detekt runs with an extra ruleset (formatting).
 *
 * --------------------
 * Tips for maintainers
 * --------------------
 *  - Use `api` only if the types leak into this module’s public API (e.g., Arrow's Either); otherwise prefer
 *    `implementation`.
 *  - If you want to *force* (strictly enforce) versions from the BOM, replace `platform(...)` with
 *    `enforcedPlatform(...)` below.
 */

plugins {
    id("keen.library") // Project-wide library conventions (tests, publishing knobs, etc.)
    id("keen.jvm") // JVM/Kotlin toolchain + compiler defaults (property-driven Java version)
    alias(libs.plugins.detekt) // Static analysis (Detekt) for Kotlin sources
}

dependencies {
    // Import the project-local platform/BOM that pins versions for your house stack (e.g., Arrow).
    // This does not add classes to the classpath; it only contributes *version constraints* to resolution.
    // Use `api(platform(...))` if you publish this module and want consumers to inherit the same constraints.
    implementation(platform(projects.dependencyConstraints))

    // Attach Detekt’s formatting ruleset (ktlint rules packaged for Detekt).
    detektPlugins(libs.detekt.formatting.get().apply { "$group:$module:$version" })

    // Arrow libraries (bundle from the catalog).
    api(libs.bundles.arrow)

    // Kotest test dependencies (bundle).
    testImplementation(libs.bundles.kotest)
}
