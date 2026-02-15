/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

package cl.ravenhill.kalm.tools.hadolint

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.FreeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.stringPattern
import io.kotest.property.checkAll
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * ## Hadolint CLI Test Suite
 *
 * Verifies the behavior of [HadolintCli], including:
 *
 * - CLI argument parsing
 * - Threshold normalization and validation
 * - Dockerfile resolution logic
 * - Runner selection strategy
 * - Exit code aggregation
 * - Output and error stream behavior
 *
 * ### Testing Strategy
 *
 * This suite intentionally combines:
 *
 * - **Example-based tests** (DDT) for explicit edge cases and messages.
 * - **Property-based tests (PBT)** for input space validation (threshold domain).
 *
 * This dual approach ensures:
 *
 * - Deterministic validation of known scenarios.
 * - Broad coverage of the input domain.
 * - No duplication of production validation logic inside tests.
 *
 * ### Architectural Notes
 *
 * The CLI implementation is designed for testability via dependency injection:
 *
 * - Runner selection is injected.
 * - File existence checks are injected.
 * - Output and error streams are injected.
 *
 * This avoids:
 *
 * - Reliance on real Docker or hadolint binaries.
 * - Filesystem side effects.
 * - Global state.
 *
 * All tests are deterministic and side-effect-free.
 */
