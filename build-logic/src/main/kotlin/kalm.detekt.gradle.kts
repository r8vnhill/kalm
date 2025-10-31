/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

/*
 * # KALM — Detekt Convention Plugin
 *
 * ## Purpose
 * Centralize Detekt configuration so every module shares the same rules, baselines, and reports without copy-pasting
 * build script blocks.
 *
 * ## How to apply (in a subproject):
 * ```kotlin title="some-module/build.gradle.kts"
 * plugins {
 *     id("kalm.detekt")
 * }
 * ```
 *
 * ## Notes
 * - We apply the upstream Detekt Gradle plugin here and then tune it via the DetektExtension. Subprojects only apply
 *   this convention plugin.
 * - Config file and (optional) baseline live under rootProject/config/detekt/.
 */

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import utils.JvmToolchain

plugins {
    // Upstream Detekt plugin. This convention plugin wraps and standardizes it.
    id("io.gitlab.arturbosch.detekt")
}

// Access the version catalog to resolve detekt-formatting without hardcoding coordinates.
private val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

extensions.configure<DetektExtension> {
    // Build on Detekt's default config (detekt.yml references can override/disable rules).
    buildUponDefaultConfig = true
    // Keep autocorrect off by default to avoid surprise edits in CI or local runs.
    autoCorrect = false
    // Shared rule set for the whole repo.
    config.from(rootProject.file("config/detekt/detekt.yml"))
    // Optional baseline to suppress known, preexisting issues until they’re cleaned up.
    // We only set it when the file exists, which keeps configuration-cache friendly behavior.
    val baselineFile = rootProject.file("config/detekt/baseline.xml")
    baseline = baselineFile.takeIf(File::exists)
}

dependencies {
    // Optional formatting ruleset (ktlint-based). Guarded so builds don’t fail if alias is missing.
    libs.findLibrary("detekt-formatting").ifPresent { dep ->
        add("detektPlugins", dep)
    }
}

tasks.withType<Detekt>().configureEach {
    // Align Detekt's Kotlin compiler / bytecode target with the configured toolchain.
    val languageVersion = extensions.findByType(JavaPluginExtension::class.java)
        ?.toolchain
        ?.languageVersion
        ?.getOrElse(JvmToolchain.defaultJavaLanguageVersion())
        ?: JvmToolchain.defaultJavaLanguageVersion()
    jvmTarget = languageVersion.asInt().toString()

    // Wire Detekt's classpath so it sees compiled sources and the detekt configuration itself.
    val sourceSets = extensions.findByType<SourceSetContainer>()
    sourceSets?.findByName("main")?.output?.let { classpath.from(it) }
    configurations.findByName("detekt")?.let { classpath.from(it) }

    // Generate multiple report types for IDEs and CI:
    //  - HTML for quick local browsing,
    //  - SARIF for code scanning integrations (GitHub, Azure, etc.),
    //  - TXT for grepping in CI logs.
    reports {
        html.required.set(true)
        sarif.required.set(true)
        txt.required.set(true)
    }
}
