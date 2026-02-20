# Agent Working Rules

This file defines repository-specific guardrails for coding agents working in KALM.

## Mission

- Prioritize deterministic, reproducible engineering workflows.
- Keep build logic and automation maintainable.
- Prefer small, test-backed changes.

## Core Principles

### 1) TDD by default

For behavior changes:

1. Add/adjust a failing test.
2. Implement the change.
3. Run relevant tests and keep them green.

### 2) Deterministic Gradle

- Keep Gradle tasks non-interactive and deterministic.
- Avoid user-workflow branching via ad hoc Gradle parameters.
- Use Gradle for orchestration/wiring; move UX/argument-heavy flows to CLI tools.

### 3) CLI + Script split

When a workflow needs runtime input or rich args:

- implement logic in `tools/` (Kotlin CLI)
- expose via `scripts/` (PowerShell wrapper)

This is the preferred architecture for lockfile and quality tooling.

## Repository Structure (relevant to agents)

- `tools/`: Kotlin/JVM CLI tools and shared CLI helpers.
- `scripts/`: PowerShell wrappers and automation (entrypoints for contributors/CI).
- `build-logic/`: Gradle convention plugins (`kalm.*`).
- `wiki/`: Primary long-form documentation.
- `dev-resources/`: concise operational references; avoid deep duplication with wiki.

## Language/Tooling Standards

### Kotlin / Gradle

- Use Kotlin DSL idioms; keep root/module build scripts as wiring where possible.
- Prefer extracting reusable logic to helper functions or convention plugins.
- Keep module dependencies intentional (`implementation` vs `api` based on public surface).
- Preserve dependency locking constraints when adding/upgrading deps.

### PowerShell

- Target PowerShell 7.4+.
- Use strict mode and analyzer-friendly patterns.
- Do not use `Write-Host` (use information/verbose/output streams).
- Reuse shared helpers from `scripts/lib` rather than duplicating functions.

## Testing & Verification

Run the smallest relevant scope first, then broaden:

- Targeted Kotlin tests:
    - `./gradlew :tools:test --tests "fully.qualified.TestName"`
- Full tools tests:
    - `./gradlew :tools:test`
- Repository-level verification:
    - `./gradlew verifyAll`
- PowerShell tests:
    - `./scripts/testing/Invoke-PesterWithConfig.ps1`

If a command cannot be executed due to environment constraints, this must be reported clearly.

## Dependency Locking Rules

- Lockfiles are source-controlled and must remain consistent with dependency changes.
- When dependencies change, regenerate lockfiles and commit them together.
- Prefer existing lock workflows (`Invoke-LocksCli.ps1` / `:tools` locks CLI) over ad-hoc commands.

## Documentation Rules

- Prefer wiki as the source of detailed guidance.
- Keep `dev-resources/` brief and operational, linking to wiki for deep explanations.
- When changing workflows, update docs in the same change set.

## Change Hygiene

- Keep edits minimal and local to the request.
- Avoid broad refactors unless they directly improve requested behavior.
- Do not silently alter user-facing behavior, CLI contracts, or error messages without tests.
- If an unrelated risky drift is identified, it must be explicitly reported before expanding the scope.
