/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */
@file:Suppress("UnstableApiUsage")

/*
 * ## Build Logic Module
 *
 * This module hosts reusable Gradle convention plugins and shared build logic.
 *
 * ### Design Goals:
 * *
 * - Encapsulate cross-project build conventions.
 * - Avoid `allprojects` / `subprojects` anti-patterns.
 * - Provide testable, versioned, reproducible build configuration.
 * - Keep build infrastructure modular and maintainable.
 *
 * ### This project is compiled using the `kotlin-dsl` plugin, enabling:
 * *
 * - Precompiled script plugins
 * - Type-safe access to Gradle APIs
 * - Strong IDE support and refactoring safety
 */

plugins {
    /**
     * Enables writing Gradle plugins and build logic in Kotlin.
     *
     * ## Required for:
     *
     * - Precompiled script plugins
     * - Type-safe DSL access
     * - Convention plugin development
     */
    `kotlin-dsl`
}

/*
 * ## Build Logic Dependencies
 *
 * These dependencies allow convention plugins defined in this module to:
 * - Apply and configure external Gradle plugins
 * - Reference plugin classes at compile time
 *
 * ### Scope rationale:
 * *
 * - `implementation` is used when the build logic references plugin types directly.
 * - If only plugin IDs were applied (without type references), `compileOnly` could be considered.
 */

dependencies {

    /**
     * Kotlin Gradle Plugin: Required to configure Kotlin-related build conventions inside custom convention plugins.
     */
    implementation(libs.kotlin.gradle.plugin)

    /**
     * Detekt Gradle Plugin: Enables static analysis conventions.
     */
    implementation(libs.detekt.gradle.plugin)

    /**
     * Ben Manes Versions Plugin: Enables dependency update analysis and reporting.
     */
    implementation(libs.ben.manes.versions.plugin)

    /**
     * RedMadRobot Detekt Rules:
     *
     * Used by the `kalm.detekt-redmadrobot` convention plugin.
     *
     * ## Note:
     *
     * This plugin must still be applied from the Gradle Plugin Portal in consuming projects. It is declared here so
     * convention plugins can reference its classes safely.
     */
    implementation(libs.detekt.redmadrobot.plugin)
}

/*
 * ## Functional Testing (Gradle TestKit)
 *
 * This module includes a dedicated functional test suite to validate convention plugins end-to-end using Gradle
 * TestKit.
 *
 * ### Why functional tests?
 *
 * - Unit tests cannot validate actual Gradle task graphs.
 * - Convention plugins must be tested against real build execution.
 * - Ensures compatibility across Gradle upgrades.
 *
 * These tests execute isolated builds in temporary directories.
 */

testing {
    suites {
        register<JvmTestSuite>("functionalTest") {

            /**
             * Uses JUnit Jupiter for test execution.
             */
            useJUnitJupiter()

            dependencies {

                /**
                 * Gradle TestKit: Allows executing builds programmatically.
                 */
                implementation(gradleTestKit())

                /**
                 * JUnit Jupiter API & Engine: Required for writing and executing tests.
                 */
                implementation(libs.junit.jupiter.api)
                runtimeOnly(libs.junit.jupiter.engine)
            }
        }
    }
}

/*
 * Integrate functional tests into the standard verification lifecycle.
 *
 * ## Ensures:
 *
 *     ./gradlew check
 *
 * runs both unit and functional tests.
 */
tasks.named("check").configure {
    dependsOn(testing.suites.named("functionalTest"))
}

/*
 * Improve discoverability in task listings.
 *
 * Some Gradle versions generate either:
 * - functionalTest
 * - functionalTestTest
 *
 * This block normalizes group/description metadata.
 */
tasks.matching {
    it.name == "functionalTest" || it.name == "functionalTestTest"
}.configureEach {
    group = "verification"
    description = "Runs Gradle TestKit functional tests for convention plugins."
}

/*
 * ## Toolchain Configuration
 *
 * Ensures consistent Java toolchains across:
 * - Java compilation
 * - Kotlin compilation
 *
 * ### Rationale:
 *
 * - Prevents environment drift.
 * - Ensures reproducible bytecode.
 * - Avoids reliance on developer-local JDKs.
 *
 * The default version is controlled by:
 *
 *   gradle.properties:
 *     buildlogic.java.version=22
 *
 * Fallback:
 * - Defaults to Java 22 if the property is absent.
 */

val buildLogicDefaultJavaVersion: Int = providers
    .gradleProperty("buildlogic.java.version")
    .map(String::toInt)
    .orElse(22)
    .get()

/*
 * Configure Java toolchain.
 */
java {
    toolchain {
        setDefaultJavaVersion(buildLogicDefaultJavaVersion)
    }
}

/*
 * Configure Kotlin JVM toolchain.
 *
 * Uses the same version to guarantee alignment.
 */
kotlin {
    jvmToolchain(buildLogicDefaultJavaVersion)
}

/**
 * Extension function to set the Java language version for a [JavaToolchainSpec].
 *
 * Extracted for:
 * - Reusability
 * - Cleaner DSL blocks
 * - Avoiding inline property resolution duplication
 *
 * @param defaultVersion The Java language version to enforce.
 */
fun JavaToolchainSpec.setDefaultJavaVersion(defaultVersion: Int) {
    languageVersion.set(JavaLanguageVersion.of(defaultVersion))
}
