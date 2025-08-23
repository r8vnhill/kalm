@file:Suppress("SpellCheckingInspection")

/*
 * :examples — runnable samples that demonstrate how to use the library.
 * ---------------------------------------------------------------------
 * CC-friendly rules we follow here:
 *  • Avoid lambdas/SAMs that capture the script object (not serializable).
 *  • Use Provider-based inputs and serializable argument providers.
 *  • Decide task enablement at configuration time (no doFirst/onlyIf lambdas).
 */

import java.io.Serializable

plugins {
    id("knob.jvm")
    alias(libs.plugins.detekt)
    application
}

dependencies {
    // Align versions via in-repo BOM; contributes constraints only (no classes).
    implementation(platform(projects.dependencyConstraints))

    // Examples showcase public APIs from these modules.
    implementation(projects.core)
    implementation(projects.benchmark)
}

/*
 * Running examples conveniently
 * =============================
 *
 * Choose which `main` to run and pass arguments per invocation, without editing this file.
 *
 * Examples:
 * ---------
 *   # Bash
 *   ./gradlew :examples:run \
 *     -PexampleMain=com.acme.examples.MainKt \
 *     -PexampleArgs="--size 100 --verbose" \
 *     -PexampleJvmArgs="--enable-preview"
 *
 *   # PowerShell
 *   .\gradlew :examples:run `
 *     "-PexampleMain=com.acme.examples.MainKt" `
 *     "-PexampleArgs=--size 100 --verbose" `
 *     "-PexampleJvmArgs=--enable-preview"
 *
 * Properties:
 * -----------
 *   - exampleMain: fully-qualified main class (e.g., com.acme.examples.MainKt)
 *   - exampleArgs: space-separated program args
 *   - exampleJvmArgs: space-separated JVM args
 */

val exampleMain: Provider<String> = providers.gradleProperty("exampleMain")
val exampleArgs: Provider<List<String>> = providers.gradleProperty("exampleArgs")
    .map { it.split(Regex("""\s+""")).filter(String::isNotBlank) }
    .orElse(emptyList())

val exampleJvmArgs: Provider<List<String>> = providers.gradleProperty("exampleJvmArgs")
    .map { it.split(Regex("""\s+""")).filter(String::isNotBlank) }
    .orElse(emptyList())

// Pick a main if provided; the task itself will be disabled if not set (see below).
application {
    mainClass.set(exampleMain.orNull)
}

/*
 * Serializable argument providers
 * -------------------------------
 * We avoid inline SAMs (CommandLineArgumentProvider { … }) because those capture the script object and break the
 * configuration cache. These tiny classes hold only Provider values and are Serializable → safe for CC.
 */
class ArgsProvider(private val src: Provider<List<String>>) : CommandLineArgumentProvider, Serializable {
    override fun asArguments(): Iterable<String> = src.get()
}

class JvmArgsProvider(private val src: Provider<List<String>>) : CommandLineArgumentProvider, Serializable {
    override fun asArguments(): Iterable<String> = src.get()
}

tasks.named<JavaExec>("run") {
    // Enable/disable at configuration time to avoid onlyIf lambdas.
    // If no main is provided, the task is skipped with a clear message.
    val hasMain = exampleMain.map { it.isNotBlank() }.orElse(false)
    enabled = hasMain.get()
    if (!enabled) {
        logger.lifecycle("No main class configured. Provide -PexampleMain=com.example.MainKt")
    }

    // Ensure the task’s own mainClass is set from the property (isolated from the extension instance)
    mainClass.set(exampleMain)

    // Wire program/JVM args via serializable providers (CC-friendly).
    argumentProviders.add(ArgsProvider(exampleArgs))
    jvmArgumentProviders.add(JvmArgsProvider(exampleJvmArgs))
}

/*
 * Safety net: do not publish this module by mistake
 * ------------------------------------------------- */
plugins.withId("maven-publish") {
    extensions.configure<PublishingExtension> {
        publications.matching { true }.all {
            (this as? MavenPublication)?.suppressAllPomMetadataWarnings()
        }
    }
    tasks.matching { it.name.startsWith("publish") }.configureEach { enabled = false }
}
