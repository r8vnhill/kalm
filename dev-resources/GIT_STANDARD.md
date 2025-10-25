# Git Command Usage Standard

> [!tip]
> Use whichever shell you prefer (sh, bash, zsh, PowerShell). This document shows portable git examples that work across platforms.

## 📖 Table of Contents

- [Git Command Usage Standard](#git-command-usage-standard)
  - [📖 Table of Contents](#-table-of-contents)
  - [💼 Overview](#-overview)
    - [🤔 Why Wrappers?](#-why-wrappers)
  - [⚙️ Enabling Git Utilities](#️-enabling-git-utilities)
  - [🧰 Available Git Commands](#-available-git-commands)
  - [📚 Getting Help](#-getting-help)
  - [🚀 Example Usage](#-example-usage)
  - [🛑 Disabling Git Utilities](#-disabling-git-utilities)
  - [🔧 When to Use Raw `git` Commands](#-when-to-use-raw-git-commands)
  - [👩‍💻 Contributing Additional Shell Support](#-contributing-additional-shell-support)
  - [📌 Notes](#-notes)


## 💼 Overview

This document explains recommended git workflows for contributors and CI. It focuses on direct, portable `git` commands and small reproducible patterns you can run in any shell. The examples here are shell-agnostic and should work on Windows, macOS, and Linux.


## ⚙️ Recommended git workflows

We prefer using standard `git` commands directly. Below are small, repeatable patterns that are portable and suitable for both local use and CI.

- Fetch and prune remotes:

```sh
git fetch --all --prune
```

- Create a tracking branch if a remote-only branch exists:

```sh
# if origin/feature/xyz exists, create a local tracking branch
git checkout -b feature/xyz origin/feature/xyz 2>/dev/null || git checkout feature/xyz
```

- Push a branch and set upstream:

```sh
git push --set-upstream origin feature/xyz
```

If you find yourself repeating complex sequences often, add small shell helper scripts under `scripts/git/<shell>/` and document them in this file.


## 🧰 Available Git Commands

Run raw `git` commands directly. Example verification:

```sh
git --version
git status
```

Each command is designed to:

- Validate input and handle edge cases
- Improve output and error messaging
- Provide consistent behavior across environments


## 📚 Getting Help

Use the standard git help and man pages:

```sh
git help <command>
# or
man git
```

For quick usage details:

```sh
git <command> --help
```


## 🚀 Example Usage

Use the usual Git commands directly. Example:

```sh
# Fetch remote updates
git fetch --all --prune

# Check out a branch (create local tracking branch if needed)
git checkout -b feature/xyz origin/feature/xyz 2>/dev/null || git checkout feature/xyz

# Push branch
git push --set-upstream origin feature/xyz
```


## 🛑 Note on unloading helpers

There are no included shell helpers to unload. If you created your own helper module in the `scripts/git/` folder, remove or unload it according to your shell's conventions.


## 🔧 When to Use Raw `git` Commands

Use raw `git` commands for advanced operations or when scripting in environments that do not provide helper scripts. Examples:

- `git rebase`, `git bisect`
- Small, self-contained scripts for CI or local automation

Use standard `git` commands or small, well-documented helpers you maintain locally.


## 👩‍💻 Contributing Additional Shell Support

We welcome community contributions for Bash, Zsh, or other shells. To contribute:

- Place your shell-specific functions in `scripts/git/<shell>/`, e.g., `scripts/git/bash/`
- Use consistent naming conventions (e.g., `git_checkout.sh`)
- Document behavior, supported shells, and any limitations in this file

If you contribute shell helpers, open a PR and document usage and supported shells.


## 📌 Notes

- Wrappers are particularly valuable in CI pipelines, Git hooks, and shared workflows
- You can customize or extend the wrappers by modifying the `scripts/git` folder
- Contributions are welcome! Please open a pull request and follow the code style of the existing utilities
