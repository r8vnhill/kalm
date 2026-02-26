# Dependency update

Quick guide to refresh the version catalog and inspect available dependency upgrades.

## 1️⃣ Run updates

Ensure Gradle runs with the JDK expected by the build (see `gradle.properties`).

> [!TIP]
> If the IDE cannot manage the required JDK (e.g., on remote shells or CI):
> 1. Prefer the PowerShell helper (available cross-platform).
>    ```powershell
>    .\scripts\gradle\Invoke-GradleWithJdk.ps1 -JdkPath 'C:\Program Files\Java\jdk-22' -GradleArgument 'updateDependencies','--no-daemon'
>    ```
> 2. Use the Bash helper only when PowerShell is unavailable.
>    ```bash
>    ./scripts/gradle/invoke_gradle_with_jdk.sh --jdk /usr/lib/jvm/temurin-22 -- updateDependencies --no-daemon
>    ```

```powershell
# PowerShell example
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-22'
.\gradlew updateDependencies --no-daemon
```

### What this does

* **`dependencyUpdates`** — generates a report of available upgrades (no file changes).
* **`versionCatalogUpdate`** — applies updates to `gradle/libs.versions.toml`.

## 2️⃣ Apply manual edits

Some versions are mirrored from the version catalog to `gradle.properties` files for use in `settings.gradle.kts` (e.g., `foojay-resolver`). These are automatically synchronized by the `:syncVersionProperties` and `:syncBuildLogicVersionProperties` tasks, which run as part of `preflight`.

If you need to manually sync after updating the version catalog:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-22'
.\gradlew syncVersionProperties syncBuildLogicVersionProperties --no-daemon
```

This ensures that:
- `gradle.properties` → `plugin.foojay-resolver.version` matches the catalog
- `build-logic/gradle.properties` → `plugin.foojay-resolver.version` matches the catalog

## 3️⃣ Regenerate lockfiles & verify

Always refresh Gradle lockfiles after updating dependencies and commit the results.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-22'
.\gradlew --write-locks preflight --no-daemon
```

If you only need to sync locks without running the full preflight suite (for example when iterating quickly), you can target a specific task:

```powershell
.\gradlew --write-locks check --no-daemon
```

If plugin accessors need regeneration (after plugin ID/name changes):

```powershell
.\gradlew :build-logic:generatePrecompiledScriptPluginAccessors --no-daemon -Dorg.gradle.java.home='C:\Program Files\Java\jdk-17'
```

> Accessors must be generated using the JVM expected by `:build-logic` (usually JDK 17 or 21).

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
