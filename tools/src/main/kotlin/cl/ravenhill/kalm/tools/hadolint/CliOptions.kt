package cl.ravenhill.kalm.tools.hadolint

/**
 * Immutable configuration parsed from CLI arguments.
 *
 * This class represents the **validated and normalized command-line state** used by the Hadolint CLI orchestration
 * layer.
 *
 * ## Design goals:
 *
 * - Immutable and side-effect-free
 * - Contains only validated domain values
 * - Free of raw strings for structured options (uses [ValidThreshold])
 * - Suitable for testing without filesystem or process interaction
 *
 * Instances of this class are produced by argument parsing and then consumed by execution logic (`runCli`, runner
 * selection, etc.).
 *
 * @property dockerfiles List of Dockerfile paths provided by the user.
 * @property failureThreshold Validated Hadolint failure threshold.
 * @property strictFiles When `true`, missing Dockerfiles cause immediate failure. When `false`, missing files are
 *   reported as warnings and skipped.
 */
data class CliOptions(
    val dockerfiles: List<String> = listOf("Dockerfile"),
    val failureThreshold: ValidThreshold = ValidThreshold.default,
    val strictFiles: Boolean = false
)
