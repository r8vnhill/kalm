/*
 * Test conventions applied to any project that applies the `knob.library` convention.
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

plugins {
    id("knob.jvm") // JVM conventions (toolchain, compiler, etc.)
    id("knob.testing") // All published libraries should have tests!
}

/*
 * Enforce explicit API for a library (recommended when stabilizing the API).
 * This forces public/protected declarations to be explicitly marked (visibility, return types).
 */
kotlin {
    explicitApi()
}
