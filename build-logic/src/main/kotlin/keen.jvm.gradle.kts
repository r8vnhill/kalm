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
 *  - Set `knob.java.default` in gradle.properties or via CLI to override the default Java version
 *  - Example: `./gradlew build -Pknob.java.default=21`
 */

import utils.jvmTargetFor
import utils.resolveDefaultJavaVersion
import utils.setLanguageVersion

plugins {
    kotlin("jvm") // Apply the Kotlin JVM plugin to enable JVM compilation
}

// Lazily resolve the default Java version as a Provider<Int> (e.g., 17, 21, 22)
val defaultJava: Provider<Int> = providers.resolveDefaultJavaVersion()

val moduleAdditionFlag = "--add-modules"
val vectorModule = "jdk.incubator.vector"

// ------------------------
// Configure Java toolchain
// ------------------------
java.toolchain {
    setLanguageVersion(defaultJava)
}

// ---------------------------------------
// Configure Kotlin toolchain and compiler
// ---------------------------------------
kotlin {
    setLanguageVersion(defaultJava)

    compilerOptions {
        jvmTarget.set(defaultJava.map(::jvmTargetFor))

        optIn.add("kotlin.RequiresOptIn")
        freeCompilerArgs.addAll(
            "-Xcontext-parameters", // Enable context parameters
            "-Xjsr305=strict",      // Enable strict nullability checks
            "-Xjvm-default=all",    // Enable default methods in interfaces
            "-Xnested-type-aliases",
            "-Xjavac-arguments=$moduleAdditionFlag=$vectorModule"
        )

        val wError = providers.gradleProperty("kotlin.warningsAsErrors")
            .map(String::toBoolean).orElse(false)
        allWarningsAsErrors.set(wError)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(defaultJava.map { it })
    // allow referencing the incubator module at compile time
    options.compilerArgs.addAll(listOf(moduleAdditionFlag, vectorModule))
}
