/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

/*
 * # KALM — RedMadRobot Detekt Convention Plugin
 *
 * Purpose
 * - Apply the RedMadRobot Detekt Gradle plugin (com.redmadrobot.detekt) to enable multi-module
 *   Detekt tasks like detektAll, detektDiff, and detektFormat, along with shared conventions.
 *
 * How to apply (root or subproject)
 *
 * ```kotlin title="build.gradle.kts"
 * plugins {
 *     id("kalm.detekt-redmadrobot")
 * }
 * ```
 *
 * Optional configuration
 *
 * ```kotlin
 * redmadrobot {
 *     detekt {
 *         // Example: check only files changed vs the main branch
 *         checkOnlyDiffWithBranch("main") {
 *             fileExtensions = setOf(".kt", ".kts")
 *         }
 *     }
 * }
 * ```
 *
 * Notes
 * - This wraps the RedMadRobot plugin, which applies core Detekt under the hood.
 * - Replaces the retired `kalm.detekt` convention.
 * - Tasks provided include: detektAll (aggregate), detektDiff (incremental), detektFormat (formatting).
 * - See docs: dev-resources/DOCUMENTATION_RULES.md#5-redmadrobot-detekt-plugin-advanced-multi-module-analysis
 * - Upstream reference: https://github.com/RedMadRobot/gradle-infrastructure
 */

plugins {
    // Apply the RedMadRobot Detekt plugin which wraps and extends the core Detekt plugin
    id("com.redmadrobot.detekt")
}

// The RedMadRobot plugin automatically configures Detekt with their conventions.
// You can further customize via the redmadrobot { detekt { } } extension block.
// 
// Example customizations (optional):
// redmadrobot {
//     detekt {
//         checkOnlyDiffWithBranch("main") {
//             fileExtensions = setOf(".kt", ".kts")
//         }
//     }
// }
