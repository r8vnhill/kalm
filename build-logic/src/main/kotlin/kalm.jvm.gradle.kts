/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

import utils.JvmToolchain.setDefaultJavaVersion
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// region SHARED KOTLIN BUILD CONFIGURATION
// Apply shared Kotlin build configuration via convention plugin.
plugins {
    kotlin("jvm")
}
// endregion

// region JAVA TOOLCHAIN

// Configure Java toolchain for consistency across environments.
//
// Ensures that both Java and Kotlin compilers use the same Java version, as defined in the shared [JvmToolchain]
// utility.
java.toolchain {
    setDefaultJavaVersion() // Applies the default Java version (e.g., Java 22)
}

kotlin.jvmToolchain {
    setDefaultJavaVersion() // Applies the same version for Kotlin JVM compilation
}
// endregion

// region KOTLIN COMPILER OPTIONS

private val warningsAsErrorsSignals = setOf("true", "strict", "enforce", "all")

val warningsAsErrors = project.findProperty("kalm.warningsAsErrors")?.toString()?.lowercase()
val commonCompilerArgs = listOf("-Xjsr305=strict")

extensions.configure<KotlinJvmProjectExtension>("kotlin") {
    compilerOptions {
        freeCompilerArgs.addAll(commonCompilerArgs)
        if (warningsAsErrors in warningsAsErrorsSignals) {
            allWarningsAsErrors.set(true)
        }
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.freeCompilerArgs.addAll(commonCompilerArgs)
    if (warningsAsErrors in warningsAsErrorsSignals) {
        compilerOptions.allWarningsAsErrors.set(true)
    }
}

// endregion
