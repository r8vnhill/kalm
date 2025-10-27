# Synchronizing GitLab and GitHub Mirrors

This guide explains how to keep the primary GitLab repository and the GitHub mirror in sync using the provided PowerShell helper script.

## Overview

The project uses **GitLab as the primary repository** and **GitHub as a mirror**. The `origin` remote is configured with:
- Fetch URL: GitLab (primary source of truth)
- Push URLs: Both GitLab and GitHub (simultaneous push to keep mirrors in sync)

## Quick Sync

To synchronize your local branch with both remotes:

```powershell
.\scripts\Sync-Remotes.ps1
```

This will:
1. Fetch updates from all remotes
2. Pull/merge changes from the tracking branch
3. Push the current branch to all configured push URLs (GitLab + GitHub)

## Usage Examples

### Sync current branch (most common)
```powershell
.\scripts\Sync-Remotes.ps1
```

### Sync a specific branch
```powershell
.\scripts\Sync-Remotes.ps1 -Branch main
```

### Preview changes without executing (dry run)
```powershell
.\scripts\Sync-Remotes.ps1 -DryRun
```

### Sync with verbose output
```powershell
.\scripts\Sync-Remotes.ps1 -Verbose
```

## Remote Configuration

Current `origin` remote setup:
```
origin  git@github.com:r8vnhill/kalm.git (fetch)
origin  git@github.com:r8vnhill/kalm.git (push)
origin  git@gitlab.com:r8vnhill/kalm.git (push)
```

To verify your remote configuration:
```powershell
git remote -v
```

## Manual Sync (Alternative)

If you prefer manual control:

```powershell
# 1. Fetch from all remotes
git fetch --all --prune

# 2. Pull changes (fast-forward if possible)
git pull origin <branch>

# 3. Push to all remotes
git push origin <branch>
```

The `origin` remote is configured to push to both GitLab and GitHub simultaneously.

## Troubleshooting

### Diverged branches
If local and remote branches have diverged, the script will attempt a merge. If conflicts occur:
```powershell
# Resolve conflicts manually
git status
# Edit conflicting files
git add <resolved-files>
git commit
# Re-run sync
.\scripts\Sync-Remotes.ps1
```

### Uncommitted changes
The script requires a clean working tree. If you have uncommitted changes:
```powershell
# Option 1: Commit changes
git add --all
git commit -m "Your commit message"

# Option 2: Stash changes temporarily
git stash
.\scripts\Sync-Remotes.ps1
git stash pop
```

### Authentication issues
Ensure SSH keys are configured for both GitLab and GitHub:
- [GitLab SSH Keys](https://docs.gitlab.com/ee/user/ssh.html)
- [GitHub SSH Keys](https://docs.github.com/en/authentication/connecting-to-github-with-ssh)

## Best Practices

1. **Always sync before starting new work**
   ```powershell
   .\scripts\Sync-Remotes.ps1
   ```

2. **Sync after completing a feature**
   ```powershell
   git add --all
   git commit -m "feat: your feature description"
   .\scripts\Sync-Remotes.ps1
   ```

3. **Use dry-run to preview changes**
   ```powershell
   .\scripts\Sync-Remotes.ps1 -DryRun
   ```

4. **Verify sync status**
   ```powershell
   git status
   git log --oneline --graph --all -10
   ```

## CI/CD Considerations

When pushing to both remotes, ensure:
- CI pipelines are configured identically on GitLab and GitHub (if applicable)
- Branch protection rules match across both platforms
- Webhooks and integrations are properly configured

## See Also

- [Dependency Update Guide](DEPENDENCY_UPDATE.md) for updating project dependencies
- [Git Standard](GIT_STANDARD.md) for commit message conventions
- [CI/CD Guide](CI_CD.md) for continuous integration workflows
