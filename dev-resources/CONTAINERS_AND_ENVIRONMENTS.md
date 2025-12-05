# KALM Containers and Environments

**Audience:** KALM maintainers and contributors. This is NOT for library consumers; use the main README for consumer guidance.

## Overview

KALM provides an OCI/Docker-based environment image (`kalm-env`) to improve **reproducibility** of builds, tests, and scientific experiments. The image ensures that:

- All builds and tests run on the same OS (Linux, Ubuntu 22.04 LTS)
- Java (JDK 21), PowerShell (7.4+), and Git versions are locked and consistent
- Environment variables (locale, timezone, etc.) are standardized
- Researchers can reproduce the exact same computational environment as CI

This is a **maintainer-facing feature**. The main README remains consumer-focused.

---

## Base Image Contents

The `kalm-env` image includes:

| Component   | Version           | Purpose                                                      |
| ----------- | ----------------- | ------------------------------------------------------------ |
| OS Base     | Ubuntu 22.04 LTS  | Stable, well-supported Linux distribution                    |
| OpenJDK     | 22 LTS            | Matches build-logic DEFAULT_JAVA_VERSION; modern Kotlin/Gradle |
| PowerShell  | 7.4+              | Cross-platform automation scripting                          |
| Gradle      | (wrapper only)    | Always uses `./gradlew` from the repo; no standalone install |
| Git         | 2.20+             | Version control, submodule management                        |
| Build tools | `build-essential` | C/C++ compiler, `make`, etc., for any native dependencies    |
| Locale      | en_US.UTF-8       | UTF-8 encoding for reproducible text processing              |

**Important:** The image does **not** contain the KALM source code. You mount or copy the repo at runtime.

---

## Building the Image Locally

### Prerequisites

- Docker or Podman installed and running
- Clone of the KALM repository (to access the Dockerfile)

### Build Command

```bash
# /path/to/kalm
# Use the simpler 'latest' tag for local work
docker build -t kalm-env:latest -f Dockerfile .
# Or build with a tag for a specific version
docker build -t kalm-env:0.1.0 -f Dockerfile .
```

**Build time:** ~5–10 minutes on first build (downloads base OS, PowerShell, JDK). Subsequent builds are faster due to layer caching.

---

## Running the Image Locally

### Basic Checks

**Note:** The image's default entrypoint is PowerShell. To run shell commands like `java`, use `--entrypoint /bin/bash`:

```bash
# Check Java installation
docker run --rm --entrypoint /bin/bash kalm-env:latest -c "java -version"

# Check PowerShell version (no --entrypoint needed; it's the default)
docker run --rm kalm-env:latest -Command '$PSVersionTable'
```

### Basic Usage: Interactive PowerShell

```bash
# /path/to/kalm
# Start a container with the repo mounted, run pwsh interactively
docker run --rm -it -v .:/workspace kalm-env:latest

# Now inside the container, you can use all repo scripts:
PS> ./gradlew tasks
PS> .\scripts\testing\Invoke-PesterWithConfig.ps1
```

**Explanation:**
- `--rm`: Remove the container after exit (keeps your system clean).
- `-it`: Interactive terminal (required for `pwsh` prompt).
- `-v .:/workspace`: Bind-mount the repo from your host to `/workspace` inside the container.
- The default entrypoint is `pwsh`, so you get a PowerShell prompt automatically.

### Run a Single Command Inside the Container

```bash
# Run a Gradle task without an interactive session
# Using PowerShell (default entrypoint)
docker run --rm -v /path/to/kalm:/workspace kalm-env:latest \
  -Command "cd /workspace; ./gradlew verifyAll"

# Run Pester tests (PowerShell)
docker run --rm -v /path/to/kalm:/workspace kalm-env:latest \
  -Command ". /workspace/scripts/testing/Invoke-PesterWithConfig.ps1"

# Alternative: Use bash with -w flag to set working directory
docker run --rm -v /path/to/kalm:/workspace -w /workspace kalm-env:latest \
  --entrypoint /bin/bash -c "./gradlew verifyAll"
```