class HadolintCliTest : FreeSpec({

    "Given default arguments" - {
        "When parsing" - {
            "Then defaults are applied" {
                HadolintCli.parseArgs(emptyArray()).apply {
                    dockerfiles.shouldContainExactly("Dockerfile")
                    failureThreshold shouldBe ValidThreshold.default
                    strictFiles.shouldBeFalse()
                }
            }
        }
    }

    "Given short flags and strict files" - {
        "When parsing" - {
            "Then values are mapped correctly" {
                HadolintCli.parseArgs(
                    arrayOf(
                        "-f",
                        "Dockerfile",
                        "-f",
                        "Dockerfile.alt",
                        "-t",
                        "error",
                        "--strict-files"
                    )
                ).apply {
                    dockerfiles.shouldContainExactly("Dockerfile", "Dockerfile.alt")
                    failureThreshold shouldBe ValidThreshold.ERROR
                    strictFiles.shouldBeTrue()
                }
            }
        }
    }

    "Given threshold inputs" - {
        "When parsing" - {
            "Then valid thresholds are normalized" - {
                withData(
                    nameFn = { "input=${it.first}" },
                    listOf(
                        "ERROR" to ValidThreshold.ERROR,
                        "error" to ValidThreshold.ERROR,
                        "Warning" to ValidThreshold.WARNING,
                    )
                ) { (input, expected) ->
                    HadolintCli.parseArgs(arrayOf("--failure-threshold", input))
                        .failureThreshold shouldBe expected
                }
            }

            "Then invalid thresholds are rejected with message" {
                shouldThrowWithMessage<IllegalArgumentException>(
                    "Invalid --failure-threshold 'nope'. Valid values: error, warning, info, style, ignore"
                ) {
                    HadolintCli.parseArgs(arrayOf("--failure-threshold", "nope"))
                }
            }
        }
    }

    "Given existing and missing Dockerfiles" - {
        "When resolving" - {
            "Then they are separated and normalized correctly" {
                withTempDockerfile { existingFile ->
                    val weirdPath =
                        Paths.get(
                            existingFile.parent.toString(),
                            "subdir",
                            "..",
                            existingFile.fileName.toString()
                        )
                    val options = CliOptions(dockerfiles = listOf(weirdPath.toString(), "missing"))

                    HadolintCli.resolveDockerfiles(options) { it == existingFile }
                        .apply {
                            existing shouldContainExactly listOf(existingFile)
                            missing shouldHaveSize 1
                        }
                }
            }
        }
    }

    "Given a runner that reports failure" - {
        "When running the CLI" - {
            "Then it returns JSON on stdout and diagnostics on stderr" {
                withTempDockerfile { existingFile ->
                    val runner = FakeRunner(exitCode = 1)
                    val out = ByteArrayOutputStream()
                    val err = ByteArrayOutputStream()

                    val result = HadolintCli.runCliJson(
                        args = arrayOf("--dockerfile", existingFile.toString(), "--failure-threshold", "error"),
                        runnerSelector = { _, _ -> runner },
                        hadolintAvailable = { false },
                        dockerAvailable = { false },
                        exists = { path -> path == existingFile },
                        nowEpochMs = sequenceOf(100L, 110L).iterator()::next,
                        out = PrintStream(out),
                        err = PrintStream(err)
                    )

                    result.exitCode shouldBe 1
                    result.threshold shouldBe "error"
                    result.strictFiles shouldBe false
                    result.targets shouldContainExactly listOf(existingFile.toString())
                    result.missing shouldContainExactly emptyList()
                    result.failed shouldContainExactly listOf(existingFile.toString())
                    result.runner shouldBe "FakeRunner"
                    result.startedAtEpochMs shouldBe 100L
                    result.finishedAtEpochMs shouldBe 110L
                    runner.runs shouldContainExactly listOf(existingFile to ValidThreshold.ERROR)
                    Json.decodeFromString<HadolintCliResult>(
                        out.toString(Charsets.UTF_8).trim()
                    ) shouldBe result
                    err.toString(Charsets.UTF_8) shouldContain existingFile.toString()
                }
            }
        }
    }

    "Given a runner that reports success" - {
        "When running the CLI" - {
            "Then stdout is JSON only and stderr has logs" {
                withTempDockerfile { existingFile ->
                    val runner = FakeRunner(exitCode = 0)
                    val out = ByteArrayOutputStream()
                    val err = ByteArrayOutputStream()

                    val result = HadolintCli.runCliJson(
                        args = arrayOf("--dockerfile", existingFile.toString()),
                        runnerSelector = { _, _ -> runner },
                        hadolintAvailable = { false },
                        dockerAvailable = { false },
                        exists = { path -> path == existingFile },
                        nowEpochMs = sequenceOf(200L, 220L).iterator()::next,
                        out = PrintStream(out),
                        err = PrintStream(err)
                    )

                    result.exitCode shouldBe 0
                    result.threshold shouldBe ValidThreshold.default.value
                    result.failed shouldContainExactly emptyList()
                    runner.runs shouldContainExactly listOf(existingFile to ValidThreshold.default)
                    Json.decodeFromString<HadolintCliResult>(
                        out.toString(Charsets.UTF_8).trim()
                    ) shouldBe result
                    err.toString(Charsets.UTF_8) shouldContain "Linting Dockerfiles"
                }
            }
        }
    }

    "Given runner availability" - {
        "When selecting a runner" - {
            "Then hadolint takes precedence" {
                HadolintCli.selectRunner(hadolintAvailable = true, dockerAvailable = true)
                    .shouldBeInstanceOf<BinaryHadolintRunner>()
            }

            "Then docker is used when hadolint is missing" {
                HadolintCli.selectRunner(hadolintAvailable = false, dockerAvailable = true)
                    .shouldBeInstanceOf<DockerHadolintRunner>()
            }

            "Then neither available throws a clear error" {
                shouldThrowWithMessage<IllegalStateException>(
                    "Could not find `hadolint` and Docker is not available. Install one of them and try again."
                ) {
                    HadolintCli.selectRunner(hadolintAvailable = false, dockerAvailable = false)
                }
            }
        }
    }

    "Given valid thresholds" - {
        "When parsing" - {
            "Then they are accepted and normalized" {
                checkAll(validThresholdArb) { input ->
                    val options = HadolintCli.parseArgs(
                        arrayOf("--failure-threshold", input.value)
                    )
                    options.failureThreshold shouldBe input
                }
            }
        }
    }

    "Given invalid thresholds" - {
        "When parsing" - {
            "Then parsing fails" {
                checkAll(invalidThresholdArb) { input ->
                    shouldThrow<IllegalArgumentException> {
                        HadolintCli.parseArgs(
                            arrayOf("--failure-threshold", input)
                        )
                    }
                }
            }
        }
    }

    "Given --help" - {
        "When running the CLI" - {
            "Then JSON is still emitted and exitCode is zero" {
                val out = ByteArrayOutputStream()
                val err = ByteArrayOutputStream()

                val result = HadolintCli.runCliJson(
                    args = arrayOf("--help"),
                    out = PrintStream(out),
                    err = PrintStream(err),
                    nowEpochMs = sequenceOf(300L, 330L).iterator()::next
                )

                result.exitCode shouldBe 0
                Json.decodeFromString<HadolintCliResult>(
                    out.toString(Charsets.UTF_8).trim()
                ) shouldBe result
                err.toString(Charsets.UTF_8) shouldContain "Usage:"
            }
        }
    }

    "Given invalid arguments" - {
        "When running the CLI" - {
            "Then JSON is emitted with a non-zero exit code" {
                val out = ByteArrayOutputStream()
                val err = ByteArrayOutputStream()

                val result = HadolintCli.runCliJson(
                    args = arrayOf("--unknown"),
                    out = PrintStream(out),
                    err = PrintStream(err),
                    nowEpochMs = sequenceOf(400L, 430L).iterator()::next
                )

                result.exitCode shouldBe 1
                Json.decodeFromString<HadolintCliResult>(
                    out.toString(Charsets.UTF_8).trim()
                ) shouldBe result
                err.toString(Charsets.UTF_8) shouldContain "Unknown argument"
            }
        }
    }
})

