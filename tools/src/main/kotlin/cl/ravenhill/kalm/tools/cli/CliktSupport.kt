package cl.ravenhill.kalm.tools.cli

/**
 * Represents a parsed CLI token.
 *
 * A token may represent:
 *
 * - An option with an inline value (e.g., `--output=file.txt`)
 * - An option without inline value (e.g., `--output`)
 * - A positional argument (if its name is not recognized as an option)
 *
 * @property name The option name (portion before `=`).
 * @property hasInlineValue Whether the token contains an `=` delimiter.
 * @property inlineValue The value after `=`, or `null` if no inline value is present.
 */
private data class ParsedToken(
    val name: String,
    val hasInlineValue: Boolean,
    val inlineValue: String?
)

/**
 * Regular expression used to extract an option name from an error message.
 *
 * It matches patterns like:
 *
 * - `option foo`
 * - `option 'foo'`
 * - `option "foo"`
 *
 * The captured group corresponds to the option name.
 */
private val noSuchOptionRegex = Regex("""\boption\s+['"]?([^'"]+)['"]?""")

/**
 * Parses a raw CLI token into a [ParsedToken].
 *
 * This function only performs structural parsing. It does not validate whether the token corresponds to a known option.
 *
 * ## Usage:
 *
 * Typical usage is internal to validation logic.
 *
 * ### Example 1: Inline value
 *
 * ```kotlin
 * val token = parseToken("--output=file.txt")
 * // token.name == "--output"
 * // token.hasInlineValue == true
 * // token.inlineValue == "file.txt"
 * ```
 *
 * ### Example 2: No inline value
 *
 * ```kotlin
 * val token = parseToken("--output")
 * // token.name == "--output"
 * // token.hasInlineValue == false
 * // token.inlineValue == null
 * ```
 */
private fun parseToken(token: String): ParsedToken {
    val hasEquals = '=' in token
    return ParsedToken(
        name = token.substringBefore('='),
        hasInlineValue = hasEquals,
        inlineValue = token
            .substringAfter('=', missingDelimiterValue = "")
            .takeIf { hasEquals }
    )
}

/**
 * Checks whether a parsed token is missing a required value.
 *
 * An option is considered to have a missing value if:
 *
 * - It uses inline syntax (`--option=...`) and the value is blank.
 * - It does not use inline syntax, and the next argument is missing or starts with `-`.
 *
 * @param token Parsed CLI token.
 * @param nextArg The next argument in the CLI (or `null` if at end of arguments).
 * @return `true` if the token's value is missing.
 */
private fun isMissingValue(token: ParsedToken, nextArg: String?): Boolean =
    when {
        token.hasInlineValue -> token.inlineValue.isNullOrBlank()
        else -> nextArg == null || nextArg.startsWith("-")
    }

/**
 * Detects whether any option in [args] that requires a value is missing one.
 *
 * The function supports both:
 *
 * - Inline syntax: `--option=value`
 * - Separate argument syntax: `--option value`
 *
 * Parsing stops when `--` is encountered, following conventional CLI semantics.
 *
 * An option is considered to have a missing value if:
 *
 * - It uses inline syntax, and the value is blank.
 * - It does not use inline syntax and:
 *   - It is the last argument, or
 *   - The next token starts with `-` (assumed to be another option).
 *
 * ## Usage:
 *
 * This function is typically invoked during CLI validation before command execution.
 *
 * ### Example 1: Missing inline value
 *
 * ```kotlin
 * val result = detectMissingOptionValue(
 *     args = listOf("--output="),
 *     optionNames = setOf("--output")
 * )
 * // result == "Missing value for option --output"
 * ```
 *
 * ### Example 2: Valid separate value
 *
 * ```kotlin
 * val result = detectMissingOptionValue(
 *     args = listOf("--output", "file.txt"),
 *     optionNames = setOf("--output")
 * )
 * // result == null
 * ```
 *
 * @param args Raw command-line arguments.
 * @param optionNames Set of option names that require a value.
 * @return A human-readable error message if a missing value is detected, or `null` if validation succeeds.
 */
internal fun detectMissingOptionValue(
    args: List<String>,
    optionNames: Set<String>
): String? = args
    .indices
    .takeWhile { args[it] != "--" }
    .map { index ->
        val token = parseToken(args[index])
        val nextArg = args.getOrNull(index + 1)
        Triple(index, token, nextArg)
    }
    .firstOrNull { (_, token, nextArg) ->
        token.name in optionNames && isMissingValue(token, nextArg)
    }
    ?.let { (_, token, _) ->
        "Missing value for option ${token.name}"
    }

/**
 * Extracts the option name from an error message indicating an unknown option.
 *
 * If the message contains a pattern such as:
 *
 * - `No such option: option foo`
 * - `Unknown option 'foo'`
 *
 * The function returns the extracted option name.
 *
 * If no option name can be detected, the original trimmed message is returned.
 *
 * ## Usage:
 *
 * ### Example
 *
 * ```kotlin
 * val name = extractNoSuchOptionName("Unknown option 'foo'")
 * // name == "foo"
 * ```
 *
 * @param message The original error message.
 * @return The extracted option name if found; otherwise the trimmed message.
 */
internal fun extractNoSuchOptionName(message: String): String =
    noSuchOptionRegex
        .find(message)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?: message.trim()
