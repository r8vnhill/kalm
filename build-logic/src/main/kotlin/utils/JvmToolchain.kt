/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package utils

import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import utils.JvmToolchain.DEFAULT_JAVA_VERSION

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
     * Returns the default language version that build logic should use when configuring toolchains.
     */
    fun defaultJavaLanguageVersion(): JavaLanguageVersion = JavaLanguageVersion.of(DEFAULT_JAVA_VERSION)

    /**
     * Sets the language version of the current Java toolchain to [DEFAULT_JAVA_VERSION].
     *
     * Applicable to both [JavaPluginExtension.toolchain] and [KotlinJvmProjectExtension.jvmToolchain].
     *
     * @receiver The [JavaToolchainSpec] instance being configured.
     */
    fun JavaToolchainSpec.setDefaultJavaVersion(): Unit =
        languageVersion.set(defaultJavaLanguageVersion())
}
