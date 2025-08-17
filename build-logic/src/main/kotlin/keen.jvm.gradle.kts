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
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
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
                v >= 24 -> JvmTarget.JVM_24
                v == 23 -> JvmTarget.JVM_23
                v == 22 -> JvmTarget.JVM_22
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

        // Enable context parameters and nested type aliases (still requires X-flag as of Kotlin 2.2.x)
        freeCompilerArgs.addAll("-Xcontext-parameters", "-Xnested-type-aliases")

        // Configure warnings-as-errors via a Gradle property (-Pkotlin.warningsAsErrors)
        val wError = providers.gradleProperty("kotlin.warningsAsErrors")
            .map(String::toBoolean)
            .orElse(false)
        allWarningsAsErrors.set(wError)
    }
}


// compute a JavaLanguageVersion from property
val defaultJavaLang: Provider<JavaLanguageVersion> = defaultJava.map(JavaLanguageVersion::of)

// get a launcher for the chosen toolchain
val toolchains = project.extensions.getByType(JavaToolchainService::class.java)
val testLauncher = toolchains.launcherFor {
    languageVersion.set(defaultJavaLang)
}

tasks.withType<Test>().configureEach {
    // run tests on the same toolchain JDK, not the Gradle daemon JDK
    javaLauncher.set(testLauncher)

    useJUnitPlatform()

    // add the incubator module at runtime
    jvmArgs("--add-modules=jdk.incubator.vector")
}

tasks.withType<JavaCompile>().configureEach {
    // allow referencing the incubator module at compile time
    options.compilerArgs.addAll(listOf("--add-modules", "jdk.incubator.vector"))
}

tasks.withType<KotlinCompile>().configureEach {
    // forward to javac for Kotlin sources that touch JDK modules
    compilerOptions.freeCompilerArgs.add("-Xjavac-arguments=--add-modules=jdk.incubator.vector")
}
