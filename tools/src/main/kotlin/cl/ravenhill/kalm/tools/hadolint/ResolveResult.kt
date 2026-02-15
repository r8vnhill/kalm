package cl.ravenhill.kalm.tools.hadolint

import java.nio.file.Path

/**
 * Result of resolving CLI-provided Dockerfile paths against the filesystem.
 *
 * This class separates filesystem resolution concerns from execution logic. It is produced by `resolveDockerfiles(...)`
 * and consumed by the orchestration layer.
 *
 * ## Design rationale:
 *
 * - Keeps resolution deterministic and side-effect-free
 * - Makes strict/non-strict handling explicit
 * - Simplifies testing (no process execution involved)
 * - Avoids mixing validation logic with execution logic
 *
 * ## The lists are:
 *
 * - Normalized (`toAbsolutePath().normalize()`)
 * - Order-preserving
 * - Mutually exclusive
 *
 * @property existing Dockerfiles that were successfully resolved and exist on disk. These paths are ready to be passed
 *   to a [HadolintRunner].
 * @property missing Dockerfiles that were requested but not found. How these are handled depends on
 *   `CliOptions.strictFiles`:
 *   - If `false`, they are reported and skipped.
 *   - If `true`, execution fails immediately.
 */
data class ResolveResult(
    val existing: List<Path>,
    val missing: List<Path>
)
