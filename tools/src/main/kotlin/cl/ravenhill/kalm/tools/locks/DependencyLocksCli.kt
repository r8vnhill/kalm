/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

package cl.ravenhill.kalm.tools.locks

import kotlin.system.exitProcess

object DependencyLocksCli {

    fun buildShellCommand(args: Array<String>): String {
        require(args.isNotEmpty()) {
            "Missing command. Supported commands: write-all, write-module, write-configuration, diff"
        }
        return when (val command = args.first()) {
            "write-all" -> "./gradlew preflight --write-locks --no-parallel"
            "write-module" -> buildWriteModuleCommand(args.drop(1))
            "write-configuration" -> buildWriteConfigurationCommand(args.drop(1))
            "diff" -> "git diff -- **/gradle.lockfile settings-gradle.lockfile"
            else -> throw IllegalArgumentException(
                "Unknown command '$command'. Supported commands: write-all, write-module, write-configuration, diff"
            )
        }
    }

    private fun buildWriteModuleCommand(args: List<String>): String {
        val module = optionValue(args, "--module")
            ?: throw IllegalArgumentException("Missing required option --module for write-module")
        return "./gradlew $module:dependencies --write-locks --no-parallel"
    }

    private fun buildWriteConfigurationCommand(args: List<String>): String {
        val module = optionValue(args, "--module")
        val configuration = optionValue(args, "--configuration")
        if (module.isNullOrBlank() || configuration.isNullOrBlank()) {
            throw IllegalArgumentException("write-configuration requires --module and --configuration")
        }
        return "./gradlew $module:dependencies --configuration $configuration --write-locks --no-parallel"
    }

    private fun optionValue(args: List<String>, optionName: String): String? {
        val index = args.indexOf(optionName)
        if (index < 0 || index + 1 >= args.size) {
            return null
        }
        return args[index + 1]
    }

    @JvmStatic
    fun main(args: Array<String>) {
        try {
            println(buildShellCommand(args))
        } catch (e: IllegalArgumentException) {
            System.err.println(e.message ?: e.toString())
            exitProcess(1)
        }
    }
}

