# Dependency Locking: How to Use and Troubleshoot

This project uses **Gradle Dependency Locking** to ensure reproducible builds across machines and CI.
Lock files capture the exact versions that were resolved for each configuration.

---

## Table of Contents

- [Dependency Locking: How to Use and Troubleshoot](#dependency-locking-how-to-use-and-troubleshoot)
  - [Table of Contents](#table-of-contents)
  - [How Locking Is Configured in This Repository](#how-locking-is-configured-in-this-repository)
    - [Example: Before and After](#example-before-and-after)
  - [Day-to-Day Use](#day-to-day-use)
    - [Common Commands](#common-commands)
  - [Troubleshooting](#troubleshooting)
    - [❗ “Configuration ':core:XYZ' is locked but does not have lock state”](#-configuration-corexyz-is-locked-but-does-not-have-lock-state)
      - [Cause:](#cause)
      - [Fix](#fix)
    - [⚠️ Detekt Fails Under Strict Locking](#️-detekt-fails-under-strict-locking)
      - [Symptom](#symptom)
      - [Fix](#fix-1)
    - [🔄 After Changing `libs.versions.toml`](#-after-changing-libsversionstoml)
    - [⚙️ Configuration Cache Warnings](#️-configuration-cache-warnings)
    - [📦 Dependency Update Tasks](#-dependency-update-tasks)
  - [Policies and CI Checklist](#policies-and-ci-checklist)
    - [✅ CI Quick Checks](#-ci-quick-checks)
  - [Why Avoid `allprojects { … }` for Global Wiring?](#why-avoid-allprojects----for-global-wiring)
  - [📚 Further Reading](#-further-reading)

---

## How Locking Is Configured in This Repository

* Locking is applied via a **convention plugin**, not with top-level `allprojects` blocks:

  * Plugin file: `build-logic/src/main/kotlin/kalm.dependency-locking.gradle.kts`
  * It configures:

    * `lockAllConfigurations()` — locks every configuration in the target project
    * `lockMode = LockMode.STRICT` (Gradle 8.3+) — fails fast when a configuration resolves without lock state
  * The convention is applied by other project plugins (for example, `kalm.jvm`), so modules opt in explicitly.
  * **Rationale:** avoids the pitfalls of global `allprojects` configuration and works better with included builds and the configuration cache.

* Each module stores its own lock file:

  * `core/gradle.lockfile`
  * Root and settings builds can also persist lock state.

> [!IMPORTANT]
> Using a convention plugin instead of global configuration isolates setup per module, improves reproducibility, and maintains configuration-cache compatibility.

### Example: Before and After

```kotlin
// ❌ Legacy global configuration
allprojects {
    dependencyLocking {
        lockAllConfigurations()
        lockMode.set(LockMode.STRICT)
    }
}

// ✅ Preferred convention plugin approach
plugins {
    id("kalm.dependency-locking")
}
```

---

## Day-to-Day Use

Most of the time you don’t need to think about locks — Gradle reads from them automatically.
You only need to update locks when:

* A new dependency or configuration appears.
* The version catalog changes (e.g., updated Kotlin or libraries).

When that happens, regenerate locks with `--write-locks`.

### Common Commands

```pwsh
# Write lock state for main compile classpath of :core
./gradlew :core:compileKotlin --write-locks --no-daemon --no-parallel

# Write lock state for test compile/runtime classpaths
./gradlew :core:compileTestKotlin --write-locks --no-daemon --no-parallel
./gradlew :core:test --write-locks --no-daemon --no-parallel

# Detekt configuration
./gradlew :core:detekt --write-locks --no-daemon --no-parallel

# Alternative: resolve a specific configuration
./gradlew :core:dependencies --configuration testCompileClasspath --write-locks --no-daemon --no-parallel
```

> [!NOTE]
> Use `--no-parallel` for dependency maintenance or report tasks (some are not configuration-cache friendly).
> Running a real task (`compileKotlin`, `test`, etc.) is the easiest way to refresh the correct lock file.

---

## Troubleshooting

### ❗ “Configuration ':core:XYZ' is locked but does not have lock state”

#### Cause

Strict mode is on and the configuration has no recorded lock state.

#### Fix

Rerun a task that resolves the configuration with `--write-locks`:

```pwsh
./gradlew :core:compileTestKotlin --write-locks
./gradlew :core:test --write-locks
./gradlew :core:dependencies --configuration detekt --write-locks
```

Then rerun normally (without `--write-locks`).

---

### ⚠️ Detekt Fails Under Strict Locking

#### Symptom

> “Configuration is locked but does not have lock state.”

#### Fix

```pwsh
./gradlew :core:detekt --write-locks
./gradlew :core:detekt
```

---

### 🔄 After Changing `libs.versions.toml`

When bumping library versions (e.g., Kotlin, Kotest):

```pwsh
./gradlew :core:compileKotlin --write-locks
./gradlew :core:compileTestKotlin --write-locks
./gradlew :core:test --write-locks
```

> [!TIP]
> You can run a broader *preflight* task (if defined), but note that dependency update or report tasks might not be configuration-cache compatible.

---

### ⚙️ Configuration Cache Warnings

Expected situations:

* You passed `--write-locks` (forces reconfiguration).
* An IDE init script changed (e.g., IntelliJ `ijWrapper*.gradle`).

> [!NOTE]
> These only skip cached configurations; they do **not** affect correctness.

---

### 📦 Dependency Update Tasks

`dependencyUpdates` or `versionCatalogUpdate` may trigger cache warnings — this is expected.
Prefer `--no-parallel` when running them.

---

## Policies and CI Checklist

* Keep **strict mode** on — it surfaces missing locks early.
  To fix missing locks, temporarily comment out `lockMode = LockMode.STRICT`, regenerate, then restore it.
* Use provider-safe catalog lookups (`libs.findLibrary("...")`) to keep configuration lazy.
* Review lockfiles in PRs — they’re part of reproducibility.

> [!TIP]
> Lockfiles are part of your reproducibility story — treat them as versioned source of truth, not transient artifacts.

### ✅ CI Quick Checks

* Run `preflight` or `:core:compileKotlin --write-locks` **once after catalog updates**.
* **Do not** generate locks in every CI build — treat it as a controlled maintenance step.
* Fail builds if lock mismatches occur (`LockMode.STRICT` enforces this).

> [!WARNING]
> Never disable strict mode globally — use it to ensure consistent dependency resolution across contributors and CI environments.

---

## Why Avoid `allprojects { … }` for Global Wiring?

Gradle strongly discourages global cross-project configuration.

> “Avoid cross-project configuration using subprojects and allprojects.”
> — [Gradle User Manual: Sharing Build Logic Between Subprojects](https://docs.gradle.org/current/userguide/sharing_build_logic_between_subprojects.html)

Community guidance aligns with this:

> “`subprojects` and `allprojects` should be avoided to prevent cross-project configuration.”
> — [Gradle Forum Discussion](https://discuss.gradle.org/t/how-to-avoid-cross-project-configuration-without-buildscripts/44199)

> [!IMPORTANT]
> Global configuration via `allprojects` runs eagerly across all included builds, introduces implicit dependencies, and breaks configuration-cache isolation.
> Convention plugins and explicit per-module configuration make builds faster, clearer, and easier to maintain.

---

## 📚 Further Reading

* **Gradle User Manual – Dependency Locking:**
  [https://docs.gradle.org/current/userguide/dependency_locking.html](https://docs.gradle.org/current/userguide/dependency_locking.html)
* **Gradle API – `DependencyLockingHandler`:**
  [https://docs.gradle.org/current/javadoc/org/gradle/api/artifacts/dsl/DependencyLockingHandler.html](https://docs.gradle.org/current/javadoc/org/gradle/api/artifacts/dsl/DependencyLockingHandler.html)
* **Gradle Blog – Building Maintainable Multi-Project Builds:**
  [https://blog.gradle.org/structuring-gradle-builds](https://blog.gradle.org/structuring-gradle-builds)
* **StackOverflow – How to Lock Dependencies in Gradle:**
  [https://stackoverflow.com/questions/65350214/how-do-i-lock-dependencies-in-gradle-6-7-1](https://stackoverflow.com/questions/65350214/how-do-i-lock-dependencies-in-gradle-6-7-1)
* **Gradle Forum – Avoiding Cross-Project Configuration:**
  [https://discuss.gradle.org/t/how-to-avoid-cross-project-configuration-without-buildscripts/44199](https://discuss.gradle.org/t/how-to-avoid-cross-project-configuration-without-buildscripts/44199)