**Note:** When using `--entrypoint /bin/bash`, you must either:
- Use `-w /workspace` to set the working directory, OR
- Include `cd /workspace &&` in the command string.

### Using Docker Compose (Recommended for Development)

A `docker-compose.yml` file is provided at the repo root for convenience. It eliminates the need for long `docker run` commands:

```bash
# Interactive PowerShell session (recommended)
docker-compose run --rm kalm

# Inside the container:
$ ./gradlew verifyAll
$ .\scripts\testing\Invoke-PesterWithConfig.ps1
```

**Convenience services:**

```bash
# Run Gradle tasks via docker-compose (shorter syntax)
docker-compose run --rm gradle verifyAll
docker-compose run --rm gradle :core:test

# Run Pester tests via docker-compose
docker-compose run --rm pester
```

**First time setup:**
```bash
# Build the image (automatic on first run, but you can build explicitly)
docker-compose build
```

### Preserve File Permissions (for Git Operations)

If you commit or modify files from inside the container and want the host to own them correctly on Linux:

```bash
# Run as the current host user (on Linux; macOS similar)
docker-compose run --rm \
  --user $(id -u):$(id -g) \
  kalm
```

---

## Running with Podman

If you prefer Podman (a drop-in Docker alternative):

```bash
# Build and run with Podman instead of Docker
podman build -t kalm-env:latest -f Dockerfile .
podman run --rm -it -v /path/to/kalm:/workspace localhost/kalm-env:latest
```

All commands are identical except `docker` → `podman`.

---

## CI Integration (Phase 2+)

When the image is integrated into CI pipelines (e.g., GitLab CI), jobs will specify:

```yaml
# GitLab CI example (Phase 3+)
image: registry.gitlab.com/r8vnhill/kalm/kalm-env:0.1.0

build:
  script:
    - ./gradlew verifyAll
```

Documentation for CI usage will live in `dev-resources/CI_CD.md` once Phase 3 is underway.

---

## Troubleshooting

### "Docker daemon is not running"

Ensure Docker (or Podman) is started on your system.

```bash
# macOS / Linux
docker ps  # Should list containers (or show no containers if empty)
```

### "Cannot mount /path/to/kalm: No such file or directory"

Use the full absolute path to your KALM repo:

```bash
# Wrong: relative path
docker run -v ./kalm:/workspace ...

# Correct: absolute path
docker run -v /Users/yourname/Projects/kalm:/workspace ...
```

### "java: command not found" inside container

Verify the image was built successfully:

```bash
docker images | grep kalm-env
```

If not present, rebuild:

```bash
docker build -t kalm-env:latest -f Dockerfile .
```

### "Permission denied" when writing files from container

On Linux, container processes run as the `builder` user (non-root). Use the `--user` flag to run as your host user (see "Preserve File Permissions" above).

---

## For Researchers: Reproducibility Bundles

When publishing a KALM-based experiment, include:

1. **Image tag or digest:**
   ```
   kalm-env:0.1.0 (or the full digest: sha256:abc123...)
   ```

2. **KALM Git commit:**
   ```
   commit abc123def456... on branch iss6/docker
   ```

3. **Experiment command:**
   ```
   docker run --rm -v /path/to/experiment:/experiment \
     kalm-env:0.1.0 \
     ./gradlew :runExperiment
   ```

This ensures others can reproduce your exact computational environment.

---

## Design Rationale

See the wiki page **"Design-Reproducibility-CI-CD.md"** for the full rationale behind containerization in KALM and how it aligns with reproducibility goals for scientific investigations.

---

## Next Steps

- **Phase 1 (current):** Local-only Dockerfile and docs (this file).
- **Phase 2:** Local workflows, validation against native runs.
- **Phase 3:** Partial CI integration (dedicated jobs).
- **Phase 4:** Full CI migration.
- **Phase 5:** Research reproducibility workflows.

See `dev-resources/PLAN-dockerization.md` for the full roadmap.
