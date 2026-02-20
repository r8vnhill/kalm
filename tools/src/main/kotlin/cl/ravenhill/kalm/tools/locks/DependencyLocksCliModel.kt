/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

package cl.ravenhill.kalm.tools.locks

/**
 * Canonical list of supported subcommands.
 *
 * This list is the single source of truth for:
 *
 * - Help and usage messages.
 * - Unknown-command diagnostics.
 * - CLI validation logic.
 *
 * Keeping command identifiers centralized prevents drift between:
 *
 * - Parser configuration,
 * - Error reporting,
 * - Documentation output.
 */
internal val supportedCommands =
    listOf("write-all", "write-module", "write-configuration", "diff")

/**
 * Human-readable representation of [supportedCommands].
 *
 * Intended for embedding inside error messages such as: `Unknown command 'foo'. Supported commands: write-all, ...`
 *
 * This avoids recomputing the formatted string in multiple places.
 */
internal val supportedCommandsText =
    supportedCommands.joinToString(", ")

/**
 * Canonical usage text for the locks CLI.
 *
 * This string is rendered:
 *
 * - In plain-text help mode.
 * - In JSON mode (as the help message payload).
 * - Alongside certain failure messages.
 *
 * The content is intentionally static to:
 *
 * - Keep CLI output deterministic.
 * - Avoid dynamic formatting differences across environments.
 * - Ensure stable output for integration tests.
 */
internal const val LOCKS_CLI_USAGE = """
    Usage:
      locks-cli [--json] write-all
      locks-cli [--json] write-module --module :core
      locks-cli [--json] write-configuration --module :core --configuration testRuntimeClasspath
      locks-cli [--json] diff
      locks-cli help | --help

    Legacy examples:
      locks-cli write-all
      locks-cli write-module --module :core
      locks-cli write-configuration --module :core --configuration testRuntimeClasspath
      locks-cli diff
"""

/**
 * Domain model representing valid lock-related commands.
 *
 * This sealed hierarchy decouples:
 *
 * - Parsing concerns (string-based CLI input),
 * - Execution concerns (rendering Gradle or Git commands),
 * - Output formatting concerns (plain text vs. JSON).
 *
 * Using a sealed interface guarantees exhaustive handling in `when` expressions, improving maintainability when new
 * commands are introduced.
 */
internal sealed interface LocksCommand {

    /**
     * Refreshes all dependency lockfiles.
     *
     * Typically renders to a `./gradlew preflight --write-locks --no-parallel` style command.
     */
    data object WriteAll : LocksCommand

    /**
     * Refreshes lockfiles for a specific module.
     *
     * @property module Gradle module path (e.g., `:core`, `:tools:cli`).
     */
    data class WriteModule(val module: String) : LocksCommand

    /**
     * Refreshes lockfiles for a specific configuration within a module.
     *
     * @property module Gradle module path.
     * @property configuration Gradle configuration name (e.g., `testRuntimeClasspath`).
     */
    data class WriteConfiguration(
        val module: String,
        val configuration: String
    ) : LocksCommand

    /**
     * Displays differences in lockfiles.
     *
     * Typically renders to a `git diff` command targeting known lockfile paths.
     */
    data object Diff : LocksCommand
}

/**
 * Normalized representation of the CLI invocation.
 *
 * This separates global flags from command-specific arguments.
 *
 * @property json Whether JSON output mode was requested.
 * @property commandArgs The argument list after global flags (e.g., `--json`) have been stripped
 */
internal data class ExecutionRequest(
    val json: Boolean,
    val commandArgs: List<String>
)

/**
 * Result of parsing the raw execution request.
 *
 * Parsing at this stage validates:
 *
 * - Duplicate global flags.
 * - Structural issues before subcommand parsing begins.
 *
 * This early validation allows clearer, more targeted error messages.
 */
internal sealed interface ExecutionRequestResult {

    /**
     * Successfully parsed execution request.
     */
    data class Ok(val request: ExecutionRequest) : ExecutionRequestResult

    /**
     * Structural failure during request parsing.
     *
     * @property message Human-readable explanation of the issue.
     */
    data class Error(val message: String) : ExecutionRequestResult
}

/**
 * Result of parsing command-level arguments.
 *
 * This stage transforms tokenized CLI input into a typed [LocksCommand], or produces a structured error message.
 */
internal sealed interface ParseResult {

    /**
     * Successfully parsed subcommand.
     */
    data class Ok(val command: LocksCommand) : ParseResult

    /**
     * Command-level validation error.
     *
     * Examples:
     *
     * - Unknown command.
     * - Unknown option.
     * - Missing required option.
     */
    data class Error(val message: String) : ParseResult
}

/**
 * Mutable parsing state used during Clikt subcommand execution.
 *
 * Clikt commands are instantiated independently and must communicate the parsed domain command back to the caller.
 *
 * This small mutable holder:
 *
 * - Avoids global state.
 * - Keeps mutation local to parsing scope.
 * - Ensures only one command is produced.
 *
 * The field is nullable during parsing and required to be non-null once parsing completes successfully.
 */
internal data class ParsedCommandState(
    var command: LocksCommand? = null
)

/**
 * Fully rendered CLI output.
 *
 * @property text The text to print.
 * @property toStdErr Whether the text should be written to stderr.
 */
internal data class RenderedCliResult(
    val text: String,
    val toStdErr: Boolean
)
