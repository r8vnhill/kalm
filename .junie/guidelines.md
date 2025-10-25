# Project-specific development guidelines

This document captures practical, project-specific knowledge for building, testing, and contributing to this repository. It is intended for experienced developers and focuses on conventions and pitfalls unique to this project rather than generic Gradle/Kotlin guidance.

## 1. Build and configuration

### Gradle and convention plugins
- The repository is a Gradle multi-project with custom convention plugins under `build-logic`:
  - `keen.jvm`: standardizes the Java/Kotlin toolchain (see `build-logic/src/main/kotlin/utils/JvmToolchain.kt`). The toolchain must be available locally; Gradle can download matching JDKs if configured to do so.
  - `keen.library`: enforces unified test configuration for all modules (JUnit Platform with verbose logging of passed/skipped/failed). No per-module duplication is required.
  - `keen.reproducible`: configures reproducible archives. Avoid adding non-deterministic metadata to artifacts.
- Root `build.gradle.kts` enables:
  - Binary compatibility validation (`org.jetbrains.kotlinx.binary-compatibility-validator`). API dump files live under `core/api/*`. Keep them in sync when public APIs are changed.
  - Detekt for static analysis with config in `config/detekt/detekt.yml`. The formatting plugin is applied where relevant.

### Toolchains
- Java/Kotlin versions are controlled centrally. Do not hardcode versions in module build files; rely on build-logic. If a specific Java version is required for a task, update `utils/JvmToolchain.kt` and the convention plugins rather than individual modules.

### Dependency versions
- All versions and bundles are managed in `gradle/libs.versions.toml`. Kotest dependencies are provided as a bundle (`bundles.kotest`) which includes the JUnit5 runner. Prefer adding libraries by reference here and consuming them in modules.

### Module layout
- `core`: primary library code and public API checks (see `core/api/core.api`). Changing public signatures requires updating the API dump (see Testing/Verification section below).

## 2. Testing

### Framework and platform
- Kotest 6 (milestone) is used with the JUnit Platform. The `keen.library` convention applies `useJUnitPlatform()` to all Test tasks, and enables detailed logging including stdout.

### Running tests
From the repository root:

- Any OS:
  ```sh
  ./gradlew test
  ```
- Windows:
  ```powershell
  .\gradlew.bat test
  ```
- Per-module test tasks also work (e.g., `:core:test`) but are rarely necessary due to small size.

Known environment constraint: if Gradle fails very early with `java.lang.IllegalArgumentException: 25` coming from `org.jetbrains.kotlin.com.intellij.util.lang.JavaVersion.parse`, your Gradle daemon is running on a too‑new JDK (e.g., Java 25) that the embedded Kotlin parser cannot recognize.

Workarounds:
- Run Gradle with a supported JDK (e.g., 17 or 21). Examples:
  - Temporarily set `JAVA_HOME` to a compatible JDK before invoking Gradle.
  - Or add `org.gradle.java.home=<path-to-jdk-17-or-21>` in your local `gradle.properties` (do not commit machine-specific paths).
  - In IntelliJ/IDEA, set Gradle JVM to a supported JDK under Settings > Build Tools > Gradle.
- The project itself configures the compile toolchain via build-logic; this message concerns only the JDK used by Gradle to run the build, not the target toolchain.

### Adding tests
- Place tests under `<module>/src/test/kotlin` using the production package structure.
- Kotest styles are supported (e.g., StringSpec, FunSpec). The JUnit5 runner is on the classpath via the Kotest bundle.
- Example minimal test (validated locally during guideline authoring):
  - Location: `core/src/test/kotlin/cl/ravenhill/kalm/engine/SimpleHillClimberTest.kt`
  - Content:
    
    ```kotlin
    class SimpleHillClimberTest : StringSpec({
        "optimizes quadratic from zero to zero (local optimum)" {
            val engine = SimpleHillClimber(objectiveFunction = { -(it.x * it.x) + 4 })
            val result = engine.optimize(ScalarFeature(0.0))
            result.x.shouldBeExactly(0.0)
        }
        "walks uphill from negative to zero with step=0.1" {
            val engine = SimpleHillClimber(objectiveFunction = { -(it.x * it.x) + 4 }, stepSize = 0.1)
            val result = engine.optimize(ScalarFeature(-1.0))
            result.x.shouldBeExactly(0.0)
        }
    })
    ```
    
    Required imports (add at the top of the test file):
  - `cl.ravenhill.kalm.engine.SimpleHillClimber`
  - `cl.ravenhill.kalm.repr.ScalarFeature`
    - `io.kotest.core.spec.style.StringSpec`
    - `io.kotest.matchers.doubles.shouldBeExactly`

Guidelines:
- Prefer deterministic tests; the optimization engine is simple and should allow exact assertions for the default step sizes.
- If you change step sizes or termination logic, update tests accordingly and avoid floating point flakiness (use tolerances if needed).

### Static analysis and API checks
- Run Detekt:
  ```sh
  ./gradlew detekt
  ```
- Run API checks:
  ```sh
  ./gradlew apiCheck
  ```
- If public API changes are intended, update the API dump:
  ```sh
  ./gradlew apiDump
  ```
  and commit the updated files under `core/api`.

## 3. Development conventions and tips

### Code style
- Kotlin style is defined in `dev-resources/KeenCodeStyle.xml`. Import into your IDE to match formatter/wrapping preferences used in this project.

### Git workflow helpers
- Use standard `git` commands for local and CI workflows. See `dev-resources/GIT_STANDARD.md` for recommended patterns and examples. If you want to add language- or shell-specific helpers (Bash/Zsh/etc.), place them under `scripts/git/<shell>/` and document them in `dev-resources/GIT_STANDARD.md`.

### Documentation and CI/CD
- Additional rules are found in `dev-resources/*`. Keep `dev-resources/CI_CD.md` in mind when modifying build logic or adding tasks.

### Common pitfalls
- If tests don’t discover Kotest specs, ensure `useJUnitPlatform()` is applied (it is via `keen.library`) and that the Kotest runner dependency is present (it is included in the Kotest bundle). Also verify your test class is in `src/test/kotlin` and ends with `Test` or is a public class recognized by Kotest.
- The toolchain is required; Gradle may download a matching JDK for you. If your environment blocks downloads, pre-install the target JDK and point Gradle to it via `org.gradle.java.installations` or by adjusting `utils/JvmToolchain.kt`.
- The binary compatibility validator requires keeping `core/api/core.api` in sync. If you see `apiCheck` failures after public API changes, run `apiDump` and review differences before committing.

## Validation note
- The example test above was created and executed successfully during authoring to confirm the instructions. It was then removed to keep the repository clean; follow the steps under “Adding tests” to recreate it locally if needed.