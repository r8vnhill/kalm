# KALM: Kotlin Algorithms for Learning and Metaheuristics

[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.20-blueviolet?logo=kotlin)](https://kotlinlang.org/)
[![Gradle](https://img.shields.io/badge/Gradle-8.14-blue?logo=gradle)](https://gradle.org/)
[![License: BSD-2-Clause](https://img.shields.io/badge/License-BSD--2--Clause-blue.svg)](LICENSE)
[![Pre-Alpha](https://img.shields.io/badge/status-pre--alpha-orange)](#)

**KALM** aims to be a flexible and extensible optimization framework for solving a wide range of optimization problems, including combinatorial, numerical, and multi-objective domains.

> [!warning] Project status: Early-stage / pre-alpha  
> KALM is currently under active development. At this stage, the repository primarily contains project configuration and build setup. No user-facing features are implemented yet.

## ✨ Goals (planned)
- Support for modular optimization algorithms (e.g., genetic algorithms, differential evolution)
- Extensible components for selection, mutation, and evaluation
- Integration with analysis and visualization tools

## 🚧 Current State
- ✅ Kotlin + Gradle project setup
- ❌ No functional optimization features implemented yet

## 🛠️ Getting Started

You can clone and build the project to explore the current structure.

```bash
git clone https://gitlab.com/r8vnhill/kalm.git
cd kalm
./gradlew build
```

### Running Gradle with a specific JDK

Prefer configuring the IDE first. When that is not possible (e.g., CI pipelines or remote shells), invoke Gradle via the helper scripts in this order:

1. PowerShell (recommended even on Unix when available)
	```powershell
	.\scripts\Invoke-GradleWithJdk.ps1 -JdkPath 'C:\Program Files\Java\jdk-22' -GradleArgument 'clean', 'build', '--no-daemon'
	```
2. Bash / POSIX shells (fallback compatibility)
	```bash
	./scripts/invoke_gradle_with_jdk.sh --jdk /usr/lib/jvm/temurin-22 -- clean build --no-daemon
	```

---

*This project is maintained by [Ignacio Slater-Muñoz](https://www.ravenhill.cl).*
