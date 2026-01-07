# KALM Dockerization Plan (Phased)

Goal: introduce a single OCI/Docker-based "base build & experiments" image that improves reproducibility for scientific investigations and CI, while keeping the transition incremental and low-risk.

This plan is for maintainers and CI authors, not consumers of the library.

---

## Phase 0 – Preconditions & Design Check

**Outcome:** Agreement on scope, base assumptions, and where docs will live.

1. Confirm core assumptions
   - Container format: OCI image, built and run primarily with Docker; Podman supported as a drop-in alternative.
   - Target environment: Linux base image compatible with current CI (PowerShell 7.4+, JDK version used by KALM, Gradle wrapper only, Git available).
   - Use cases:
     - CI (GitLab) running builds, tests, and quality gates in the base image.
     - Researchers running experiments/benchmarks in the same image for reproducibility.
2. Decide registry and tagging policy
   - Choose primary registry (likely GitLab Container Registry for the canonical image).
   - Define tag scheme, e.g.:
     - `kalm-env:<semver>` tied to repository release versions.
     - Optional `kalm-env:<semver>-jdk<major>` if JDK variants are ever needed.
     - CI-friendly tags like `kalm-env:main` or `kalm-env:<branch>` for bleeding-edge work.
3. Lock in documentation locations
   - `dev-resources/CONTAINERS_AND_ENVIRONMENTS.md` - minimal, actionable Compose quickstart.
   - `dev-resources/CI_CD.md` - CI usage notes and cross-links.
   - Wiki: `Container-Build-and-Run.md` (detailed usage + variants), `Container-Image.md` (contents/rationale), and design docs (`Design-Reproducibility-CI-CD.md`, design decisions).

---

## Phase 1 – Base Image Definition (Local Only)

**Outcome:** A local-only Dockerfile and docs that describe the intended image contents and behavior, without yet wiring it into CI.

1. Create the base image definition
   - Add `Dockerfile.kalm-env` (name is illustrative; choose a final name during implementation) at the repo root or under a dedicated folder (e.g. `containers/`).
   - Ensure the Dockerfile:
     - Uses a Linux base image compatible with PowerShell 7.4+.
     - Installs the JDK version required by KALM.
     - Installs PowerShell 7.4+.
     - Has Git and basic build tools required by Gradle and scripts.
     - Does **not** install standalone Gradle; always uses `./gradlew` from the workspace.
     - Sets a sensible working directory (e.g. `/workspace`) but does **not** bake the source tree into the image yet (we will mount or copy later per use case).
2. Define standard entrypoints and conventions
   - Decide whether the image will:
     - Use PowerShell as the default shell/entrypoint, or
     - Keep `/bin/sh` as the default and document how to run `pwsh` explicitly.
   - Make sure environment variables for JDK and locale are correctly set.
3. Document Phase 1 usage (local, experimental)
   - In `dev-resources/CONTAINERS_AND_ENVIRONMENTS.md`:
     - Provide the **actionable** Compose quickstart (build image, start container, run Gradle/Pester).
     - Link to the wiki for deeper rationale and non-Compose variants.
     - Clarify that at this stage, CI is **not** yet using the image; this is a maintainer/researcher preview.

---

## Phase 2 – Integrate with Local Workflows (Opt-In)

**Outcome:** Maintainers can opt-in to using the image for local builds/tests; no CI changes yet.

1. Add helper documentation for local usage
   - Extend the wiki (`Container-Build-and-Run.md`) with examples and advanced scenarios (Buildx, `docker run`, permissions, caching), keeping `dev-resources/CONTAINERS_AND_ENVIRONMENTS.md` minimal.
   - Add a small section to `scripts/README.md`:
     - Note that all scripts (`Invoke-GradleWithJdk.ps1`, `Invoke-PesterWithConfig.ps1`, Git sync scripts, etc.) can be used inside the container as long as the repo is mounted and Git is available.
2. Validate parity with native runs
   - Manually run:
     - The primary Gradle verification task (e.g. `verifyAll`) inside the container.
     - The Pester suite via `Invoke-PesterWithConfig.ps1` inside the container.
   - Compare outcomes against native runs:
     - Ensure no extra dependency-locking changes are triggered.
     - Check for differences in path handling, line endings, or locale-sensitive behavior.
   - Record any issues and resolve or document them before proceeding to CI integration.
