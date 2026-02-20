package cl.ravenhill.kalm.tools.cli

import io.kotest.core.spec.style.FreeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * Canonical error message used when the `--module` option is detected without an associated value.
 *
 * Keeping this as a constant:
 *
 * - Prevents duplication across test cases.
 * - Makes the test resilient to future refactors.
 * - Documents the expected public-facing CLI behavior.
 */
private const val MISSING_MODULE_MESSAGE = "Missing value for option --module"

/**
 * Set of option names that require a value.
 *
 * These represent long-form CLI flags that:
 *
 * - May appear as `--option=value`
 * - Or as `--option value`
 *
 * The test suite assumes:
 *
 * - Any option in this set must have a non-missing value.
 * - Options not present in this set are ignored by `detectMissingOptionValue`.
 */
private val optionNames = setOf("--module", "--configuration")

/**
 * Represents a single data-driven test case for `detectMissingOptionValue`.
 *
 * @property name Human-readable label for the test case.
 * @property args Token sequence simulating CLI input.
 * @property expected Expected result:
 *     - `null` → no missing-value error.
 *     - non-null → expected error message.
 */
private data class MissingValueCase(
    val name: String,
    val args: List<String>,
    val expected: String?
)

/**
 * ## CliktSupportTest
 *
 * Behavior-focused tests for CLI parsing-helpers used in KALM's command-line tooling.
 *
 * This suite validates two responsibilities:
 *
 * 1. Detection of missing option values.
 * 2. Extraction of option names from Clikt-style error messages.
 *
 * ### Testing Philosophy
 *
 * - Uses data-driven testing (DDT) to cover edge cases.
 * - Encodes sentinel (`--`) semantics explicitly.
 * - Treats token sequences as immutable, positional inputs.
 * - Avoids over-mocking: pure functions are tested directly.
 *
 * ### Sentinel Rule (`--`)
 *
 * The `--` token terminates option parsing.
 *
 * - Tokens after `--` must not be interpreted as options.
 * - Tokens before `--` must still be validated normally.
 *
 * This mirrors standard POSIX CLI semantics.
 */
class CliktSupportTest : FreeSpec({

    "given detectMissingOptionValue" - {
        "when evaluating option/value token sequences" - {
            "then it follows missing-value and sentinel rules" - {

                withData(
                    nameFn = { it.name },

                    /**
                     * Inline option without value.
                     *
                     * Pattern: `--option=`
                     */
                    MissingValueCase(
                        name = "missing-inline-value",
                        args = listOf("--module="),
                        expected = MISSING_MODULE_MESSAGE
                    ),

                    /**
                     * Value expected, but the next token is another option.
                     */
                    MissingValueCase(
                        name = "missing-before-another-option",
                        args = listOf("--module", "--configuration", "test"),
                        expected = MISSING_MODULE_MESSAGE
                    ),

                    /**
                     * Option appears as the final token.
                     */
                    MissingValueCase(
                        name = "missing-at-end",
                        args = listOf("--module"),
                        expected = MISSING_MODULE_MESSAGE
                    ),

                    /**
                     * Option followed by sentinel.
                     *
                     * Sentinel does not count as a value.
                     */
                    MissingValueCase(
                        name = "missing-before-sentinel",
                        args = listOf("--module", "--"),
                        expected = MISSING_MODULE_MESSAGE
                    ),

                    /**
                     * The current implementation treats empty string as present.
                     *
                     * This test locks in that behavior explicitly.
                     */
                    MissingValueCase(
                        name = "empty-next-token-is-currently-treated-as-present",
                        args = listOf("--module", ""),
                        expected = null
                    ),

                    /**
                     * Whitespace-only inline values are considered blank.
                     */
                    MissingValueCase(
                        name = "whitespace-inline-value",
                        args = listOf("--module= "),
                        expected = MISSING_MODULE_MESSAGE
                    ),

                    /**
                     * Options not in `optionNames` are ignored.
                     */
                    MissingValueCase(
                        name = "option-not-in-allow-set-is-ignored",
                        args = listOf("--other="),
                        expected = null
                    ),

                    /**
                     * Multiple options, all values provided.
                     */
                    MissingValueCase(
                        name = "values-present-for-multiple-options",
                        args = listOf("--module", ":core", "--configuration", "runtimeClasspath"),
                        expected = null
                    ),

                    /**
                     * Sentinel prevents later parsing.
                     */
                    MissingValueCase(
                        name = "sentinel-stops-later-option-parsing",
                        args = listOf("--", "--module="),
                        expected = null
                    ),

                    /**
                     * Sentinel does not suppress earlier errors.
                     */
                    MissingValueCase(
                        name = "sentinel-does-not-hide-earlier-missing-value",
                        args = listOf("--module=", "--"),
                        expected = MISSING_MODULE_MESSAGE
                    ),

                    /**
                     * Tokens after sentinel do not affect earlier valid parsing.
                     */
                    MissingValueCase(
                        name = "tokens-after-sentinel-do-not-change-result",
                        args = listOf("--module", ":core", "--", "--configuration"),
                        expected = null
                    )
                ) { case ->
                    val result = detectMissingOptionValue(case.args, optionNames)

                    if (case.expected == null) {
                        result.shouldBeNull()
                    } else {
                        result shouldBe case.expected
                    }
                }
            }
        }
    }

    "given extractNoSuchOptionName" - {
        "when parsing clikt-like messages" - {
            "then it extracts the option token robustly" - {
                withData(
                    nameFn = { it.first },

                    /**
                     * Single-quoted variant.
                     */
                    "single-quoted" to "no such option '--wat'",

                    /**
                     * Double-quoted variant.
                     */
                    "double-quoted" to "Unknown option \"--wat\"",

                    /**
                     * Message containing trailing suggestion.
                     */
                    "with-suggestion-tail" to
                            "no such option '--wat' (did you mean '--what'?)"
                ) { (_, message) ->
                    extractNoSuchOptionName(message) shouldBe "--wat"
                }
            }
        }

        "when no option token exists" - {
            "then it falls back to trimmed message" {
                extractNoSuchOptionName("  unexpected message  ") shouldBe "unexpected message"
            }
        }
    }
})
