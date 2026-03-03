/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

package cl.ravenhill.kalm

/**
 * Re-export of Arrow's [arrow.core.NonEmptyList].
 *
 * This typealias intentionally *resurfaces* Arrow APIs that are used throughout KALM, so that users can reference core
 * functional data types via the KALM public API without importing (or depending on) Arrow types directly in their
 * source code.
 *
 * ## Usage:
 *
 * Use [NonEmptyList] when an API requires a list that is statically guaranteed to contain at least one element.
 *
 * ### Example 1: Exposing a KALM API without Arrow imports
 *
 * ```kotlin
 * import cl.ravenhill.kalm.NonEmptyList
 *
 * fun errors(): NonEmptyList<String> = TODO()
 * ```
 *
 * @param T Element type.
 */
public typealias NonEmptyList<T> = arrow.core.NonEmptyList<T>

/**
 * Re-export of Arrow's [arrow.core.Either].
 *
 * This typealias intentionally *resurfaces* Arrow APIs that are used throughout KALM, so that users can reference core
 * functional data types via the KALM public API without importing (or depending on) Arrow types directly in their
 * source code.
 *
 * ## Usage:
 *
 * Use [Either] to model computations that can result in either an error value ([ERROR]) or a success value ([SUCCESS]).
 *
 * ### Example 1: Returning an error-or-value result via the KALM API surface
 *
 * ```kotlin
 * import cl.ravenhill.kalm.Either
 *
 * fun parseInt(input: String): Either<String, Int> = TODO()
 * ```
 *
 * @param ERROR Error (left) type.
 * @param SUCCESS Success (right) type.
 */
public typealias Either<ERROR, SUCCESS> = arrow.core.Either<ERROR, SUCCESS>
