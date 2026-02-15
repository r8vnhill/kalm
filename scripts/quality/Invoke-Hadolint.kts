#!/usr/bin/env kotlin

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

private val defaultThreshold = "warning"
private val validThresholds = setOf("error", "warning", "info", "style", "ignore")

data class CliOptions(
    val dockerfiles: List<String> = listOf("Dockerfile"),
    val failureThreshold: String = defaultThreshold
)

fun printUsage() {
    println(
        """
        Usage:
          kotlin scripts/quality/invoke-hadolint.kts [options]

        Options:
          --dockerfile <path>           Dockerfile path to lint (repeatable)
          --failure-threshold <level>   error|warning|info|style|ignore (default: warning)
          --help                        Show this help
        """.trimIndent()
    )
}

fun parseArgs(args: Array<String>): CliOptions {
    val dockerfiles = mutableListOf<String>()
    var failureThreshold = defaultThreshold

    var i = 0
    while (i < args.size) {
        when (val arg = args[i]) {
            "--dockerfile" -> {
                if (i + 1 >= args.size) {
                    error("Missing value for --dockerfile")
                }
                dockerfiles += args[i + 1]
                i += 2
            }

            "--failure-threshold" -> {
                if (i + 1 >= args.size) {
                    error("Missing value for --failure-threshold")
                }
                failureThreshold = args[i + 1].lowercase()
                i += 2
            }

            "--help", "-h" -> {
                printUsage()
                kotlin.system.exitProcess(0)
            }

            else -> error("Unknown argument: $arg")
        }
    }

    if (failureThreshold !in validThresholds) {
        error("Invalid --failure-threshold '$failureThreshold'. Valid values: ${validThresholds.joinToString(", ")}")
    }

    return CliOptions(
        dockerfiles = if (dockerfiles.isEmpty()) listOf("Dockerfile") else dockerfiles,
        failureThreshold = failureThreshold
    )
}

fun canRun(vararg command: String): Boolean = try {
    val process = ProcessBuilder(*command)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .start()
    process.waitFor() == 0
} catch (_: Exception) {
    false
}

fun hasCommand(command: String): Boolean = try {
    ProcessBuilder(command, "--version")
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .start()
        .waitFor()
    true
} catch (_: Exception) {
    false
}

fun runHadolintBinary(file: Path, threshold: String): Int {
    val process = ProcessBuilder(
        "hadolint",
        "--failure-threshold",
        threshold,
        file.toString()
    )
        .inheritIO()
        .start()
    return process.waitFor()
}

fun runHadolintDocker(file: Path, threshold: String): Int {
    val process = ProcessBuilder(
        "docker",
        "run",
        "--rm",
        "-i",
        "hadolint/hadolint",
        "--failure-threshold",
        threshold,
        "-"
    )
        .redirectInput(file.toFile())
        .inheritIO()
        .start()
    return process.waitFor()
}

fun main(args: Array<String>) {
    val options = parseArgs(args)

    val hadolintAvailable = hasCommand("hadolint")
    val dockerAvailable = canRun("docker", "version")

    if (!hadolintAvailable && !dockerAvailable) {
        error("Could not find `hadolint` and Docker is not available. Install one of them and try again.")
    }

    val dockerfiles = options.dockerfiles.map { Paths.get(it).toAbsolutePath().normalize() }
    val existing = dockerfiles.filter { Files.exists(it) }
    val missing = dockerfiles.filterNot { Files.exists(it) }

    missing.forEach { println("WARNING: Dockerfile not found and will be skipped: $it") }

    if (existing.isEmpty()) {
        error("No valid Dockerfiles were found to lint.")
    }

    println("Linting Dockerfiles with threshold '${options.failureThreshold}'...")
    println("Targets: ${existing.joinToString(", ")}")

    val failed = mutableListOf<Path>()
    for (file in existing) {
        println("Running Hadolint on: $file")
        val exitCode = if (hadolintAvailable) {
            runHadolintBinary(file, options.failureThreshold)
        } else {
            runHadolintDocker(file, options.failureThreshold)
        }
        if (exitCode != 0) {
            failed.add(file)
        }
    }

    if (failed.isNotEmpty()) {
        System.err.println("Hadolint reported issues in: ${failed.joinToString(", ")}")
        kotlin.system.exitProcess(1)
    }

    println("Hadolint completed successfully. ✅")
}

main(args)
