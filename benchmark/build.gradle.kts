/*
 * =========================================================================
 * :benchmark — library of benchmarking helpers for consumers of the project
 * =========================================================================
 * Users add this module as a dependency to write/run their own benchmarks.
 */

plugins {
    id("keen.library")
    id("keen.jvm")
    alias(libs.plugins.detekt)
}

dependencies {
    implementation(platform(projects.dependencyConstraints))
    api(projects.core)
    testImplementation(libs.bundles.kotest)
}
