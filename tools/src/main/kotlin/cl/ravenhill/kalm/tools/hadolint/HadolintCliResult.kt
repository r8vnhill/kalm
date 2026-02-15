package cl.ravenhill.kalm.tools.hadolint

import kotlinx.serialization.Serializable

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
