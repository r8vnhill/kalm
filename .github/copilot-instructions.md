# AI Assistant Guide for this Repo (KALM)

Purpose: give agents the minimum, precise context to work productively here. Keep answers concrete and aligned with how this project is structured and built.

## Hard rules (must follow)
1) Never stage, commit, push, or open PRs unless the user explicitly asks in a message.
2) Explain intended multi-file changes before applying them; keep diffs minimal and scoped.
3) Preserve existing style and public API unless a change is requested or required by tests/fixes.

# AI assistant instructions for this repository (KALM)

Purpose: give an AI agent exactly the repo-level facts and commands needed to be productive. Be concrete, conservative, and cite files.

## Hard rules (must follow)
- Never stage, commit, push, or open PRs unless the user explicitly asks.
- Explain multi-file changes before applying them; keep diffs minimal and scoped.
- Preserve public APIs and coding style unless tests or the user request otherwise.

## Quick repo summary (why this matters)
- Multi-module Gradle (Kotlin/JVM) project. Primary modules: `core` (domain + algorithms), `platform` (BOM), and `build-logic` (convention plugins).
- Key example types: `core/src/main/kotlin/cl/ravenhill/kalm/engine/OptimizationEngine.kt`, `core/.../repr/Feature.kt`, `core/.../engine/SimpleHillClimber.kt`.

## Essential commands (what you'll run)
- Full verification (tests + lint + API checks): `./gradlew verifyAll`
- Preflight (verifyAll + dependency helpers): `./gradlew preflight`
- Run a single module's tests: `./gradlew :core:test`
- Run Detekt for `core`: `./gradlew :core:detekt`
- Run Gradle with a specific JDK (preferred scripts): `./scripts/Invoke-GradleWithJdk.ps1` (Windows) or `./scripts/invoke_gradle_with_jdk.sh`

## Project-specific conventions & traps
- Dependency locking is strict (see `dev-resources/DEPENDENCY_LOCKING.md`). Do not regenerate lockfiles unless the user requests and they run `--write-locks` explicitly.
- Use the version catalog at `gradle/libs.versions.toml` and provider-safe accessors; avoid eager resolution in build scripts.
- Detekt config lives at `config/detekt/detekt.yml`. Thresholds are intentionally low — justify any suppression with tests.
- Binary compatibility is enforced: API dumps live under `<module>/api/*.api` and are the source-of-truth for public API changes.

## Integration points & automation
- Convention plugins: `build-logic/src/main/kotlin/*.gradle.kts` (look for `kalm.*` plugins).
- Automation scripts: `scripts/` (use them for Git tasks, syncing wiki, invoking Gradle with a chosen JDK). Prefer those over raw git for user-requested commits.

## When to use PullStrategy (git divergence handling)
Quick examples for choosing a pull strategy when a fast-forward isn't possible:

- ff-only (default): fail fast. Use when you want strict CI-like behavior and require a manual merge/rebase before automated syncs. Example: automated CI or a gated pipeline.

- merge: safe automatic merge. Use when the wiki or a submodule may receive external edits and you prefer auto-merging remote changes into your local branch (creates a merge commit). Example:

```powershell
./scripts/Sync-WikiOnly.ps1 -PullStrategy merge -SkipPush -WikiCommitMessage "chore(wiki): integrate remote"
```

- rebase: linear history. Use when you want to replay local commits on top of remote changes and keep history linear; prefer when local changes are small and you can tolerate rebases. Example:

```powershell
./scripts/Sync-WikiOnly.ps1 -PullStrategy rebase -SkipPush -WikiCommitMessage "chore(wiki): rebase onto remote"
```

Recommendation: keep `ff-only` as the default for automation and opt-in to `merge`/`rebase` interactively when you understand the branch state.

## Minimal contract for agent edits (copy when proposing changes)
- Inputs: precise file paths to edit, reason, and tests or verification to run.
- Outputs: list of files changed, test results, and any new lint/API failures produced.
- Error modes: failing build, detekt violations, API dump mismatch — report and stop.

## Useful file references (examples to cite in edits)
- `core/src/main/kotlin/cl/ravenhill/kalm/engine/OptimizationEngine.kt` — engine contract example
- `build-logic/` — how project-wide conventions are applied
- `dev-resources/AGENT_GUIDELINES.md` — workspace runtime tips (shell behavior)
- `dev-resources/DEPENDENCY_LOCKING.md` — exact lockfile workflows

## Final notes
- Read `dev-resources/AGENT_GUIDELINES.md` at session start (it contains shell-specific hints — e.g., avoid pwsh wrapper when already in pwsh).
- Keep responses terse and actionable. Ask one clarifying question only when truly blocked.

Last updated: 2025-11-08
