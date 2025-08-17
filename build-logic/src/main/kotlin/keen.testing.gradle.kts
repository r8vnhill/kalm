/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import utils.resolveDefaultJavaVersion

// === Property providers (lazy; evaluated only when needed) ===

// Lazily resolve the default Java version as a Provider<Int> (e.g., 17, 21, 22)
val defaultJava: Provider<Int> = providers.resolveDefaultJavaVersion()

// Print stdout/stderr from tests (off by default to keep CI noise low)
val showStdStreams = providers.gradleProperty("test.showStandardStreams")
    .map(String::toBoolean)
    .orElse(false)

// Comma‑separated JUnit tags to include/exclude (empty = no filter)
val includeTagsProp = providers.gradleProperty("test.includeTags").orElse("")
val excludeTagsProp = providers.gradleProperty("test.excludeTags").orElse("")

// Parallelism: default to a sensible cap to avoid oversubscription on dev laptops/CI agents
val maxForksProp = providers.gradleProperty("test.maxParallelForks")
    .map(String::toInt)
    .orElse(Runtime.getRuntime().availableProcessors().coerceAtMost(8))

// Fork a new JVM after N tests (0 = don’t)
val forkEveryProp = providers.gradleProperty("test.forkEvery")
    .map(String::toLong)
    .orElse(0L)

// Extra JVM args for test forks (space‑separated string → List<String>)
val extraJvmArgs = providers.gradleProperty("test.jvmArgs")
    .map { it.split(Regex("""\s+""")).filter(String::isNotBlank) }
    .orElse(emptyList())

// Exception format for test logs; robust mapping with a safe default
val exceptionFormatProvider = providers.gradleProperty("test.exceptionFormat")
    .map(String::uppercase)
    .map { runCatching { TestExceptionFormat.valueOf(it) }.getOrDefault(TestExceptionFormat.FULL) }
    .orElse(TestExceptionFormat.FULL)

// === Conventions for all Test tasks ===

tasks.withType<Test>().configureEach {
    // Always use JUnit Platform (JUnit 5 and compatible engines)
    useJUnitPlatform()

    // Deterministic environment across OS/locales (fewer "works on my machine" issues)
    systemProperty("file.encoding", "UTF-8")
    systemProperty("user.language", "en")
    systemProperty("user.country", "US")
    systemProperty("user.timezone", "UTC")

    // Parallelism & forking (users/CI can override via Gradle properties)
    maxParallelForks = maxForksProp.get()
    forkEvery = forkEveryProp.get()
    jvmArgs(extraJvmArgs.get())

    // Optional JUnit tag filters (empty lists mean "no filter")
    useJUnitPlatform {
        val include = includeTagsProp.get()
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
        val exclude = excludeTagsProp.get()
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
        if (include.isNotEmpty()) includeTags(*include.toTypedArray())
        if (exclude.isNotEmpty()) excludeTags(*exclude.toTypedArray())
    }

    // Logging: focused by default; opt‑in to stdout/stderr via property
    testLogging {
        events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
        showStandardStreams = showStdStreams.get()
        exceptionFormat = exceptionFormatProvider.get()
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}

// compute a JavaLanguageVersion from property
val defaultJavaLang: Provider<JavaLanguageVersion> = defaultJava.map(JavaLanguageVersion::of)

// get a launcher for the chosen toolchain
val toolchains = project.extensions.getByType(JavaToolchainService::class.java)
val testLauncher = toolchains.launcherFor {
    languageVersion.set(defaultJavaLang)
}

tasks.withType<Test>().configureEach {
    // run tests on the same toolchain JDK, not the Gradle daemon JDK
    javaLauncher.set(testLauncher)

    useJUnitPlatform()
}
