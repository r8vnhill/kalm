/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

package cl.ravenhill.kalm.tools.locks

internal val supportedCommands = listOf("write-all", "write-module", "write-configuration", "diff")
internal val supportedCommandsText = supportedCommands.joinToString(", ")

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

internal sealed interface LocksCommand {
    data object WriteAll : LocksCommand
    data class WriteModule(val module: String) : LocksCommand
    data class WriteConfiguration(val module: String, val configuration: String) : LocksCommand
    data object Diff : LocksCommand
}

internal data class ExecutionRequest(
    val json: Boolean,
    val commandArgs: List<String>
)

internal sealed interface ExecutionRequestResult {
    data class Ok(val request: ExecutionRequest) : ExecutionRequestResult
    data class Error(val message: String) : ExecutionRequestResult
}

internal sealed interface ParseResult {
    data class Ok(val command: LocksCommand) : ParseResult
    data class Error(val message: String) : ParseResult
}

internal data class ParsedCommandState(var command: LocksCommand? = null)

internal data class RenderedCliResult(val text: String, val toStdErr: Boolean)
