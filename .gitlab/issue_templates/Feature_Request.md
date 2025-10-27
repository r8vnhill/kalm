> Start with the information you have today. Update this issue as discovery, experimentation, and design progress.
> It's okay to leave sections blank initially. This template is a guide, not a gate.

## How to use this template
- Start with Quick Pitch and Motivation. Leave the rest blank if unknown; update as you learn.
- Prefer links over walls of text (papers, notebooks, prior issues/MRs).
- Use the Roadmap checkboxes to signal phase and progress.
- Capture acceptance criteria as simple Given/When/Then checks (see tips below).
- Attach diagrams or pseudo-code where helpful; they can be rough.
- Mention ownership and labels so it gets routed correctly.

<details>
<summary>Formatting tips (click to expand)</summary>

Examples for common sections:

- Acceptance criteria (copy and adapt):
  - [ ] Given <state>, when <action>, then <outcome>
  - [ ] Given <precondition>, when <event>, then <observable result>

- Reference list item:
  - [ ] Smith et al., 2020 — https://doi.org/... (why it matters: ...)

- Code/algorithm sketch:
  ```
  # pseudocode
  init model
  for dataset in benchmarks:
      run_experiment(dataset)
      collect(metric)
  ```

- Diagram links: link to a PNG/SVG or a whiteboard tool (e.g., Excalidraw, Mermaid in a MR).

</details>

## Quick Pitch
- Problem or opportunity: `__`
- Desired impact / outcome: `__`
- Primary stakeholders / consumers: `__`

## Motivation & Current Understanding
- Background or links to prior discussions: `__`
- Known constraints, assumptions, or open questions: `__`
- Initial references (optional for now):
  - [ ] Citation 1: `Author et al., Year – DOI/URL`
  - [ ] Citation 2: `__`

## Early Research Notes _(update as you learn more)_
> Capture ideas, papers, experiments, or datasets as you discover them. 
- Relevant literature or benchmarks: `__`
- Prototype sketches / diagrams (optional): `__`
- Potential collaborators or reviewers: `__`

## Proposed Direction
- High-level approach or architecture sketch:
  ```
  ...
  ```
- What success looks like (acceptance criteria): `__`
  - Example: [ ] Given <input>, when <algorithm step>, then <metric>=<target>
- Out-of-scope / deferred ideas: `__`
- Risks or unknowns to investigate: `__`

## Roadmap & Milestones
Use the checkboxes to show progress as the feature matures.
- [ ] **Discovery** – clarify goals, stakeholders, and constraints
  - Questions to answer: `__`
- [ ] **Experimentation** – run prototypes, benchmarks, or literature reviews
  - Planned experiments / datasets: `__`
- [ ] **Design & Planning** – finalise architecture and backlog items
  - Key design decisions: `__`
- [ ] **Implementation** – development tasks and code reviews
  - Milestones or epics: `__`
- [ ] **Validation & Rollout** – tests, documentation, comms
  - Success metrics / release notes to update: `__`

## Collaboration & Dependencies
- External dependencies (datasets, tooling, upstream changes): `__`

## Quick actions (paste into a comment)
Use GitLab quick actions to label, assign, and plan fast:

```
/label ~"feature" ~"research"
/assign @owner_username
/cc @team_or_group
/milestone %Q4-2025
/epic &123      # if epics are enabled
```
