@file:Suppress("SpellCheckingInspection")

/*
 * === Root build configuration (build.gradle.kts) ===
 * - Apply shared conventions and quality tools that the whole build benefits from.
 * - Centralize static analysis (Detekt) and dependency maintenance helpers (VCU + ben-manes).
 */

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import java.util.*

// === Plugins ===
// - keen.reproducible: convention plugin that makes all archives byte-for-byte reproducible.
// - kotlin binary compatibility: validates ABI changes for published modules.
// - detekt: static analysis for Kotlin.
// - version-catalog-update: helps bump versions in libs.versions.toml.
// - ben-manes versions: produces a dependency update report (does not change files).
plugins {
    id("keen.reproducible")

    alias(libs.plugins.kotlin.bin.compatibility)

    // Apply detekt at the root so we can centralize defaults; subprojects still need to apply the plugin.
    alias(libs.plugins.detekt)

    alias(libs.plugins.version.catalog.update)
    alias(libs.plugins.ben.manes.versions)
    alias(libs.plugins.kover)
}

// === Kotlin binary compatibility validation ===
// Ignore non-API modules so ABI validation focuses on published libraries.
apiValidation {
    ignoredProjects += listOf(
        "examples",
        "benchmark"
        // "test-utils"
    )
}

// === Detekt (root defaults) ===
// These defaults are applied to the root project. To propagate to subprojects where the detekt plugin is applied, see
// the `subprojects { plugins.withId("detekt") { … } }` block further below.
detekt {
    // Start from Detekt’s defaults, then layer your config
    buildUponDefaultConfig = true

    config.setFrom(rootProject.files("config/detekt/detekt.yml"))

    // Speed up on multicore machines
    parallel = true
}

val detektPluginId = libs.plugins.detekt.get().pluginId

// Propagate Detekt defaults to subprojects **that apply detekt**.
// This keeps each module’s build file small and consistent.
subprojects {
    plugins.withId(detektPluginId) {
        extensions.configure(DetektExtension::class) {
            buildUponDefaultConfig = true
            config.setFrom(rootProject.files("config/detekt/detekt.yml"))
            parallel = true
        }
    }

    // Apply Kover where you want coverage (typically all JVM modules with tests)
    plugins.withId("org.jetbrains.kotlin.jvm") {
        apply(plugin = "org.jetbrains.kotlinx.kover")
    }
}

// Convenience wrapper so `./gradlew lint` runs Detekt (root and subprojects where applied).
tasks.register("lint") {
    group = "verification"
    description = "Runs static analysis (Detekt) across the project."
    dependsOn(tasks.matching { it.name == "detekt" })
}

// === Version Catalog Update (VCU) ===
// Keeps libs.versions.toml tidy and helps auto-bump versions.
versionCatalogUpdate {
    // Sort keys for smaller diffs and readability
    sortByKey.set(true)

    // Keep unused entries while refactoring
    keep { keepUnusedVersions.set(true) }
}

// === Dependency Updates report (ben-manes) ===
// Produces a JSON/Plain report of available updates; pairs nicely with VCU.
tasks.withType<DependencyUpdatesTask>().configureEach {
    // Only accept stable candidates (mirror VCU’s policy)
    rejectVersionIf {
        val v = candidate.version.lowercase(Locale.ROOT)
        listOf("alpha", "beta", "rc", "cr", "m", "milestone", "preview", "eap", "snapshot")
            .any(v::contains)
    }

    // Also report Gradle wrapper updates
    checkForGradleUpdate = true

    outputFormatter = "json,plain"
    outputDir = layout.buildDirectory.dir("dependencyUpdates").get().asFile.toString()
    reportfileName = "report"

    notCompatibleWithConfigurationCache("This task inspects configurations, breaking configuration cache.")
}

// === Dependency maintenance umbrella task ===
// Runs both: update the catalog and generate a report of what’s available.
tasks.register("dependencyMaintenance") {
    group = "dependencies"
    description = "Runs version catalog updates and dependency update reports."
    dependsOn("versionCatalogUpdate")
    dependsOn("dependencyUpdates")
}

kover {
    // Global defaults for all projects (you can override per project if needed)
    reports {
        // Exclude things that skew coverage
        filters {
            excludes {
                // Packages, classes, annotations
                packages(
                    ".*generated.*",
                    ".*build.*"
                )
                classes(
                    ".*Dagger.*",
                    ".*Hilt.*",
                    ".*Module.*",
                    ".*Factory.*"
                )
                annotatedBy(
                    "javax.annotation.Generated",
                    "kotlinx.serialization.Serializable"
                )
            }
        }
    }
}

//#region Default Java version checks
// opt-out switch: -PskipJavaDefaultCheck=true
val skipCheck = providers.gradleProperty("skipJavaDefaultCheck")
    .map(String::toBoolean).orElse(false)

val verifyKeenJavaDefault by tasks.registering {
    group = "verification"
    description = "Ensures keen.java.default is present in root and build-logic gradle.properties and that both match."

    // Track the two properties files
    val rootPropsFile = layout.projectDirectory.file("gradle.properties")
    val buildLogicPropsFile = layout.projectDirectory.file("build-logic/gradle.properties")

    inputs.files(rootPropsFile, buildLogicPropsFile)
    outputs.upToDateWhen { false } // always re-run to catch edits

    doLast {
        if (skipCheck.get()) {
            logger.lifecycle("Skipping keen.java.default consistency check (-PskipJavaDefaultCheck=true).")
            return@doLast
        }

        fun readProp(file: File, key: String): String? = file.takeIf { it.isFile }?.let {
            Properties().apply { it.inputStream().use(::load) }.getProperty(key)
        }?.trim()?.takeIf { it.isNotEmpty() }

        val key = "keen.java.default"
        val rootVal = readProp(rootPropsFile.asFile, key)
        val logicVal = readProp(buildLogicPropsFile.asFile, key)

        fun guidance(): String = """
            Define the property in BOTH files with the same value. Examples:
              • Root:        ${rootPropsFile.asFile}
                    $key=22
              • Build-logic: ${buildLogicPropsFile.asFile}
                    $key=22

            You can also set it per-invocation:
              ./gradlew assemble -P$key=22
        """.trimIndent()

        requireNotNull(rootVal) {
            "Missing '$key' in ${rootPropsFile.asFile}. \n\n${guidance()}"
        }
        requireNotNull(logicVal) {
            "Missing '$key' in ${buildLogicPropsFile.asFile}. \n\n${guidance()}"
        }

        // basic sanity
        val asInt = { s: String ->
            s.toIntOrNull() ?: throw GradleException(
                "Invalid $key value '$s' (must be an integer, e.g. 17, 21, 22).\n\n${guidance()}"
            )
        }
        val rootInt = asInt(rootVal)
        val logicInt = asInt(logicVal)

        if (rootInt != logicInt) {
            throw GradleException(
                "Inconsistent '$key': root=$rootInt, build-logic=$logicInt.\n\n${guidance()}"
            )
        }

        logger.lifecycle("✔ '$key' = $rootInt (root & build-logic match).")
    }
}

// Make every `assemble` in the build depend on the check
allprojects {
    tasks.matching { it.name == "assemble" }.configureEach {
        dependsOn(rootProject.tasks.named("verifyKeenJavaDefault"))
    }
}
//#region
