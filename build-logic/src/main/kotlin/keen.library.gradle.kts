/*
 * Test conventions applied to any project that applies the `keen.library` convention.
 *
 * === Goals ===
 *  - Use JUnit Platform (JUnit 5) everywhere
 *  - Keep test runs deterministic (locale/timezone/encoding)
 *  - Make behavior configurable via Gradle properties (no code edits for CI/local)
 *  - Stay lazy/configuration‑cache friendly (`configureEach`, Providers)
 *
 * Supported Gradle properties (override in gradle.properties or -P...):
 *  - test.showStandardStreams=true|false → print stdout/stderr from tests (default: false)
 *  - test.includeTags=fast,unit → only run tests with these JUnit tags
 *  - test.excludeTags=slow,integration → exclude tests with these tags
 *  - test.maxParallelForks=4 → cap parallel forks (default: min(cores, 8))
 *  - test.forkEvery=0 → fork every N tests (0 = no per‑N forking)
 *  - test.jvmArgs=--add-opens ... → extra JVM args for test forks (space‑separated)
 *  - test.exceptionFormat=FULL|SHORT → logging verbosity for exceptions (default: FULL)
 */

import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.internal.impldep.org.junit.platform.launcher.TagFilter.excludeTags
import org.gradle.internal.impldep.org.junit.platform.launcher.TagFilter.includeTags

// === Property providers (lazy; evaluated only when needed) ===

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
        if (include.isNotEmpty()) includeTags(include)
        if (exclude.isNotEmpty()) excludeTags(exclude)
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
