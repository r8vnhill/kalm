# Dependency update

Quick guide to refresh the version catalog and inspect available dependency upgrades.

## 1️⃣ Run updates

**Recommended: Use the Docker container for guaranteed reproducibility:**

```bash
# Using docker compose (recommended)
docker compose run --rm kalm ./gradlew updateDependencies --no-daemon
```

**Alternative: Use local Gradle wrapper**

Ensure Gradle runs with the JDK expected by the build (see `gradle.properties`).

```bash
# The Gradle wrapper will use JAVA_HOME or the configured toolchain
./gradlew updateDependencies --no-daemon
```

### What this does

* **`dependencyUpdates`** — generates a report of available upgrades (no file changes).
* **`versionCatalogUpdate`** — applies updates to `gradle/libs.versions.toml`.

## 2️⃣ Apply manual edits

Some versions are mirrored from the version catalog to `gradle.properties` files for use in `settings.gradle.kts` (e.g., `foojay-resolver`). These are automatically synchronized by the `:syncVersionProperties` and `:syncBuildLogicVersionProperties` tasks, which run as part of `preflight`.

If you need to manually sync after updating the version catalog:

```bash
# Using Docker (recommended)
docker compose run --rm kalm ./gradlew syncVersionProperties syncBuildLogicVersionProperties --no-daemon

# Or locally
./gradlew syncVersionProperties syncBuildLogicVersionProperties --no-daemon
```

This ensures that:
- `gradle.properties` → `plugin.foojay-resolver.version` matches the catalog
- `build-logic/gradle.properties` → `plugin.foojay-resolver.version` matches the catalog

## 3️⃣ Regenerate lockfiles & verify

Always refresh Gradle lockfiles after updating dependencies and commit the results.

```bash
# Using Docker (recommended)
docker compose run --rm kalm ./gradlew --write-locks preflight --no-daemon

# Or locally
./gradlew --write-locks preflight --no-daemon
```

If you only need to sync locks without running the full preflight suite (for example when iterating quickly), you can target a specific task:

```bash
./gradlew --write-locks check --no-daemon
```

If plugin accessors need regeneration (after plugin ID/name changes):

```bash
# Using Docker (handles JDK version automatically)
docker compose run --rm kalm ./gradlew :build-logic:generatePrecompiledScriptPluginAccessors --no-daemon

# Or locally (ensure correct JDK version)
./gradlew :build-logic:generatePrecompiledScriptPluginAccessors --no-daemon
```

## 4️⃣ Commit example

```
⬆️ chore(deps): update catalog via version-catalog-update

Updated:
- org.jetbrains.kotlin:kotlin-gradle-plugin 2.2.0-RC → 2.2.20-Beta2
- foojay-resolver 1.0.0 → 1.1.0 (auto-synced to gradle.properties)
```

## Notes

* The `:syncVersionProperties` and `:syncBuildLogicVersionProperties` tasks automatically keep property versions synchronized with the version catalog.
* Ben-Manes rejects unstable candidates (`alpha`, `beta`, `rc`, etc.) by default.
* If Gradle fails with *"Dependency requires at least JVM runtime X"*, rerun with that JDK.
* Always review diffs in `gradle/libs.versions.toml`, the `gradle.properties` files, the generated lockfiles (`gradle.lockfile`, `settings-gradle.lockfile`, `<module>/gradle.lockfile`), and the reports under `build/dependencyUpdates/`.

>[!TIP] That’s it!
> Run `updateDependencies`, review the reports, and verify with a full build before committing.
