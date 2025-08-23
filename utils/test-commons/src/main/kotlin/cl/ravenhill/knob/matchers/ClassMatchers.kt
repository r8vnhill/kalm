/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.matchers

import io.kotest.assertions.asClue
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.should
import io.kotest.matchers.shouldNot
import kotlin.reflect.KClass

/* ---------- Public API ---------- */

/**
 * Creates a [Matcher] that checks whether a value has the **same runtime class** as [other].
 *
 * ## Usage:
 * ```kotlin
 * val a: Any = "Eric"
 * val b: Any = "Hobsbawm"
 * a should haveSameClassAs(b) // ✅ passes
 * ```
 *
 * @param other The reference instance whose class will be used for comparison.
 * @return A [Matcher] asserting strict class equality with [other].
 */
fun haveSameClassAs(other: Any): Matcher<Any?> = haveClass(other::class)

/**
 * Creates a [Matcher] that checks whether a value has the **exact runtime class** [kClass].
 *
 * Unlike `isInstanceOf`, this requires exact class equality—subclasses do not match.
 *
 * ## Usage:
 * ```kotlin
 * val x: Any = 420
 * x should haveClass(Int::class) // ✅ passes
 * ```
 *
 * @param kClass The [KClass] the actual value must match exactly.
 * @return A [Matcher] for strict class equality.
 */
fun haveClass(kClass: KClass<out Any>): Matcher<Any?> = exactClassMatcher(kClass)

/**
 * Reified overload of [haveClass] for conciseness.
 *
 * ## Usage:
 * ```kotlin
 * val x: Any = 420
 * x should haveClass<Int>() // ✅ passes
 * ```
 *
 * @return A [Matcher] for strict class equality on the reified type parameter [T].
 */
inline fun <reified T : Any> haveClass(): Matcher<Any?> = haveClass(T::class)

/**
 * Asserts that this value has the same runtime class as [other].
 *
 * ## Usage:
 * ```kotlin
 * "Ryohgo" shouldHaveSameClassAs "Narita"  // ✅ passes
 * ```
 *
 * @param other The reference instance to compare against.
 */
infix fun Any?.shouldHaveSameClassAs(other: Any) =
    this.asClue { this should haveSameClassAs(other) }

/**
 * Asserts that this value does **not** have the same runtime class as [other].
 *
 * ## Usage:
 * ```kotlin
 * "Tolkien" shouldNotHaveSameClassAs 420 // ✅ passes
 * ```
 *
 * @param other The reference instance to compare against.
 */
infix fun Any?.shouldNotHaveSameClassAs(other: Any) =
    this.asClue { this shouldNot haveSameClassAs(other) }

/**
 * Asserts that this value has the **exact runtime class** [kClass].
 *
 * ## Usage:
 * ```kotlin
 * val x: Any = 42
 * x shouldHaveClass Int::class // ✅ passes
 * ```
 *
 * @param kClass The [KClass] the value is expected to have.
 */
infix fun Any?.shouldHaveClass(kClass: KClass<out Any>) =
    this.asClue { this should haveClass(kClass) }

/**
 * Asserts that this value does **not** have the **exact runtime class** [kClass].
 *
 * ## Usage:
 * ```kotlin
 * val x: Any = 42
 * x shouldNotHaveClass String::class // ✅ passes
 * ```
 *
 * @param kClass The [KClass] the value should not match.
 */
infix fun Any?.shouldNotHaveClass(kClass: KClass<out Any>) =
    this.asClue { this shouldNot haveClass(kClass) }

/**
 * Asserts that this value has the **exact runtime class** [T].
 *
 * ## Usage:
 * ```kotlin
 * val x: Any = 42
 * x.shouldHaveClass<Int>() // ✅ passes
 * ```
 *
 * @receiver The value being checked.
 * @param T The expected type.
 */
inline fun <reified T : Any> Any?.shouldHaveClass() =
    this.asClue { this should haveClass<T>() }

/**
 * Asserts that this value does **not** have the **exact runtime class** [T].
 *
 * ## Usage:
 * ```kotlin
 * val x: Any = 42
 * x.shouldNotHaveClass<String>() // ✅ passes
 * ```
 *
 * @receiver The value being checked.
 * @param T The type that must not match the runtime class.
 */
inline fun <reified T : Any> Any?.shouldNotHaveClass() =
    this.asClue { this shouldNot haveClass<T>() }

/* ---------- Core & helpers (private) ---------- */

/**
 * Builds a [Matcher] that checks whether a value has the **exact runtime class** [expected].
 *
 * This matcher succeeds only if:
 * - The value is non-null, and
 * - Its [KClass] matches [expected] exactly (no subclasses, supertypes, or interfaces).
 *
 * @param expected The exact [KClass] the actual value must have.
 * @return A [Matcher] for asserting strict class equality.
 */
private fun exactClassMatcher(expected: KClass<*>): Matcher<Any?> = Matcher { actual ->
    val passed = actual?.let { it::class == expected } == true
    MatcherResult(
        passed = passed,
        failureMessageFn = {
            "Expected instance of ${expected.fqName()}, but was ${actual.className()} (value: ${actual.preview()})."
        },
        negatedFailureMessageFn = {
            "Expected not to be instance of ${expected.fqName()}, but it was (value: ${actual.preview()})."
        }
    )
}

/**
 * Resolves the runtime class name of a value in a human-readable form.
 *
 * - If the value is `null`, returns the literal string `"<null>"`.
 * - Otherwise, delegates to [fqName] on the value’s [KClass] for a fully qualified name, falling back to a simple name
 *   or `toString()` if necessary.
 *
 * @return A string representation of the runtime class name or `"<null>"`.
 */
private fun Any?.className(): String =
    when (this) {
        null -> "<null>"
        else -> this::class.fqName()
    }

/**
 * Returns a readable, fully qualified name for the [KClass].
 *
 * Resolution order:
 * 1. [KClass.qualifiedName] if available (e.g., `"kotlin.String"`).
 * 2. [KClass.simpleName] if the class has no qualified name (e.g., local or anonymous classes).
 * 3. Fallback to [toString] for edge cases.
 *
 * This is mainly used in failure messages to provide clear identification of the class under test, regardless of
 * whether it has a qualified name.
 *
 * @return A string representing the class name, preferring the fully qualified form.
 */
private fun KClass<*>.fqName(): String =
    qualifiedName ?: simpleName ?: toString()

/**
 * Creates a short, human-readable preview of any value for use in debug or failure messages.
 *
 * - If the value is `null`, returns the literal string `"null"`.
 * - If the value is a [String], it is wrapped in quotes and truncated with [elide] if longer than [max].
 * - For all other types, uses [toString] and truncates the result with [elide] if longer than [max].
 *
 * This helper prevents very large values from overwhelming test output while still providing enough context to identify
 * them.
 *
 * @param max Maximum length of the preview (defaults to `64`).
 * @return A truncated, readable representation of the value.
 */
private fun Any?.preview(max: Int = 64): String =
    when (this) {
        null -> "null"
        is String -> "\"${this.elide(max)}\""
        else -> this.toString().elide(max)
    }

/**
 * Produces a shortened preview of the [String] if it exceeds [max] characters.
 *
 * @param max Maximum number of characters allowed before truncation.
 * @return Either the original string (if short enough) or a truncated string ending in `…`.
 */
private fun String.elide(max: Int): String =
    if (length <= max) this else take(max - 1) + "…"
