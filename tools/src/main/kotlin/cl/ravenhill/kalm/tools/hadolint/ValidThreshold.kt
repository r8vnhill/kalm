package cl.ravenhill.kalm.tools.hadolint

import java.util.Locale.getDefault

/**
 * Canonical set of Hadolint failure thresholds supported by the CLI.
 *
 * This enum acts as the **single source of truth** for valid threshold values. It eliminates stringly-typed logic and
 * centralizes:
 *
 * - Accepted values
 * - Normalization rules
 * - Validation behavior
 *
 * Using an enum instead of raw strings provides:
 *
 * - Compile-time safety
 * - Exhaustive `when` support
 * - Safer refactoring
 * - Clear domain modeling
 *
 * @property value Canonical lowercase representation passed to Hadolint.
 */
enum class ValidThreshold(val value: String) {

    /** Fail only on errors. */
    ERROR("error"),

    /** Fail on warnings and errors (default behavior). */
    WARNING("warning"),

    /** Fail on informational issues and above. */
    INFO("info"),

    /** Fail on style issues and above. */
    STYLE("style"),

    /** Never fail regardless of findings. */
    IGNORE("ignore");

    companion object {

        /**
         * Lookup map from a canonical lowercase string -> enum instance.
         *
         * Constructed once at class initialization for O(1) resolution.
         */
        private val map = entries.associateBy(ValidThreshold::value)

        /**
         * Default threshold if the user does not specify one.
         */
        val default: ValidThreshold = WARNING

        /**
         * Parses a string into a [ValidThreshold].
         *
         * ## Behavior:
         *
         * - Case-insensitive
         * - Uses the JVM default locale for lowercase normalization
         * - Throws [IllegalArgumentException] for invalid values
         *
         * @param value User-provided threshold string.
         * @return Corresponding [ValidThreshold] instance.
         * @throws IllegalArgumentException if the value is not recognized.
         */
        fun fromString(value: String): ValidThreshold =
            map[value.lowercase(getDefault())]
                ?: throw IllegalArgumentException(
                    "Invalid --failure-threshold '$value'. " +
                            "Valid values: ${entries.joinToString { it.value }}"
                )
    }
}
