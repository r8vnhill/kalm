# Dependency update

Quick guide to refresh the version catalog and inspect available dependency upgrades.

## 1️⃣ Run updates

Ensure Gradle runs with the JDK expected by the build (see `gradle.properties`).

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-22'
.\gradlew updateDependencies --no-daemon
```

### What this does

* **`dependencyUpdates`** — generates a report of available upgrades (no file changes).
* **`versionCatalogUpdate`** — applies updates to `gradle/libs.versions.toml`.

## 2️⃣ Apply manual edits

Some versions (e.g., toolchain, Foojay resolver) are defined in `gradle.properties` and must be updated manually when needed.

## 3️⃣ Verify the build

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-22'
.\gradlew clean build --no-daemon
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

Manual:
- gradle.properties: plugin.foojay-resolver.version 1.0.0 → 1.1.0
```

## Notes

* VCU does **not** modify `gradle.properties`; edit those entries manually.
* Ben-Manes rejects unstable candidates (`alpha`, `beta`, `rc`, etc.) by default.
* If Gradle fails with *“Dependency requires at least JVM runtime X”*, rerun with that JDK.
* Always review diffs in `gradle/libs.versions.toml` and the reports under `build/dependencyUpdates/`.

>[!TIP] That’s it!
> Run `updateDependencies`, review the reports, and verify with a full build before committing.
