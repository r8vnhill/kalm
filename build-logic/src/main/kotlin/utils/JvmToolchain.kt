/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

package utils

import org.gradle.api.JavaVersion
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * Lightweight facade to lazily resolve the default Java *major* version from Gradle properties.
 *
 * Source of truth:
 *  - -Pknob.java.default=<N>, e.g., 17, 21, 22
 * Fallback:
 *  - DEFAULT_JAVA_VERSION when the property is absent
 */
interface ProviderFactoryRef {

    /** Returns a provider for a Gradle property (empty if not defined). */
    fun gradleProperty(name: String): Provider<String>

    /** Lazily resolves the default Java version with validation. */
    fun resolveDefaultJavaVersion(): Provider<Int> =
        gradleProperty(PROP_JAVA_DEFAULT)
            .warnOnMissing()
            .map(::parseJavaVersion)
            .orElse(DEFAULT_JAVA_VERSION)

    /**
     * Returns the default language version that build logic should use when configuring toolchains.
     */
    fun defaultJavaLanguageVersion(): JavaLanguageVersion = JavaLanguageVersion.of(DEFAULT_JAVA_VERSION)

    /**
     * If the backing property is missing or blank, log a friendly note once at configuration time,
     * then keep behaving lazily (no hard failure, still returns the original Provider).
     */
    fun JavaToolchainSpec.setDefaultJavaVersion(): Unit =
        languageVersion.set(defaultJavaLanguageVersion())
}

/** Public, minimal surface for consumers: resolve once, reuse everywhere. */
fun ProviderFactory.resolveDefaultJavaVersion(): Provider<Int> =
    asRef.resolveDefaultJavaVersion()
