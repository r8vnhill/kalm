# KNOB Development Guidelines

*Last verified: 2025-08-23*


## Overview

* **Build system:** Gradle 9, Kotlin DSL, Kotlin 2.1.x, multi-module.
* **Custom convention plugins:** live under `build-logic`.
* **Toolchains:** Java 21 (configured by property **`knob.java.default`**).
* **Static analysis & coverage:** Detekt (`config/detekt/detekt.yml`) and Kover.
* **Testing:** JUnit Platform + Kotest (unit, assertions, property testing).

## Build & Toolchains

### Requirements

* JDK is provisioned automatically via **Gradle toolchains** (no local JDK required).
* Default version: **Java 21**, set by `knob.java.default` in both:

    * `gradle.properties` (root)
    * `build-logic/gradle.properties`
* Guard task: `verifyKnobJavaDefault` checks consistency and runs during `assemble`.

### Vector API

* Project uses **JVM incubator Vector API** in numeric code and tests.
* Build conventions automatically add:

    * **Compile-time:** `--add-modules jdk.incubator.vector`
    * **Test-time:** inherited via `-Ptest.jvmArgs` (defaults include the module).
* To disable (e.g. on CI):

  ```powershell
  ./gradlew test "-Ptest.jvmArgs="
  ```

### Common Gradle Commands

```powershell
# Clean build (tests included)
./gradlew build

# Assemble only (ABI check runs)
./gradlew assemble

# Static analysis (Detekt all projects)
./gradlew lint

# Coverage report (Kover)
./gradlew koverHtmlReport
```

### Performance Toggles (`gradle.properties`)

* Defaults:

    * `org.gradle.configuration-cache=true`
    * `org.gradle.parallel=true`
    * `org.gradle.caching=true`
* Daemon memory: `org.gradle.jvmargs=-Xmx4g`
  (override in `~/.gradle/gradle.properties`).
* For constrained environments:

  ```powershell
  ./gradlew test "-Ptest.maxParallelForks=1"
  ./gradlew test "-Ptest.jvmArgs="
  ```

---

## Testing

### Conventions

* All modules use **JUnit Platform** via `knob.testing` convention.
* Deterministic defaults: encoding = UTF-8, locale = en-US, timezone = UTC.
* Configurable test properties:

    * `test.showStandardStreams` (default: false)
    * `test.includeTags` / `test.excludeTags`
    * `test.maxParallelForks` (default: `min(cores, 8)`)
    * `test.forkEvery` (0 = disabled)
    * `test.jvmArgs` (defaults include Vector API)

Shared utilities live in `:utils:test-commons`.
Use `testImplementation(projects.utils.testCommons)` to depend on them.

### Running Tests

```powershell
# All modules
./gradlew test

# Per module
./gradlew :core:test
./gradlew :utils:math:test
./gradlew :utils:domain:test
./gradlew :utils:test-commons:test

# One test class
./gradlew :core:test --tests "cl.ravenhill.knob.repr.GradientFreeSpec"
```

### Adding Tests

* Use **Kotest FreeSpec** and **property-based tests** (`io.kotest.property`).
* Place tests under `<module>/src/test/kotlin` following package structure.
* Prefer generators/matchers from `:utils:test-commons`.
* Domain-specific 

## Static Analysis & Style

* **Detekt:** root config at `config/detekt/detekt.yml`.
* **Lint task:** `./gradlew lint` runs Detekt across all subprojects.
* **IDE style:** `dev-resources/KeenCodeStyle.xml` (IntelliJ scheme).

## Dependencies & Binary Compatibility

* **Version catalog:** `gradle/libs.versions.toml`, managed with `versionCatalogUpdate`.
* Update reports: `./gradlew dependencyMaintenance`.
* **Non-stable versions rejected by default.**
* **Binary compatibility validator:** enabled at root; excludes `:examples`, `:benchmark`, `:utils:test-commons`.

## Troubleshooting

* **Memory constraints:**

    * Run with `-Ptest.maxParallelForks=1`.
    * Disable Vector API: `-Ptest.jvmArgs=`.
* **Java default mismatch:**

    * Ensure `knob.java.default` is the same in both properties files.
    * Bypass guard with `-PskipJavaDefaultCheck=true` (use only in emergencies).
* **IDE sync:**

    * Ensure Gradle JVM in IDE is compatible (JDK 21).

## Conventions Recap

* Apply conventions via build-logic plugins:

    * `knob.jvm`: toolchain, compiler opts, vector module args.
    * `knob.library`: library conventions + testing.
    * `knob.testing`: JUnit Platform, deterministic env, property-driven knobs.
    * `knob.reproducible`: stable archives, timestamps, and order.
