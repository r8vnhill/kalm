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
