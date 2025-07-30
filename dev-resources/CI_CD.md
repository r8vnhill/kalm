# CI/CD and Release Guide

## Table of Contents

- [CI/CD and Release Guide](#cicd-and-release-guide)
  - [Table of Contents](#table-of-contents)
  - [Versioning and Tagging Strategy](#versioning-and-tagging-strategy)
    - [🔖 Centralized Version Management](#-centralized-version-management)
    - [🧭 Semantic Versioning and Git Tagging](#-semantic-versioning-and-git-tagging)
    - [✅ Accepted Version Formats](#-accepted-version-formats)
  - [📝 Manually Drafting a Release](#-manually-drafting-a-release)
    - [🧪 Before Drafting](#-before-drafting)
    - [✍️ How to Create a Release Draft](#️-how-to-create-a-release-draft)
      - [In GitLab (🔐 Primary Source of Truth)](#in-gitlab--primary-source-of-truth)
      - [In GitHub (🌐 Public Mirror at `r8vnhill/keen-op`)](#in-github--public-mirror-at-r8vnhillkeen-op)
    - [🧾 Changelog Guidelines](#-changelog-guidelines)
    - [🏷️ Tagging the Release](#️-tagging-the-release)

## Versioning and Tagging Strategy

### 🔖 Centralized Version Management

All project modules share a single version, defined in:

```kotlin
build-logic/src/main/kotlin/Versions.kt
```

To apply centralized versioning, subprojects must apply the base convention plugin:

```kotlin
plugins {
    id("keen.base")
}
```

For example, the `keen.reproducible` plugin automatically inherits the version through:

```kotlin
plugins {
    id("keen.base")
}
```

### 🧭 Semantic Versioning and Git Tagging

This project follows [Semantic Versioning](https://semver.org) (`MAJOR.MINOR.PATCH`) to convey changes and manage compatibility.

- Each release **must** be identified by a **Git tag** (e.g., `v1.2.3`) that matches the version declared in `Versions.kt`.
- Tags provide traceability and can be used to trigger release pipelines in GitLab CI/CD (or GitHub Actions, if used as a mirror).

### ✅ Accepted Version Formats

The following suffixes are allowed for pre-release versions:

- `-SNAPSHOT`
- `-ALPHA`
- `-BETA`
- `-RC.n` (e.g., `-RC.1`, `-RC.2`)

> [!tip]
> Valid examples:
> - `1.0.0-SNAPSHOT`
> - `1.2.3-BETA`
> - `4.2.0-RC.3`

> [!caution]
> Invalid formats:
> - `1.0.0-MILESTONE.1`
> - `4.2.0-RC-3` (must use dot, not dash)

Please ensure all tags follow the expected format to maintain consistency and compatibility with CI/CD tooling.

## 📝 Manually Drafting a Release

To ensure traceability and consistency across GitLab and GitHub, releases are drafted **manually** using standardized steps.

> [!important]
> The **canonical source of truth** is the GitLab repository. GitHub is a **read-only mirror** used for visibility and discoverability.

> [!note]
> The canonical changelog is maintained in [`CHANGELOG.md`](../CHANGELOG.md) at the project root. Keep an `[Unreleased]` section at the top to stage upcoming changes.

### 🧪 Before Drafting

1. Ensure that all pull/merge requests for the milestone have been merged.
2. Confirm that `Versions.kt` has been updated to the intended version (e.g., `1.2.3`).
3. Validate that CI pipelines pass on the **default branch** (`main` unless otherwise configured).

### ✍️ How to Create a Release Draft

#### In GitLab (🔐 Primary Source of Truth)

1. Navigate to **Repository → Tags**.
2. Click **New Release**.
3. Select the appropriate version tag (e.g., `v1.2.3`), or create it if it does not yet exist.
4. Use a clear and consistent **release title**, such as `v1.2.3 – 2025-07-23`.
5. Populate the **changelog** body using [Keep a Changelog](https://keepachangelog.com) conventions.

    **Suggested changelog structure:**

    ```md
    ## [1.2.3] – 2025-07-23
    ### Added
    - Support for parallel test execution (#42)

    ### Fixed
    - Correct version mismatch in reproducible plugin (#45)

    ### Changed
    - Updated CI templates for reproducibility

    ### Removed
    - Dropped deprecated snapshot logic from core module

    ### Security
    - No changes
    ```

6. Optionally link related merge requests or issues.
7. Click **Save Release**.

#### In GitHub (🌐 Public Mirror at [`r8vnhill/keen-op`](https://github.com/r8vnhill/keen-op))

If desired, you may also publish a matching release in the GitHub mirror:

1. Navigate to **Releases** in the GitHub UI.
2. Click **Draft a new release**.
3. Use the same tag and changelog content as the GitLab release.
4. Publish the release.

> [!important]
> **GitHub hosts a read-only mirror** of the project. Official development, issue tracking, and versioning decisions occur in **GitLab**.

> [!note]
> If you publish a GitHub release manually, ensure the corresponding Git tag is pushed to GitHub:
>
> ```bash
> git push github v1.2.3
> ```

### 🧾 Changelog Guidelines

- Prefer **past tense verbs** for entries ("Added", "Fixed", etc.).
- Group changes under the appropriate headings:
  - `Added`
  - `Changed`
  - `Fixed`
  - `Deprecated`
  - `Removed`
  - `Security`
- Keep each bullet point **concise and actionable**.
- Reference issues or merge requests using `#ID` where applicable.
- Avoid vague catch-all entries like “Misc fixes”.

> [!tip]
> Clear, meaningful changelogs make it easier to review, understand, and communicate the scope of each release.

### 🏷️ Tagging the Release

If you didn’t create the tag from the GitLab UI, you can tag manually:

```bash
git tag v1.2.3
git push origin v1.2.3
```

> [!important]
> If using GitHub for releases, you must also push the tag to GitHub:
>
> ```bash
> git push github v1.2.3
> ```

CI/CD will trigger automatically if pipelines are configured to respond to tag events.

> [!warning]
> Avoid using GitHub-only automation (like Release Drafter) unless fully synchronized with GitLab. This project relies on **manual changelog curation** to ensure consistency across platforms.
