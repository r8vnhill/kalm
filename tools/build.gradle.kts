/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

/**
 * # :tools (build.gradle.kts)
 *
 * The `:tools` module provides reusable JVM-based tooling components that support the broader KALM ecosystem.
 *
 * ## Purpose
 *
 * ### This module is intended for:
 *
 * - Shared utilities used across build logic or runtime modules.
 * - Small, focused tools that support development, quality checks, automation, or internal workflows.
 * - Logic that should remain decoupled from application entry points.
 *
 * ### The module is designed to be:
 *
 * - **Modular** --- no implicit coupling to application modules.
 * - **Reusable** --- logic can be consumed independently.
 * - **Testable** --- all behavior should be verifiable in isolation.
 * - **Lightweight** --- avoid unnecessary runtime dependencies.
 *
 * ## Applied Convention Plugins
 *
 * ### `kalm.library`
 *
 * Applies standard library conventions:
 *
 * - Reproducible build configuration
 * - Strict dependency management
 * - Consistent publication metadata
 * - Shared quality gates
 *
 * This ensures all library modules behave consistently and can be versioned independently.
 *
 * ### `kalm.jvm`
 *
 * Applies JVM-specific conventions:
 *
 * - Toolchain configuration
 * - Kotlin/JVM defaults
 * - Compiler options alignment
 *
 * This guarantees consistent bytecode targets and avoids environment drift.
 *
 * ## Testing Strategy
 *
 * The module uses Kotest via:
 *
 * `testImplementation(libs.bundles.kotest)`
 *
 * Recommended testing practices:
 *
 * - Prefer **Property-Based Testing (PBT)** for pure utilities.
 * - Use **Data-Driven Testing (DDT)** for edge-case coverage.
 * - Keep test functions small and deterministic.
 * - Avoid hidden global state.
 *
 * Tools modules should prioritize:
 *
 * - Determinism
 * - Side effect isolation
 * - Clear input/output contracts
 *
 * ## Design Guidelines
 *
 * - Keep classes and functions small (< 25 lines where practical).
 * - Favor pure functions and immutability.
 * - Avoid unnecessary abstraction layers.
 * - Prefer composition to inheritance.
 * - Do not introduce framework-level dependencies unless strictly necessary.
 *
 * ## When to Add Dependencies
 *
 * Only introduce a dependency if it:
 *
 * - Significantly reduces complexity,
 * - Improves correctness or safety,
 * - Avoids reimplementing well-tested behavior,
 * - Or enables better testing.
 *
 * Tooling modules should remain lightweight.
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

tasks.register<JavaExec>("runHadolintCli") {
    group = "verification"
    description = "Runs cl.ravenhill.kalm.tools.hadolint.HadolintCli"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("cl.ravenhill.kalm.tools.hadolint.HadolintCli")
}

tasks.register<JavaExec>("runLocksCli") {
    group = "verification"
    description = "Runs cl.ravenhill.kalm.tools.locks.DependencyLocksCli"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("cl.ravenhill.kalm.tools.locks.DependencyLocksCli")
}
