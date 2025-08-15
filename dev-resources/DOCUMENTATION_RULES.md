# Documentation Guidelines

## 1. Markdown Formatting for Code Snippets

When writing code snippets in documentation, **always** use proper Markdown syntax. Enclose all code blocks with:

````markdown
```kotlin
// ...
```
````

This ensures consistent syntax highlighting and improves readability across all documentation platforms.

---

## 2. Use of `@property` for Public Variables

When documenting classes, **prefer** using the `@property` tag to describe public variables, rather than documenting them inline within the class body.
This improves discoverability in generated documentation and keeps property metadata centralized.

---

## 3. Preferred Use of Reference Links

When referring to types or other significant elements, **prefer** reference-style links (e.g., `[String]`) over monospace formatting (e.g., `` `String` ``).
This allows generated documentation to provide navigable links to type definitions.

---

## 4. Usage Examples

When providing usage examples in KDoc, follow this structure:

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
> **Place examples before the `@tags` section.**
> Common tags include `@param`, `@return`, `@throws`, and `@receiver`.

---

## 5. Context Parameters

KDoc does not currently have an official syntax for context parameters.
Until a standard emerges, **use** the `@param` tag to document them.

```kotlin
/**
 * ...
 * @param ctx Scope in which the operation runs.
 */
context(ctx: CoroutineScope)
fun fetchData() { /* ... */ }
```
