/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 *
 * === Summary ===
 * This build script configures the *build-logic* included build that contains your precompiled / convention plugins.
 * Build logic runs **inside the Gradle daemon’s JVM**, so we:
 *
 * 1) Resolve a single “default Java” version from a Gradle property (`java.lts`) with a safe fallback to the
 *    **current Gradle JVM** major version.
 * 2) Provide tiny helpers to apply that version to both Java and Kotlin *toolchains*.
 * 3) Pin Kotlin bytecode (`jvmTarget`) to **21** for broader IDE/daemon compatibility, regardless of which JDK compiles
 *    the build logic.
 *
 * === Notes ===
 * Keeping build-logic compatible with the Gradle JVM (often 21 LTS) avoids IDE resolution issues while allowing module
 * toolchains to be newer.
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

// === Plugins ===
// `kotlin-dsl` enables writing Gradle build logic using Kotlin (precompiled scripts).
plugins {
    `kotlin-dsl`
}

// === Dependencies for the build-logic project itself (not your application modules). ===
// We add the Kotlin Gradle Plugin so convention plugins can interact with Kotlin DSL.
dependencies {
    implementation(libs.kotlin.gradle.plugin)
}

// === Default Java Version ===
// Resolve the default Java version for toolchains used by *build-logic*:
// - Prefer a Gradle property `java.lts` (e.g., 21 or 22)
// - Fall back to the current Gradle JVM's major version if not provided
//
// Rationale: Matching the Gradle JVM by default keeps build-logic loadable in IDEs running Gradle with 21, while still
// allowing explicit override when needed.
val defaultJava: Provider<Int> = providers
    .gradleProperty("java.lts")
    .map(String::toInt)
    .orElse(JavaVersion.current().majorVersion.toInt())

// === Helper extensions to apply the resolved Java version to both Java and Kotlin toolchains. ===

/** Applies a given Provider<Int> (Java major version) to this Java toolchain spec. */
fun JavaToolchainSpec.setDefaultJavaVersion(versionProvider: Provider<Int>) {
    languageVersion.set(versionProvider.map(JavaLanguageVersion::of))
}

/** Applies a given Provider<Int> (Java major version) to the Kotlin JVM toolchain. */
fun KotlinJvmProjectExtension.setDefaultJavaVersion(versionProvider: Provider<Int>) {
    jvmToolchain {
        languageVersion.set(versionProvider.map(JavaLanguageVersion::of))
    }
}

// === Apply the toolchain configuration to the *build-logic* itself. ===
// - We use the resolved `defaultJava` value for both Java and Kotlin toolchains.
// - We explicitly set Kotlin's `jvmTarget` to JVM_21 so produced class files are compatible with 21+ even if built
//   using a newer JDK. (A newer JVM can run older classfile targets; this keeps IDEs happy c:)
java.toolchain {
    setDefaultJavaVersion(defaultJava)
}

kotlin {
    setDefaultJavaVersion(defaultJava)

    compilerOptions {
        // Ensure bytecode target is JVM 21 for broader compatibility with Gradle/IDEs, regardless of the actual JDK
        // used to compile the build-logic.
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

// Warn on incompatible versions
if (!JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_21)) {
    logger.warn("Build-logic is optimized for Gradle JVM 21+. Current: ${JavaVersion.current()}")
}