3. Decide on any adjustments to scripts for container-friendliness
   - If scripts assume specific drive letters/paths on Windows, document container-friendly alternatives or adjust scripts in a backward-compatible way.
   - Ensure no script attempts interactive prompts or GUI operations when running inside the container.

---

## Phase 3 – CI Adoption (Partial)

**Outcome:** Selected CI jobs (e.g. a dedicated pipeline or a subset of stages) run inside the base image, while the rest of the pipeline remains unchanged.

1. Add CI configuration for image usage
   - Update the GitLab CI configuration (without breaking existing pipelines) to:
     - Define the base image as a CI image for a dedicated job or stage (for example, a new job that runs `./gradlew verifyAll`).
     - Keep the original jobs intact so you can compare behavior.
   - Ensure CI has access to the registry where the image is stored, or build the image within the pipeline during this phase if publishing is not yet configured.
2. Monitor and compare
   - Run a few pipeline executions with both:
     - Existing jobs on the current runners/images.
     - New jobs using the KALM base image.
   - Compare:
     - Test outcomes and timings.
     - Any environment-specific issues (file permissions, locale, time zones).
   - Adjust the Dockerfile or scripts as needed based on these observations.
3. Update CI documentation
   - In `dev-resources/CI_CD.md`:
     - Document which jobs are now using the base image and why.
     - Explain how to add new jobs or stages that run inside the image.
   - Cross-link to `dev-resources/CONTAINERS_AND_ENVIRONMENTS.md` for image details.

---

## Phase 4 – CI Adoption (Default)

**Outcome:** The base image becomes the default environment for all critical CI jobs; non-containerized jobs are kept only where necessary.

1. Migrate core jobs
   - Switch main CI stages (build, test, quality gates) to use the base image by default.
   - Ensure that any job-specific dependencies (e.g. additional tools for coverage reporting) are either:
     - Added to the base image in a controlled way, or
     - Installed per-job in a light-weight step.
2. Retire redundant environments
   - Remove or downgrade legacy jobs that no longer provide additional value once the image-based jobs are stable.
   - Keep minimal non-containerized jobs only if required by the CI provider or for special cases.
3. Capture CI + image versioning
   - Update documentation in `dev-resources/CI_CD.md` and the wiki to:
     - Describe how image tags align with KALM releases and CI configuration.
     - Specify where to update the image tag when changing CI to use a new base image version.

---

## Phase 5 – Research & Reproducibility Workflows

**Outcome:** Researchers have a clear, documented way to use the KALM image as part of reproducibility bundles.

1. Define experiment workflow patterns
   - In `dev-resources/CONTAINERS_AND_ENVIRONMENTS.md` and the wiki:
     - Describe how to run KALM-enabled experiments inside the container.
     - Recommend recording: image tag (or digest), Git commit hash, KALM version, and experiment configuration as part of reproducibility metadata.
2. Provide a concise maintainer/researcher quick-start
   - Add a “Quick start for experiments in containers” section that, at a high level, explains:
     - How to obtain the correct image for a release.
     - How to start a container with the project mounted and run PowerShell/Gradle workflows.
     - What metadata to capture so others can reproduce the same environment later.
3. Align with design docs
   - Update `wiki/Design-Reproducibility-CI-CD.md` and relevant design decision documents to:
     - Reflect that the base Docker/OCI image is now the reference environment for CI and recommended for published scientific investigations.
     - Note remaining open questions (e.g. future support for HPC-focused runtimes like Apptainer) as potential future work, not Phase 1 requirements.

---

## Phase 6 – Optional Enhancements (Future)

These steps are not required for initial Dockerization but may be useful later.

1. Devcontainer / IDE integration
   - Provide a devcontainer configuration (or similar) that reuses the same base image for contributors who prefer container-based development in VS Code or other IDEs.
2. Specialized images
   - Consider creating additional images for:
     - Benchmarking suites or example experiments.
     - Alternative JDK versions, if the project’s compatibility story expands.
3. HPC integration
   - Evaluate whether the OCI images should be repackaged for HPC environments (e.g. Apptainer) and document this as a separate, opt-in workflow.
