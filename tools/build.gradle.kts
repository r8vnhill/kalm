/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

/**
 * # :tools (build.gradle.kts)
 *
 * The `:tools` module contains reusable JVM-based tooling components that support development workflows and internal
 * quality processes across the KALM ecosystem.
 *
 * This module is intentionally isolated from application runtime modules and is designed to provide deterministic,
 * composable, and testable tooling.
 *
 * ## Architectural Role
 *
 * `:tools` is a **library-style module** that hosts:
 *
 * - Standalone CLI utilities
 * - Internal automation helpers
 * - Validation and quality enforcement tools
 * - Development-time infrastructure logic
 *
 * ### It does **not**:
 *
 * - Contain application entry points
 * - Depend on UI layers
 * - Implicitly couple to runtime modules
 *
 * Tools should be independently executable and verifiable in isolation.
 *
 * ## CLI Task Registration
 *
 * This module exposes CLI entry points via `JavaExec` tasks.
 *
 * ## Running CLI Tools
 *
 * From the project root:
 *
 * ```
 * ./gradlew :tools:runHadolintCli
 * ./gradlew :tools:runLocksCli
 * ```
 *
 * If CLI arguments are required, they should be wired via `args(...)` or Gradle properties to maintain reproducibility.
 */

plugins {
    id("kalm.library")
    id("kalm.jvm")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.clikt)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.bundles.kotest)
}

fun registerCliTask(
    name: String,
    mainClassName: String,
    taskDescription: String
) {
    tasks.register<JavaExec>(name) {
        group = "verification"
        description = taskDescription
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass.set(mainClassName)
    }
}

registerCliTask(
    name = "runHadolintCli",
    mainClassName = "cl.ravenhill.kalm.tools.hadolint.HadolintCli",
    taskDescription = "Runs cl.ravenhill.kalm.tools.hadolint.HadolintCli"
)

registerCliTask(
    name = "runLocksCli",
    mainClassName = "cl.ravenhill.kalm.tools.locks.DependencyLocksCli",
    taskDescription = "Runs cl.ravenhill.kalm.tools.locks.DependencyLocksCli"
)
