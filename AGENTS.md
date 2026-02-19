# Agent Working Rules

This file defines repository-specific guardrails for automated agents.

## Gradle Task Design

- Keep Gradle tasks deterministic and non-interactive.
- Avoid parameter-driven Gradle task behavior for user workflows (for example `-Pfoo=...` to drive branching logic in tasks).
- If a workflow needs runtime user input, implement it as:
  - a dedicated CLI in `tools/`
  - a PowerShell wrapper in `scripts/` that calls that CLI
- Use Gradle tasks for stable wiring/orchestration, not interactive UX.

## TDD Default

- Follow TDD for code changes by default:
  1. add/adjust a failing test
  2. implement the behavior
  3. run tests and keep the suite green

