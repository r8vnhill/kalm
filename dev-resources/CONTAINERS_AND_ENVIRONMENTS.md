# KALM Containers and Environments

> [!IMPORTANT] Audience
> KALM maintainers and contributors.
> This is NOT for library consumers; use the main README for consumer guidance.

> [!NOTE] Detailed docs live in the wiki
> - [Container: Build and Run](https://gitlab.com/r8vnhill/kalm/-/wikis/Container-Build-and-Run) (recommended Compose workflow + Buildx/docker run variants)
> - [Container Image Overview](https://gitlab.com/r8vnhill/kalm/-/wikis/Container-Image) (contents, defaults, rationale)
> - [Dockerfile Linting with Hadolint](https://gitlab.com/r8vnhill/kalm/-/wikis/Dockerfile-Linting-Hadolint)

---

## Recommended workflow: Docker Compose

KALM ships a `docker-compose.yml` at the repo root. For most contributors, this is the simplest way to
build the image and run tasks in a consistent environment.

### Prerequisites

- Docker installed and running
- Docker Compose v2 available (`docker compose version`)

### Quickstart

```bash
# From repo root (builds image `kalm-env:local` as defined in docker-compose.yml)
docker compose build

# Open an interactive PowerShell session in the container with the repo mounted at /workspace
docker compose run --rm kalm
```

Inside the container:

```powershell
./gradlew verifyAll
```

### One-off commands (no interactive shell)

Use the convenience services:

```bash
# Gradle (uses a persistent cache volume)
docker compose --profile gradle run --rm gradle verifyAll

# Hadolint Kotlin script
docker compose --profile hadolint run --rm hadolint-kts
# Example with options
docker compose --profile hadolint run --rm hadolint-kts --failure-threshold error --dockerfile Dockerfile
```

For the `gradle` service, Compose appends the supplied task names to the service entrypoint, so
`docker compose --profile gradle run --rm gradle verifyAll` runs `./gradlew verifyAll` inside the
container.

Containerized Pester support has been retired. The supported container workflows are the Gradle and
Hadolint services; any remaining PowerShell test references should be treated as legacy/local-only
until they are fully removed.

### Linux file ownership note

If you create files inside the container and want them owned by your host user:

```bash
docker compose run --rm --user "$(id -u):$(id -g)" kalm
```
