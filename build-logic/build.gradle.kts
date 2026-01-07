/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

// Apply required plugins.
// `kotlin-dsl` enables writing Gradle build logic using Kotlin.
// This is necessary for convention plugins and precompiled script plugins.
plugins {
    `kotlin-dsl`
}

dependencies {
    // Adds the Kotlin Gradle Plugin to enable use in custom build logic and DSL extensions
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
    implementation("com.github.ben-manes:gradle-versions-plugin:0.53.0")
    // Add RedMadRobot Detekt plugin for the kalm.detekt-redmadrobot convention
    // Note: This plugin must be applied from Gradle Plugin Portal in consuming projects
    // We declare it here so the convention plugin can reference it
    implementation("com.redmadrobot.detekt:com.redmadrobot.detekt.gradle.plugin:0.19.1")
}

// Register a Gradle TestKit-based functional test suite to validate convention plugins end-to-end.
testing {
    suites {
        register<JvmTestSuite>("functionalTest") {
            useJUnitJupiter()

            dependencies {
                // Gradle TestKit for executing builds in isolation
                implementation(gradleTestKit())
                // JUnit Jupiter API & Engine for writing/running functional tests
                implementation(libs.junit.jupiter.api)
                runtimeOnly(libs.junit.jupiter.engine)
            }
        }
    }
}

// Include functional tests in the standard verification lifecycle
tasks.named("check").configure {
    dependsOn(testing.suites.named("functionalTest"))
}

// Configure toolchains to consistently use Java 22 across both
// Java and Kotlin compiler settings.
with(JvmToolchain) {
    java {
        toolchain {
            setDefaultJavaVersion()
        }
    }

    kotlin {
        jvmToolchain {
            setDefaultJavaVersion()
        }
    }
}

// Toolchain configuration utility for Gradle builds.
// Defines a reusable extension to consistently apply a Java version.
/**
 * Utility for configuring a consistent Java toolchain version across Gradle builds logic.
 *
 * Provides a reusable extension to set the default Java language version used across both Java and Kotlin toolchains.
 *
 * @property DEFAULT_JAVA_VERSION The Java language version applied across all build logic (Java 22).
 */
object JvmToolchain {
    private const val DEFAULT_JAVA_VERSION = 22

    /**
     * Sets the language version of the current Java toolchain to [DEFAULT_JAVA_VERSION].
     *
     * Applicable to both [JavaPluginExtension.toolchain] and
     * [org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension.jvmToolchain].
     *
     * @receiver The [JavaToolchainSpec] instance being configured.
     */
    fun JavaToolchainSpec.setDefaultJavaVersion(): Unit =
        languageVersion.set(JavaLanguageVersion.of(DEFAULT_JAVA_VERSION))
}
