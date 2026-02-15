# KALM: Kotlin Algorithms for Learning and Metaheuristics

[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.20-blueviolet?logo=kotlin)](https://kotlinlang.org/)
[![Gradle](https://img.shields.io/badge/Gradle-8.14-blue?logo=gradle)](https://gradle.org/)
[![License: BSD-2-Clause](https://img.shields.io/badge/License-BSD--2--Clause-blue.svg)](LICENSE)
[![Pre-Alpha](https://img.shields.io/badge/status-pre--alpha-orange)](#)

> [!warning] Project status: Early-stage / pre-alpha
> This project is in active development. APIs and features are subject to change.

**Planned features:**
- Support for modular optimization algorithms (e.g., genetic algorithms, differential evolution)
- Extensible components for selection, mutation, and evaluation
- Integration with analysis and visualization tools

## 🚧 Current State
- ✅ Kotlin + Gradle project setup
- ✅ Docker-based reproducible build environment (fully functional)
- ⚠️ Project is not yet executable — Docker container and CI/CD infrastructure were prioritized to ensure reproducible development and testing before implementing core features
- ❌ No functional optimization features implemented yet

## 🛠️ Getting Started

### Requirements

- **JDK 22 or newer** (JDK 25 is not yet supported by Detekt; use JDK 22 or 23)
- Gradle 8.14+ (included via wrapper)

You can clone and build the project to explore the current structure.

```bash
git clone https://gitlab.com/r8vnhill/kalm.git
cd kalm
./gradlew preflight
```

### Running Gradle with a specific JDK

Prefer configuring the IDE first. When that is not possible (e.g., CI pipelines or remote shells), invoke Gradle via the helper scripts in this order:

1. PowerShell (recommended even on Unix when available)
	```powershell
	.\scripts\gradle\Invoke-GradleWithJdk.ps1 -JdkPath 'C:\Program Files\Java\jdk-22' -GradleArgument 'clean', 'build', '--no-daemon'
	```
2. Bash / POSIX shells (fallback compatibility)
	```bash
	./scripts/gradle/invoke_gradle_with_jdk.sh --jdk /usr/lib/jvm/temurin-22 -- clean build --no-daemon
	```

### Syncing GitLab and GitHub Mirrors

The project maintains GitLab as the primary repository with a GitHub mirror. To synchronize your local branch with both remotes:

```powershell
.\scripts/git/Sync-Remotes.ps1
```

See [dev-resources/SYNC_REMOTES.md](dev-resources/SYNC_REMOTES.md) for detailed usage and troubleshooting.

### Git & Submodule Automation

The project includes PowerShell scripts for Git and wiki submodule workflows:

```powershell
# Sync entire repo + all submodules
./scripts/git/Sync-RepoAndWiki.ps1

# Sync only wiki submodule (and optionally update pointer)
./scripts/git/Sync-WikiOnly.ps1 -UpdatePointer
```

See **[`scripts/README.md`](scripts/README.md)** for comprehensive documentation on all automation scripts.

Recommendation: When your environment supports PowerShell 7.4+, prefer using the project's provided automation scripts in the `scripts/` directory (for example, `Sync-RepoAndWiki.ps1` and `Sync-WikiOnly.ps1`) for routine Git and submodule tasks — they include safety checks and are validated by PSScriptAnalyzer.

---

## 📦 Consuming KALM

Once artefacts are published (local or remote Maven repository), import the BOM to keep dependencies aligned:

```kotlin
dependencies {
	implementation(platform("cl.ravenhill.kalm:kalm-platform:0.2.2"))
	implementation("cl.ravenhill.kalm:kalm-core:0.2.2")
}
```

Update the coordinates to match the release you consume or your locally published snapshot.

## 🔁 Reproducible builds

Gradle dependency lockfiles (`gradle.lockfile`, `settings-gradle.lockfile`, `<module>/gradle.lockfile`) are version-controlled to guarantee deterministic builds. Regenerate them whenever dependencies change and commit the results:

```powershell
$env:JAVA_HOME = '/path/to/jdk-22'
./gradlew --write-locks preflight --no-daemon
```

For detailed guidance and troubleshooting on dependency locking (strict mode, writing locks, and common errors), see [`dev-resources/DEPENDENCY_LOCKING.md`](dev-resources/DEPENDENCY_LOCKING.md).

## 🧪 Quality & Verification

Run all verification in one go:

```powershell
./gradlew verifyAll
```

This aggregates tests, Detekt static analysis, and API surface checks across modules. For focused static analysis and formatting (powered by the RedMadRobot Detekt plugin via the `kalm.detekt-redmadrobot` convention):

```powershell
# Run Detekt across all modules
./gradlew detektAll
# Check only changes vs. main (faster local and CI diffs)
./gradlew detektDiff

# Auto-format Kotlin sources
./gradlew detektFormat
```

Advanced configuration examples (e.g., diff branch selection, file extensions) live in `dev-resources/DOCUMENTATION_RULES.md` under “RedMadRobot Detekt Plugin”.

## 📚 Research Documentation

For algorithm design rationale, complexity analysis, experimental methodology, and design decisions, see the [**project wiki**](https://gitlab.com/r8vnhill/kalm/-/wikis/home).

The wiki documents:
- **Why** certain abstractions were chosen over alternatives (e.g., self-referential types, immutability trade-offs)
- Convergence guarantees and parameter tuning for implemented algorithms
- Benchmark setup and reproducibility guidelines
- Challenges faced during implementation and lessons learned

To fetch wiki content locally as a submodule:
```bash
git submodule update --init --recursive
```

Or sync to the latest wiki version:
```bash
./gradlew syncWiki
```

---

*This project is maintained by [Ignacio Slater-Muñoz](https://www.ravenhill.cl).*