/**
 * Canonical set of failure-threshold values accepted by Hadolint.
 *
 * This is the single source of truth for:
 * - DDT expectations (error messages and normalization)
 * - PBT generators (valid vs. invalid domains)
 *
 * Keeping it in one place avoids drift between tests and implementation.
 */
private val validThresholds = setOf("error", "warning", "info", "style", "ignore")

/**
 * Property-based generator for **valid** threshold inputs.
 *
 * Generates:
 * - canonical values (e.g., "error")
 * - uppercase variants (e.g., "ERROR")
 * - capitalized variants (e.g., "Error")
 *
 * This validates that parsing is case-insensitive while still normalizing to lowercase.
 */
private val validThresholdArb =
    Arb.enum<ValidThreshold>()

/**
 * Property-based generator for **invalid** threshold inputs.
 *
 * Domain:
 * - alphabetic strings (1..10 chars)
 * - filtered to exclude all valid thresholds (case-insensitive)
 *
 * Using a restricted pattern makes shrinking predictable and avoids generating empty/whitespace-only values, which are
 * usually better covered by targeted example-based tests.
 */
private val invalidThresholdArb =
    Arb.stringPattern("[a-zA-Z]{1,10}")
        .filter { it.lowercase() !in validThresholds }

/**
 * Test double implementation of [HadolintRunner].
 *
 * This runner does **not** execute any external process. Instead, it:
 *
 * - Records every invocation (file and threshold)
 * - Returns a predefined exit code
 *
 * It is used to verify:
 *
 * - Correct propagation of CLI arguments
 * - Threshold normalization behavior
 * - Per-file invocation semantics
 * - Exit-code aggregation logic
 *
 * By isolating process execution behind the [HadolintRunner] abstraction, the CLI logic can be tested deterministically
 * without:
 *
 * - Spawning external processes
 * - Requiring Docker or Hadolint binaries
 * - Introducing non-deterministic behavior in CI
 *
 * @property exitCode Exit code to simulate for each invocation. `0` typically represents success, non-zero represents
 *   failure.
 * @property runs Captured invocations as `(file, threshold)` pairs, preserving call order for precise assertions.
 */
private class FakeRunner(private val exitCode: Int) : HadolintRunner {

    val runs = mutableListOf<Pair<Path, ValidThreshold>>()

    /**
     * Returns a stable, human-readable command description.
     *
     * In production runners this usually returns the full command line that will be executed (e.g.,
     * `hadolint --failure-threshold error Dockerfile`).
     *
     * In tests, we deliberately return a constant value to:
     *
     * - Keep output deterministic
     * - Avoid coupling assertions to formatting details
     */
    override fun commandDescription(file: Path, threshold: ValidThreshold): String =
        "fake-hadolint"

    /**
     * Simulates a Hadolint execution.
     *
     * Behavior:
     *
     * 1. Records the `(file, threshold)` pair in [runs]
     * 2. Returns the configured [exitCode]
     *
     * No filesystem or process interaction occurs.
     */
    override fun run(file: Path, threshold: ValidThreshold): Int {
        runs += file to threshold
        return exitCode
    }
}

/**
 * Creates a temporary Dockerfile-like path for tests that need filesystem interaction.
 *
 * Why this helper exists:
 * - Avoids `deleteOnExit()` (which can leak files during long CI runs).
 * - Ensures deterministic cleanup via `finally`.
 * - Keeps individual tests short and focused.
 *
 * @param block Test logic that receives the created [Path].
 */
private inline fun withTempDockerfile(block: (Path) -> Unit) {
    val file = Files.createTempFile("Dockerfile", ".tmp")
    try {
        block(file)
    } finally {
        Files.deleteIfExists(file)
    }
}
