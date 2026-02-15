package cl.ravenhill.kalm.tools.hadolint

import kotlinx.serialization.Serializable

/**
 * Structured JSON result produced by the Hadolint CLI.
 *
 * This model is emitted to **stdout** as a single JSON object and is intended for machine consumption (e.g., PowerShell
 * `ConvertFrom-Json`, CI pipelines, or other automation tooling).
 *
 * ## Design goals:
 *
 * - Stable schema suitable for automation
 * - Fully serializable via `kotlinx.serialization`
 * - No human-oriented formatting (logs go to stderr)
 * - Deterministic structure for testability
 *
 * Human-readable diagnostics are written to stderr. This ensures:
 *
 * - stdout remains pure JSON
 * - predictable integration with scripting environments
 *
 * @property exitCode Process-like exit code:
 *   - `0` → All lint targets succeeded
 *   - `1` → At least one target failed, or validation failed
 * @property threshold Canonical failure threshold passed to Hadolint (lowercase string).
 * @property strictFiles Whether strict missing-file validation was enabled.
 * @property targets List of resolved Dockerfiles that were actually linted. These are normalized absolute paths
 *   serialized as strings.
 * @property missing Dockerfiles requested by the user but not found on disk. If [strictFiles] is `true`, presence of
 *   missing files may cause failure.
 * @property failed Subset of [targets] that produced non-zero exit codes. Empty when lint succeeded for all targets.
 * @property runner Identifier of the execution strategy used:
 *   - `"binary"` → Local `hadolint` binary
 *   - `"docker"` → Docker container fallback
 * @property startedAtEpochMs Epoch timestamp (milliseconds) captured at CLI start.
 * @property finishedAtEpochMs Epoch timestamp (milliseconds) captured after execution completed. The duration can be
 *   computed as: `finishedAtEpochMs - startedAtEpochMs`
 */
@Serializable
data class HadolintCliResult(
    val exitCode: Int,
    val threshold: String,
    val strictFiles: Boolean,
    val targets: List<String>,
    val missing: List<String>,
    val failed: List<String>,
    val runner: String,
    val startedAtEpochMs: Long,
    val finishedAtEpochMs: Long
)
