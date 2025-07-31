package utils

import org.gradle.api.logging.Logging
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import utils.ProviderFactoryRef.Companion.DEFAULT_JAVA_VERSION
import utils.ProviderFactoryRef.Companion.PROP_JAVA_DEFAULT
import utils.ProviderFactoryRef.Companion.VALID_RANGE

/**
 * Utility object to configure consistent Java/Kotlin toolchain versions in Gradle builds.
 *
 * Provides extension functions to simplify and unify how the Java version is set for both [JavaToolchainSpec] and
 * [KotlinJvmProjectExtension].
 *
 * Enables both eager and lazy configuration (via [Provider]) and adds input validation.
 */
object JvmToolchain {

    /**
     * Logger for this utility object.
     */
    private val logger = Logging.getLogger(JvmToolchain::class.java)

    /**
     * Sets the Java language version for a [JavaToolchainSpec] using an eager integer value.
     *
     * @param version The Java version to use (e.g., 17, 21, 22).
     * @throws IllegalArgumentException If the version is not in the valid range (8–99).
     */
    fun JavaToolchainSpec.setDefaultJavaVersion(version: Int) {
        validate(version)
        languageVersion.set(JavaLanguageVersion.of(version))
    }

    /**
     * Sets the Java language version for a [JavaToolchainSpec] using a [Provider]-wrapped value.
     *
     * This form supports lazy evaluation and configuration caching.
     *
     * @param versionProvider A provider of the Java version to use (e.g., 17, 21, 22).
     */
    fun JavaToolchainSpec.setDefaultJavaVersion(versionProvider: Provider<Int>) {
        languageVersion.set(versionProvider.map { version ->
            validate(version)
            JavaLanguageVersion.of(version)
        })
    }

    /**
     * Sets the Java language version for the Kotlin JVM toolchain using an eager integer value.
     *
     * @receiver The [KotlinJvmProjectExtension] to configure.
     * @param version The Java version to use (e.g., 17, 21, 22).
     * @throws IllegalArgumentException If the version is not in the valid range (8–99).
     */
    fun KotlinJvmProjectExtension.setDefaultJavaVersion(version: Int) {
        validate(version)
        jvmToolchain {
            languageVersion.set(JavaLanguageVersion.of(version))
        }
    }

    /**
     * Sets the Java language version for the Kotlin JVM toolchain using a [Provider]-wrapped value.
     *
     * This form supports lazy evaluation and configuration caching.
     *
     * @receiver The [KotlinJvmProjectExtension] to configure.
     * @param versionProvider A provider of the Java version to use (e.g., 17, 21, 22).
     */
    fun KotlinJvmProjectExtension.setDefaultJavaVersion(versionProvider: Provider<Int>) {
        jvmToolchain {
            languageVersion.set(versionProvider.map { v ->
                validate(v)
                JavaLanguageVersion.of(v)
            })
        }
    }

    /**
     * Validates that the given Java version is within an acceptable range (8–99).
     *
     * Prints a warning if the version is less than 17, recommending LTS versions.
     *
     * @throws IllegalArgumentException If the version is outside the valid range.
     */
    private fun validate(version: Int) {
        require(version in 8..99) {
            "Unsupported Java version: $version. Expected a major version (e.g., 17, 21, 22)."
        }
        if (version < 17) {
            logger.warn(
                "Configuring toolchain to Java $version — consider using an LTS (17/21/22+) unless necessary."
            )
        }
    }
}

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
        private const val PROP_JAVA_DEFAULT: String = "keen.java.default"

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
    }
        .resolveDefaultJavaVersion()
}
