package utils

import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import utils.ProviderFactoryRef.Companion.DEFAULT_JAVA_VERSION
import utils.ProviderFactoryRef.Companion.PROP_JAVA_DEFAULT
import utils.ProviderFactoryRef.Companion.VALID_RANGE

/**
 * Lightweight facade over Gradle's [ProviderFactory] to resolve configuration properties as [Provider]s, plus a
 * ready-to-use resolver for the default Java major version.
 */
interface ProviderFactoryRef {

    /**
     * Returns a provider for a Gradle property (e.g., `providers.gradleProperty(name)`).
     *
     * The provider will be empty if the property is not defined.
     */
    fun gradleProperty(name: String): Provider<String>

    /**
     * Resolve the default Java *major* version as a [Provider].
     *
     * ## Source of truth:
     *  - Reads the Gradle property [PROP_JAVA_DEFAULT] (e.g., `-Pkeen.java.default=21`).
     *  - Falls back to [DEFAULT_JAVA_VERSION] when the property is not present.
     *
     * ## Validation:
     *  - Accepts values in the inclusive range [8..99] (e.g., 17, 21, 22).
     *  - Throws an [IllegalArgumentException] if the property is present but invalid.
     *
     * ## Usage:
     *  ```
     *  val defaultJava = providers.asRef().resolveDefaultJavaVersion()
     *  java.toolchain { languageVersion.set(defaultJava.map(JavaLanguageVersion::of)) }
     *  kotlin { jvmToolchain { languageVersion.set(defaultJava.map(JavaLanguageVersion::of)) } }
     *  ```
     */
    @Suppress("SpellCheckingInspection")
    fun resolveDefaultJavaVersion(): Provider<Int> =
        gradleProperty(PROP_JAVA_DEFAULT)
            .map(::parseJavaVersion) // validate & parse when provided
            .orElse(DEFAULT_JAVA_VERSION) // fall back lazily
    // Note: .orElse(...) keeps it a Provider<Int>, preserving laziness

    companion object {
        /** A constant representing the name of the Gradle property used to configure the default Java version. */
        private const val PROP_JAVA_DEFAULT: String = "java.lts"

        /** Framework default when the property is absent. */
        const val DEFAULT_JAVA_VERSION: Int = 22

        /** Acceptable range for Java *major* versions to catch obvious typos early. */
        private val VALID_RANGE = 8..99

        /**
         * Parse and validate a Java major version from a string (e.g., "21" -> 21).
         * @throws IllegalArgumentException if the value is not an integer in [VALID_RANGE].
         */
        private fun parseJavaVersion(raw: String): Int {
            val trimmed = raw.trim()
            val value = requireNotNull(trimmed.toIntOrNull()) {
                "Invalid $PROP_JAVA_DEFAULT value: '$raw' (must be an integer, e.g., 17, 21, 22)"
            }
            require(value in VALID_RANGE) {
                "Unsupported Java version: $value. Expected a major version in $VALID_RANGE (e.g., 17, 21, 22)."
            }
            return value
        }
    }
}

// === Convenience bridge from Gradle's ProviderFactory to ProviderFactoryRef. ===
// This lets you write `providers.asRef.resolveDefaultJavaVersion()` in your build/convention plugins.

/**
 * Wrap Gradle’s [ProviderFactory] in a [ProviderFactoryRef] to reuse the resolver helpers.
 */
val ProviderFactory.asRef: ProviderFactoryRef
    get() = object : ProviderFactoryRef {
        override fun gradleProperty(name: String): Provider<String> =
            this@asRef.gradleProperty(name)
    }

// === Tiny helper extensions to apply the resolved version to toolchains. ===

/**
 * Apply a provider-backed Java version to a Java toolchain.
 */
fun JavaToolchainSpec.setLanguageVersion(version: Provider<Int>) {
    languageVersion.set(version.map(JavaLanguageVersion::of))
}

/**
 * Apply a provider-backed Java version to the Kotlin JVM toolchain.
 */
fun KotlinJvmProjectExtension.setLanguageVersion(version: Provider<Int>) {
    jvmToolchain { languageVersion.set(version.map(JavaLanguageVersion::of)) }
}

/**
 * Extension that exposes the “default Java version” as a lazy Provider<Int>, suitable for wiring directly into Gradle
 * toolchain configuration.
 */
fun ProviderFactory.resolveDefaultJavaVersion(): Provider<Int> {
    // Wrap the Gradle ProviderFactory in our lightweight facade (ProviderFactoryRef) so we can reuse the common
    // resolver/validation logic implemented there.
    return object : ProviderFactoryRef {
        override fun gradleProperty(name: String): Provider<String> {
            // Delegate to the real ProviderFactory for property lookup, keeping laziness intact.
            return this@resolveDefaultJavaVersion.gradleProperty(name)
        }
    }.resolveDefaultJavaVersion()
}

/**
 * Configure this [JavaToolchainSpec] with the default Java *major* version resolved from [ProviderFactory].
 *
 * The version is obtained via `providers.resolveDefaultJavaVersion()` which:
 * - Reads the Gradle property `"keen.java.default"` when present.
 * - Lazily falls back to the framework default (e.g., 22) when absent.
 *
 * This helper preserves laziness and is compatible with configuration cache.
 *
 * ## Usage:
 * ```kotlin
 * java.toolchain {
 *   setDefaultJavaVersion(providers)
 * }
 * ```
 *
 * @receiver the [JavaToolchainSpec] being configured.
 * @param providers the Gradle [ProviderFactory] used to resolve the default Java version lazily.
 */
fun JavaToolchainSpec.setDefaultJavaVersion(providers: ProviderFactory) =
    setLanguageVersion(providers.resolveDefaultJavaVersion())

/**
 * Configure the Kotlin JVM toolchain with the default Java *major* version resolved from [ProviderFactory].
 *
 * The version is obtained via `providers.resolveDefaultJavaVersion()` which:
 * - Reads the Gradle property `"keen.java.default"` when present.
 * - Lazily falls back to the framework default (e.g., 22) when absent.
 *
 * Applies the resolved version to `kotlin.jvmToolchain { languageVersion.set(...) }`,
 * preserving laziness and remaining configuration-cache friendly.
 *
 * ## Usage:
 * ```kotlin
 * kotlin {
 *   setDefaultJavaVersion(providers)
 * }
 * ```
 *
 * @receiver the [KotlinJvmProjectExtension] to configure.
 * @param providers the Gradle [ProviderFactory] used to resolve the default Java version lazily.
 */
fun KotlinJvmProjectExtension.setDefaultJavaVersion(providers: ProviderFactory) =
    setLanguageVersion(providers.resolveDefaultJavaVersion())
