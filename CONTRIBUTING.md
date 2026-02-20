# 🤝 Contributing

Contributions and ideas are welcome. Prospective contributors are encouraged to:

* Review the documentation and contribution guidelines under `dev-resources/`.
* Review the [Code of Conduct](./CODE_OF_CONDUCT.md).
* **If using LLM agents:** Review `AGENTS.md`, which defines agent policies and workflow constraints.

---

## Before Opening a Merge Request

Ensure that:

* `./gradlew verifyAll` passes (tests + static analysis + API checks).
* `./gradlew preflight --write-locks` is used **only** when dependency versions change.
* All lockfile updates (`gradle.lockfile`, `settings-gradle.lockfile`, `<module>/gradle.lockfile`) are committed
  alongside dependency changes.

---

## Wiki Updates

The `wiki/` submodule documents research-oriented content, including:

* Design rationale
* Algorithm analysis
* Experimental methodology

Update the wiki when:

* Architectural decisions change.
* Benchmarks are modified.
* Reproducibility constraints evolve.

---

## Preferred Workflow

For operational workflows requiring runtime arguments:

* Prefer a `tools/` CLI plus a `scripts/` wrapper.
* Use Gradle tasks primarily for deterministic wiring and orchestration.

---

## Wiki Conventions

* Document **alternatives considered** and the reasoning behind decisions.
* Include reproducible benchmark context (JVM version, hardware, problem size).

---

The project follows
the [Contributor Covenant v2.1](https://www.contributor-covenant.org/version/2/1/code_of_conduct.html), which
establishes standards for an inclusive, respectful, and harassment-free environment.

> By contributing to this project, contributors agree to uphold these standards.
