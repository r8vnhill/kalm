# KNOB: Kotlin Numerical Optimization Base

[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.20-blueviolet?logo=kotlin)](https://kotlinlang.org/)
[![Gradle](https://img.shields.io/badge/Gradle-8.14-blue?logo=gradle)](https://gradle.org/)
[![License: BSD-2-Clause](https://img.shields.io/badge/License-BSD--2--Clause-blue.svg)](LICENSE)
[![Pre-Alpha](https://img.shields.io/badge/status-pre--alpha-orange)](#)
[![Pipeline status](https://gitlab.com/r8vnhill/knob/badges/main/pipeline.svg)](https://gitlab.com/r8vnhill/knob/-/pipelines)

**KNOB** aims to be a flexible and extensible optimization framework for solving a wide range of optimization problems, including combinatorial, numerical, and multi-objective domains.

> [!warning] Project status: Early-stage / pre-alpha  
> KNOB is currently under active development. At this stage, the repository primarily contains project configuration and build setup. No user-facing features are implemented yet.

## ✨ Goals (planned)
- Support for modular optimization algorithms (e.g., genetic algorithms, differential evolution)
- Extensible components for selection, mutation, and evaluation
- Integration with analysis and visualization tools

## 🚧 Current State
- ✅ Kotlin + Gradle project setup
- ❌ No functional optimization features implemented yet

### Modules (selected)
- `:core` — Core KNOB primitives (Problem, Solution, Objectives, Constraints)
- `:utils:*` — Domain/math/test utilities
- `:ec:production` — Production-ready EC algorithms (e.g., genetic algorithms)
- `:ec:academic` — Academic/illustrative EC algorithms (may depend on `:ec:production`)

## 🛠️ Getting Started

You can clone and build the project to explore the current structure:

```bash
git clone https://gitlab.com/r8vnhill/knob.git
cd knob
./gradlew build
```

---

*This project is maintained by [Ignacio Slater-Muñoz](https://www.ravenhill.cl).*
