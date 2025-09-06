/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.generators

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.property.Arb
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.map

/**
 * Flat-traverses an [Arb] of [Either] by binding successful samples (`Right<A>`) into an [Arb] that already yields
 * `Either<E, B>`, and re-emitting the resulting `Either` unchanged.
 *
 * Behavior:
 * - `Left(E)` samples are propagated as-is.
 * - `Right(A)` samples are mapped with [f] and the resulting `Either<E, B>` is returned.
 *
 * @receiver An [Arb] that produces [Either] values with outer error type [E] and success type [A].
 * @param f Mapping from the success value [A] to an [Arb] of `Either<E, B>`.
 * @param E The outer error type carried by the [Either].
 * @param A The success type produced by this [Arb] before mapping.
 * @param B The success type produced after mapping.
 * @return An [Arb] of `Either<E, B>` with left values preserved and rights mapped via [f].
 */
inline fun <E, A, B> Arb<Either<E, A>>.flatTraverseEither(
    crossinline f: (A) -> Arb<Either<E, B>>
): Arb<Either<E, B>> =
    flatMap { ea ->
        ea.fold(
            ifLeft = { Arb.constant(it.left()) },
            ifRight = { a -> f(a) } // already Arb<Either<E, B>>
        )
    }

/**
 * Flat-traverses an [Arb] of [Either] when the inner computation reports a **narrower** error type [E2].
 * The inner left is **widened** to the outer error type [E] using [widenLeft].
 *
 * Behavior:
 * - `Left(E)` samples are propagated as-is.
 * - `Right(A)` samples are mapped with [f]; the resulting `Either<E2, B>` is transformed to `Either<E, B>` via
 *   [widenLeft].
 *
 * @receiver An [Arb] that produces [Either] values with outer error type [E] and success type [A].
 * @param f Mapping from the success value [A] to an [Arb] of `Either<E2, B>`.
 * @param widenLeft Function that widens the inner error [E2] to the outer error type [E].
 * @param E The outer error type carried by the [Either].
 * @param A The success type produced by this [Arb] before mapping.
 * @param E2 The inner (narrower) error type produced by [f] that must be widened.
 * @param B The success type produced after mapping.
 * @return An [Arb] of `Either<E, B>` with left values preserved (widened when necessary) and rights mapped via [f].
 */
inline fun <E, A, E2, B> Arb<Either<E, A>>.flatTraverseEither(
    crossinline f: (A) -> Arb<Either<E2, B>>,
    crossinline widenLeft: (E2) -> E
): Arb<Either<E, B>> =
    flatMap { ea ->
        ea.fold(
            ifLeft = { Arb.constant(it.left()) },
            ifRight = { a -> f(a).map { it.mapLeft(widenLeft) } }
        )
    }
