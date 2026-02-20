/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

package cl.ravenhill.kalm.tools.locks

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class CliJsonPayload(
    val exitCode: Int,
    val command: String? = null,
    val message: String? = null
)

internal fun renderCommand(command: LocksCommand): String = when (command) {
    LocksCommand.WriteAll -> "./gradlew preflight --write-locks --no-parallel"
    is LocksCommand.WriteModule -> "./gradlew \"${command.module}:dependencies\" --write-locks --no-parallel"
    is LocksCommand.WriteConfiguration ->
        "./gradlew \"${command.module}:dependencies\" --configuration \"${command.configuration}\" --write-locks --no-parallel"

    LocksCommand.Diff -> "git diff -- **/gradle.lockfile settings-gradle.lockfile"
}

internal fun DependencyLocksCli.CliResult.toExitCode(): Int = when (this) {
    is DependencyLocksCli.CliResult.Failure -> 1
    is DependencyLocksCli.CliResult.Success -> 0
    DependencyLocksCli.CliResult.Help -> 0
}

internal fun DependencyLocksCli.CliResult.renderCliResult(jsonMode: Boolean): RenderedCliResult {
    if (jsonMode) {
        return RenderedCliResult(
            text = Json.encodeToString(
                when (this) {
                    is DependencyLocksCli.CliResult.Success -> CliJsonPayload(exitCode = 0, command = command)
                    is DependencyLocksCli.CliResult.Failure -> CliJsonPayload(exitCode = 1, message = message)
                    DependencyLocksCli.CliResult.Help -> CliJsonPayload(exitCode = 0, message = LOCKS_CLI_USAGE.trimIndent())
                }
            ),
            toStdErr = false
        )
    }
    return when (this) {
        is DependencyLocksCli.CliResult.Success -> RenderedCliResult(text = command, toStdErr = false)
        is DependencyLocksCli.CliResult.Failure ->
            RenderedCliResult(text = "$message${System.lineSeparator()}${LOCKS_CLI_USAGE.trimIndent()}", toStdErr = true)

        DependencyLocksCli.CliResult.Help -> RenderedCliResult(text = LOCKS_CLI_USAGE.trimIndent(), toStdErr = true)
    }
}
