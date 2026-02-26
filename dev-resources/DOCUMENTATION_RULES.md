# Documentation Guidelines

### 1. Markdown Formatting for Code Snippets

When writing code snippets in documentation, **ALWAYS** use proper Markdown syntax. Enclose all code blocks with:

````markdown
```kotlin
// ...
```
````

This ensures consistent syntax highlighting and improves readability.

### 2. Use of `@property` for Public Variables

When documenting classes, **PREFER** using the `@property` tag to describe public variables instead of documenting them inline within the class body.

### 3. Preferred Use of Reference Links

**PREFER** using reference-style links (e.g., `[String]`) over monospace formatting (e.g., `` `String` ``) when referring to types or other significant elements.

### 4. Usage Examples

When providing usage examples in docstring comments, follow this structure:

````kotlin
/**
 * Short description of the function.
 * 
 * Detailed description of the function.
 *
 * ## Usage:
 * Usage details and scenarios.
 *
 * ### Example 1: Description
 * ```kotlin
 * // example 1 code
 * ```
 * ### Example 2: Description
 * ```kotlin
 * // example 2 code
 * ```
 * @tags
 */
fun foo(params) = elements.forEach(action)
````

> [!important]
> **Place examples before the `@tags` section.** Common tags include `@param`, `@return`, `@throws`, etc.

### 5. RedMadRobot Detekt Plugin (Advanced Multi-Module Analysis)

Quick-start users: see the README's "Static Analysis" section for a minimal on-ramp. This section is the authoritative, detailed reference for advanced configuration and multi-module usage.

The **RedMadRobot Detekt Plugin** (`com.redmadrobot.detekt`) extends Detekt with additional tasks and conventions optimized for multi-module projects. This is a GRADLE PLUGIN (not a ruleset) that wraps the core Detekt plugin.

#### Key Features
- **`detektAll`**: Run Detekt across all modules in one task
- **`detektDiff`**: Incremental analysis checking only files changed vs. a base branch (e.g., `main`)
- **`detektFormat`**: Auto-format code across modules
- **`detektBaselineAll`**: Generate baseline files for all modules
- Conventions for shared Detekt configuration across modules
- Supports Android build variants

#### When to Use
- Multi-module projects where you want unified Detekt tasks
- Projects where incremental analysis (`detektDiff`) would speed up CI
- Teams already using RedMadRobot's Gradle infrastructure conventions

#### Configuration

1. **Add to version catalog** (already added):
   ```toml
   # gradle/libs.versions.toml
   [versions]
   detekt-redmadrobot = "0.19.1"
   
   [plugins]
   detekt-redmadrobot = { id = "com.redmadrobot.detekt", version.ref = "detekt-redmadrobot" }
   ```

2. **Apply the KALM convention plugin**:
   ```kotlin
   // In subproject or root build.gradle.kts
    plugins {
        id("kalm.detekt-redmadrobot")
    }
   ```

3. **Optional: Configure the plugin**:
   ```kotlin
   redmadrobot {
       detekt {
           // Check only files changed vs. main branch
           checkOnlyDiffWithBranch("main") {
               fileExtensions = setOf(".kt", ".kts")
           }
       }
   }
   ```

#### Available Tasks (examples)
```bash
# Run Detekt across all modules
./gradlew detektAll

# Check only changed files vs. main
./gradlew detektDiff

# Format all Kotlin files
./gradlew detektFormat

# Generate baselines for all modules
./gradlew detektBaselineAll
```

#### Notes
- The RedMadRobot plugin automatically applies the core Detekt plugin, so DON’T mix it with any other Detekt convention.
- The `detektDiff` task uses Git to detect changed files, which may not support full type-resolution analysis.
- Compatibility: check the [RedMadRobot compatibility table](https://github.com/RedMadRobot/gradle-infrastructure) for Kotlin/Gradle version requirements.

#### References
- [RedMadRobot Gradle Infrastructure](https://github.com/RedMadRobot/gradle-infrastructure)
- [Plugin Documentation](https://github.com/RedMadRobot/gradle-infrastructure/tree/main/infrastructure/detekt)
