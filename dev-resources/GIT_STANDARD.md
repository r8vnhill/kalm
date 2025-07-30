# Git Command Usage Standard

> [!tip]
> We **_strongly_** recommend using **PowerShell** (even on macOS or Linux) to ensure full compatibility with the Git utility wrappers provided in this project.
>
> - [Install PowerShell on Linux](https://learn.microsoft.com/en-us/powershell/scripting/install/installing-powershell-on-linux)
> - [Install PowerShell on macOS](https://learn.microsoft.com/en-us/powershell/scripting/install/installing-powershell-on-macos)
> - [Install PowerShell on ARM](https://learn.microsoft.com/en-us/powershell/scripting/install/powershell-on-arm)

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

To ensure **consistency, maintainability, and portability** across environments and CI/CD pipelines, this project provides Git utilities as **PowerShell wrapper functions**. These wrappers encapsulate common patterns and validation logic that enhance developer experience and reduce errors.

### 🤔 Why Wrappers?

These wrappers abstract away common logic such as:

- Branch existence checks (local/remote)
- Remote tracking and fetch behavior
- Error messaging and logging
- Argument parsing and validation

This reduces friction for new contributors and helps teams work with Git more safely and consistently.

> [!warning]
> Avoid using raw `git` commands unless strictly necessary. Instead, use the standardized wrappers described below.


## ⚙️ Enabling Git Utilities

Before using the wrapped Git commands, import the utility module into your session:

```powershell
.\scripts\EnableGitUtilities.ps1
```

This script loads the `Keen.Git` module and makes all Git-related functions available.

> [!note]
> If you're running PowerShell for the first time or on macOS/Linux, you may need to allow script execution:
>
> ```powershell
> Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
> ```

Or follow your organization’s security policies.


## 🧰 Available Git Commands

After running `EnableGitUtilities.ps1`, all commands from the `Keen.Git` module become available. To see the full list:

```powershell
Get-Command -Module Keen.Git
```

To verify the module is loaded:

```powershell
Get-Module Keen.Git
```

Each command is designed to:

- Validate input and handle edge cases
- Improve output and error messaging
- Provide consistent behavior across environments


## 📚 Getting Help

Each command is fully documented. You can view command help like this:

```powershell
Get-Help <Command> -Detailed
```

For example:

```powershell
Get-Help Git-Checkout -Detailed
```

This will show:

- A description of the command
- Parameters and options
- Exit codes and examples

Alternate help views:

```powershell
Get-Help Git-Checkout -Examples
Get-Help Git-Checkout -Full
```


## 🚀 Example Usage

```powershell
# Enable the utilities
.\scripts\EnableGitUtilities.ps1

# Check out a branch (local or remote)
Git-Checkout iss2/ci-cd
```

This example will automatically:

- Check if the branch exists locally
- Fetch remotes if needed
- Track the branch if it's remote-only


## 🛑 Disabling Git Utilities

To unload the Git utility functions from your session:

```powershell
.\scripts\DisableGitUtilities.ps1
```

This removes all `Keen.Git` commands from your current shell to prevent conflicts with global or system modules.


## 🔧 When to Use Raw `git` Commands

Use raw `git` commands only when needed, such as:

- Performing advanced operations (`git rebase`, `git bisect`)
- Using scripts in environments that don’t support PowerShell
- Debugging Git internals or experimenting with custom aliases

In all other cases, prefer the standardized PowerShell wrappers.


## 👩‍💻 Contributing Additional Shell Support

We welcome community contributions for Bash, Zsh, or other shells. To contribute:

- Place your shell-specific functions in `scripts/git/<shell>/`, e.g., `scripts/git/bash/`
- Use consistent naming conventions (e.g., `git_checkout.sh` for `Git-Checkout`)
- Follow the same documentation and logging structure as the PowerShell wrappers
- Clearly indicate any limitations or behavioral differences

> [!important]
> Only PowerShell wrappers are **officially maintained** by this project. Shell-based alternatives will be reviewed and accepted as **best-effort community support**.


## 📌 Notes

- Wrappers are particularly valuable in CI pipelines, Git hooks, and shared workflows
- You can customize or extend the wrappers by modifying the `scripts/git` folder
- Contributions are welcome! Please open a pull request and follow the code style of the existing utilities
