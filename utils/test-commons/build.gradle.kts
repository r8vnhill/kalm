/*
 * ==============================================
 * :util:test-commons — Shared test dependencies
 * ==============================================
 *
 * Provides a shared bundle of test libraries (Kotest core, assertions, property testing, etc.) as an API dependency for
 * other Keen modules.
 * This prevents duplication of test-related dependency declarations across the project.
 *
 * This module is **not intended for publication**.
 * It exists purely as a convenience layer within the Keen build, ensuring consistent test stacks across all
 * subprojects.
 *
 * --------------------
 * Tips for maintainers
 * --------------------
 *  - Use this module to centralize test dependencies. Do not add production libraries here.
 *  - If new test libraries are adopted project-wide, declare them here once and consume them via
 *    `api(projects.utils.testCommons)` in downstream modules.
 *  - Keep in mind that since this module is not published, external consumers of Keen will not have access to its
 *    dependencies.
 *  - Since this is an internal API module, explicit public APIs are not required.
 */


plugins {
    id("keen.jvm")
    alias(libs.plugins.detekt)
}

dependencies {
    // House BOM/platform for consistent versions (Kotest, etc.)
    implementation(platform(projects.dependencyConstraints))

    // Test: Kotest (core, assertions, property)
    api(libs.bundles.testing)
    testImplementation(libs.bundles.testing)
    // Detekt formatting (ktlint rules packaged for Detekt)
    detektPlugins(libs.detekt.formatting)
}
