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
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.stringPattern
import io.kotest.property.checkAll
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
                    failureThreshold shouldBe "warning"
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
                    failureThreshold shouldBe "error"
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
                        "ERROR" to "error",
                        "error" to "error",
                        "Warning" to "warning"
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
                        Paths.get(existingFile.parent.toString(), "subdir", "..", existingFile.fileName.toString())
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
            "Then it returns a non-zero exit code and prints diagnostics" {
                withTempDockerfile { existingFile ->
                    val runner = FakeRunner(exitCode = 1)
                    val out = ByteArrayOutputStream()
                    val err = ByteArrayOutputStream()

                    val exitCode = HadolintCli.runCli(
                        args = arrayOf("--dockerfile", existingFile.toString(), "--failure-threshold", "error"),
                        runnerSelector = { _, _ -> runner },
                        hadolintAvailable = { false },
                        dockerAvailable = { false },
                        exists = { path -> path == existingFile },
                        out = PrintStream(out),
                        err = PrintStream(err)
                    )

                    exitCode shouldBe 1
                    runner.runs shouldContainExactly listOf(existingFile to "error")
                    out.toString(Charsets.UTF_8) shouldContain "Linting Dockerfiles"
                    err.toString(Charsets.UTF_8) shouldContain existingFile.toString()
                }
            }
        }
    }

    "Given a runner that reports success" - {
        "When running the CLI" - {
            "Then it returns zero and produces no stderr" {
                withTempDockerfile { existingFile ->
                    val runner = FakeRunner(exitCode = 0)
                    val out = ByteArrayOutputStream()
                    val err = ByteArrayOutputStream()

                    val exitCode = HadolintCli.runCli(
                        args = arrayOf("--dockerfile", existingFile.toString()),
                        runnerSelector = { _, _ -> runner },
                        hadolintAvailable = { false },
                        dockerAvailable = { false },
                        exists = { path -> path == existingFile },
                        out = PrintStream(out),
                        err = PrintStream(err)
                    )

                    exitCode shouldBe 0
                    runner.runs shouldContainExactly listOf(existingFile to "warning")
                    err.toString(Charsets.UTF_8) shouldBe ""
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
                val error = shouldThrow<IllegalStateException> {
                    HadolintCli.selectRunner(hadolintAvailable = false, dockerAvailable = false)
                }

                error.message shouldBe
                        "Could not find `hadolint` and Docker is not available. Install one of them and try again."
            }
        }
    }

    "Given valid thresholds" - {
        "When parsing" - {
            "Then they are accepted and normalized" {
                checkAll(validThresholdArb) { input ->
                    val options = HadolintCli.parseArgs(
                        arrayOf("--failure-threshold", input)
                    )
                    options.failureThreshold shouldBe input.lowercase()
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
    Arb.element(validThresholds).flatMap { value ->
        Arb.of(value, value.uppercase(), value.replaceFirstChar { it.uppercase() })
    }

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

    val runs = mutableListOf<Pair<Path, String>>()

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
    override fun commandDescription(file: Path, threshold: String): String =
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
    override fun run(file: Path, threshold: String): Int {
        runs.add(file to threshold)
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
