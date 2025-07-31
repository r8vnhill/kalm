@file:Suppress("SpellCheckingInspection")

/*
 * =======================================
 * keen.jvm — JVM/Kotlin convention plugin
 * =======================================
 * This convention plugin configures:
 *  1. Java and Kotlin toolchains from a single property-based source
 *  2. Kotlin bytecode (jvmTarget) based on the requested Java version
 *  3. Common compiler flags for experimental APIs and context parameters
 *
 * --------
 * Behavior
 * --------
 *  - Reads the desired Java version lazily via `providers.resolveDefaultJavaVersion()`
 *  - Applies it to both Java and Kotlin toolchains
 *  - Derives the Kotlin `jvmTarget` from the Java version
 *  - Allows enabling warnings-as-errors via -Pkotlin.warningsAsErrors
 *
 * -----
 * Usage
 * -----
 *  - Set `keen.java.default` in gradle.properties or via CLI to override the default Java version
 *  - Example: `./gradlew build -Pkeen.java.default=21`
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import utils.resolveDefaultJavaVersion
import utils.setDefaultJavaVersion

plugins {
    kotlin("jvm") // Apply the Kotlin JVM plugin to enable JVM compilation
}

// Lazily resolve the default Java version as a Provider<Int> (e.g., 17, 21, 22)
val defaultJava: Provider<Int> = providers.resolveDefaultJavaVersion()

// ------------------------
// Configure Java toolchain
// ------------------------
java.toolchain {
    // Apply the default Java version to the Java toolchain
    // Using providers allows lazy evaluation and configuration-cache safety
    setDefaultJavaVersion(providers)
}

// ---------------------------------------
// Configure Kotlin toolchain and compiler
// ---------------------------------------
kotlin {
    // Apply the same default Java version to the Kotlin JVM toolchain
    setDefaultJavaVersion(providers)

    compilerOptions {
        // Map the requested Java version to a supported Kotlin JVM target
        // Fallback to JVM_1_8 if below 17 to ensure compatibility
        val requestedTarget = defaultJava.map { v ->
            when {
                v >= 22 -> JvmTarget.JVM_22
                v == 21 -> JvmTarget.JVM_21
                v == 20 -> JvmTarget.JVM_20
                v == 19 -> JvmTarget.JVM_19
                v == 18 -> JvmTarget.JVM_18
                v == 17 -> JvmTarget.JVM_17
                else    -> JvmTarget.JVM_1_8
            }
        }
        jvmTarget.set(requestedTarget)

        // Opt into experimental Kotlin APIs
        optIn.add("kotlin.RequiresOptIn")

        // Enable context parameters (still requires X-flag as of Kotlin 2.2.x)
        freeCompilerArgs.add("-Xcontext-parameters")

        // Configure warnings-as-errors via a Gradle property (-Pkotlin.warningsAsErrors)
        val wError = providers.gradleProperty("kotlin.warningsAsErrors")
            .map(String::toBoolean)
            .orElse(false)
        allWarningsAsErrors.set(wError)
    }
}
