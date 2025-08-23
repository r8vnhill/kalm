/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 *
 * === Summary ===
 * This build script configures the *build-logic* included build that contains your precompiled / convention plugins.
 * Build logic runs **inside the Gradle daemon’s JVM**, so we:
 *
 * 1) Resolve a single “default Java” version from a Gradle property (`knob.java.default`) with a safe fallback to the
 *    **current Gradle JVM** major version.
 * 2) Provide tiny helpers to apply that version to both Java and Kotlin *toolchains*.
 * 3) Pin Kotlin bytecode (`jvmTarget`) to **22** for broader IDE/daemon compatibility, regardless of which JDK compiles
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

//#region Default Java Version
val defaultJavaProp = providers.gradleProperty("knob.java.default")
val runtimeJavaVersion = JavaVersion.current().majorVersion

if (!defaultJavaProp.isPresent) {
    logger.lifecycle(
        """
        ⚠️  No 'knob.java.default' property found.
        
        Steps to fix:
          • Add to root gradle.properties:
                knob.java.default=22
          • OR set globally in ~/.gradle/gradle.properties
          • OR pass via CLI:
                ./gradlew build -Pknob.java.default=22

        Falling back to current Gradle JVM: $runtimeJavaVersion
        """.trimIndent()
    )
}

val defaultJava: Provider<Int> = defaultJavaProp
    .map(String::toInt)
    .orElse(runtimeJavaVersion.toInt())
//#endregion

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
        jvmTarget.set(defaultJava.map { JvmTarget.valueOf("JVM_$it") })
    }
}

// Warn on incompatible versions
if (!JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_22)) {
    logger.warn("Build-logic is optimized for Gradle JVM 22+. Current: ${JavaVersion.current()}")
}
