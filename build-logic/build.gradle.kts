/*
 * Copyright (c) 2025, Ignacio Slater M.
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
    implementation(libs.ben.manes.versions.plugin)
    // Add RedMadRobot Detekt plugin for the kalm.detekt-redmadrobot convention
    // Note: This plugin must be applied from Gradle Plugin Portal in consuming projects
    // We declare it here so the convention plugin can reference it
    implementation(libs.detekt.redmadrobot.plugin)
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
// Read default Java version from a project property so it can be centralized in gradle.properties.
val BUILDLOGIC_DEFAULT_JAVA_VERSION: Int = (findProperty("buildlogic.java.version") as String?)?.toInt() ?: 22

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

/**
 * Extension function to set the Java language version for a JavaToolchainSpec.
 *
 * This reads the project property `buildlogic.java.version` at execution time
 * (not script compilation time), falling back to 22 when not provided. Using
 * an extension function avoids capturing the script instance in an object
 * initializer, which is disallowed in Gradle Kotlin script compilation.
 */
fun JavaToolchainSpec.setDefaultJavaVersion(): Unit {
    val defaultJavaVersion = (project.findProperty("buildlogic.java.version") as String?)?.toInt() ?: 22
    languageVersion.set(JavaLanguageVersion.of(defaultJavaVersion))
}
