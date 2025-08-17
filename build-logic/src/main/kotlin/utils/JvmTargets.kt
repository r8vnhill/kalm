/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package utils

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/** Best-effort mapping from a Java major to a supported Kotlin JvmTarget, with clamping. */
internal fun jvmTargetFor(java: Int): JvmTarget {
    // Order by capability (update when your Kotlin version adds more)
    val supported = listOf(
        JvmTarget.JVM_21,
        JvmTarget.JVM_20,
        JvmTarget.JVM_19,
        JvmTarget.JVM_18,
        JvmTarget.JVM_17,
        JvmTarget.JVM_1_8,
    )
    // Pick the smallest supported target >= 17 for modern builds; otherwise fall back
    return when {
        java >= 21 -> JvmTarget.JVM_21
        java == 20 -> JvmTarget.JVM_20
        java == 19 -> JvmTarget.JVM_19
        java == 18 -> JvmTarget.JVM_18
        java == 17 -> JvmTarget.JVM_17
        else -> JvmTarget.JVM_1_8
    }.let { candidate ->
        // Safety clamp (in case the list above gets out of sync)
        supported.firstOrNull { it == candidate } ?: JvmTarget.JVM_21
    }
}
