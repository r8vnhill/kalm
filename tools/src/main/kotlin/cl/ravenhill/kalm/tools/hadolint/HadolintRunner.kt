package cl.ravenhill.kalm.tools.hadolint

import java.nio.file.Path

/**
 * Abstraction for executing Hadolint against a Dockerfile.
 *
 * This interface isolates process execution behind a small boundary, allowing the CLI orchestration layer to remain:
 *
 * - Deterministic
 * - Testable
 * - Independent of system availability
 *
 * ## Implementations may execute:
 *
 * - A locally installed `hadolint` binary
 * - A Docker container (`hadolint/hadolint`)
 *
 * ## The abstraction enables:
 *
 * - Dependency inversion (CLI depends on interface, not implementation)
 * - Use of test doubles (e.g., FakeRunner)
 * - Clear runner selection logic
 */
interface HadolintRunner {

    /**
     * Returns a human-readable representation of the command that will be executed.
     *
     * This is used strictly for diagnostics and error reporting.
     *
     * @param file Dockerfile to lint.
     * @param threshold Effective failure threshold.
     * @return Description of the underlying command invocation.
     */
    fun commandDescription(file: Path, threshold: ValidThreshold): String

    /**
     * Executes Hadolint against the given file.
     *
     * Implementations are responsible for invoking the underlying process.
     *
     * @param file Dockerfile to lint.
     * @param threshold Validated failure threshold.
     * @return Process exit code (0 = success, non-zero = failure).
     */
    fun run(file: Path, threshold: ValidThreshold): Int
}

/**
 * Executes Hadolint using a locally installed binary.
 *
 * This runner assumes `hadolint` is available on the system PATH.
 *
 * ## Characteristics:
 *
 * - Fastest execution path (no container startup)
 * - Direct file access
 * - Depends on local environment setup
 */
class BinaryHadolintRunner : HadolintRunner {

    override fun commandDescription(file: Path, threshold: ValidThreshold): String =
        "hadolint --failure-threshold $threshold $file"

    /**
     * Launches a new process using [ProcessBuilder] and inherits the parent process I/O streams.
     *
     * The current JVM blocks until the process completes.
     */
    override fun run(file: Path, threshold: ValidThreshold): Int {
        val process = ProcessBuilder(
            "hadolint",
            "--failure-threshold",
            threshold.value,
            file.toString()
        )
            .inheritIO()
            .start()

        return process.waitFor()
    }
}

/**
 * Executes Hadolint via Docker.
 *
 * ## This runner is used when:
 *
 * - The `hadolint` binary is not available locally
 * - Docker is available
 *
 * It streams the Dockerfile content to the container via stdin.
 *
 * ## Characteristics:
 *
 * - Environment-independent
 * - Slightly slower (container startup cost)
 * - Requires Docker availability
 */
class DockerHadolintRunner : HadolintRunner {

    override fun commandDescription(file: Path, threshold: ValidThreshold): String =
        "docker run --rm -i hadolint/hadolint --failure-threshold $threshold -"

    /**
     * Launches a Docker container and feeds the Dockerfile through stdin.
     *
     * The `-` argument instructs Hadolint inside the container
     * to read from standard input.
     */
    override fun run(file: Path, threshold: ValidThreshold): Int {
        val process = ProcessBuilder(
            "docker",
            "run",
            "--rm",
            "-i",
            "hadolint/hadolint",
            "--failure-threshold",
            threshold.value,
            "-"
        )
            .redirectInput(file.toFile())
            .inheritIO()
            .start()

        return process.waitFor()
    }
}
