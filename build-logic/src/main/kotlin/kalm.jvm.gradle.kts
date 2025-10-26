/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

import utils.JvmToolchain.setDefaultJavaVersion

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
