/*
 * Copyright (c) 2025, Ignacio Slater M.
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
 *  - -Pkeen.java.default=<N>, e.g., 17, 21, 22
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
     * If the backing property is missing or blank, log a friendly note once at configuration time,
     * then keep behaving lazily (no hard failure, still returns the original Provider).
     */
    private fun Provider<String>.warnOnMissing(): Provider<String> {
        // Safe to inspect without forcing resolution
        val missing = !isPresent
        val blank = !missing && (orNull?.isBlank() == true)

        if (missing || blank) {
            val logger = Logging.getLogger(ProviderFactoryRef::class.java)
            val gradleJvm = JavaVersion.current().majorVersion
            val example = DEFAULT_JAVA_VERSION

            // lifecycle → visible but not "warn" (as requested)
            logger.lifecycle(
                """
                ⚠ No '$PROP_JAVA_DEFAULT' property found${if (blank) " (value was blank)" else ""}.
                   Falling back to the Gradle JVM version ($gradleJvm) or framework default ($example).

                How to set it:
                  • Root gradle.properties:
                        $PROP_JAVA_DEFAULT=$example
                  • Or user-wide ~/.gradle/gradle.properties:
                        $PROP_JAVA_DEFAULT=$example
                  • Or per-invocation CLI:
                        ./gradlew build -P$PROP_JAVA_DEFAULT=$example
                """.trimIndent()
            )
        }
        return this
    }

    companion object {
        /** Gradle property that selects the default Java major version. */
        const val PROP_JAVA_DEFAULT: String = "keen.java.default"

        /** Framework default when the property is absent. */
        const val DEFAULT_JAVA_VERSION: Int = 22

        /** Acceptable major version range. */
        val VALID_RANGE: IntRange = 8..99

        /** Parses and validates a Java major version (e.g., "21" → 21). */
        fun parseJavaVersion(raw: String): Int {
            val value = raw.trim().toIntOrNull()
                ?: error("Invalid $PROP_JAVA_DEFAULT value: '$raw' (must be an integer, e.g., 17, 21, 22).")
            require(value in VALID_RANGE) {
                "Unsupported Java version: $value. Expected a major version in $VALID_RANGE (e.g., 17, 21, 22)."
            }
            return value
        }
    }
}

/** Wrap Gradle’s ProviderFactory in ProviderFactoryRef. */
val ProviderFactory.asRef: ProviderFactoryRef
    get() = object : ProviderFactoryRef {
        override fun gradleProperty(name: String) = this@asRef.gradleProperty(name)
    }

/** Map a Provider<Int> major version to toolchains (keeps laziness). */
fun JavaToolchainSpec.setLanguageVersion(version: Provider<Int>) {
    languageVersion.set(version.map(JavaLanguageVersion::of))
}

fun KotlinJvmProjectExtension.setLanguageVersion(version: Provider<Int>) {
    jvmToolchain { languageVersion.set(version.map(JavaLanguageVersion::of)) }
}

/** Public, minimal surface for consumers: resolve once, reuse everywhere. */
fun ProviderFactory.resolveDefaultJavaVersion(): Provider<Int> =
    asRef.resolveDefaultJavaVersion()
