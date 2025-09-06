/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.generators

import arrow.core.Either
import arrow.core.Either.Left
import arrow.core.Either.Right
import arrow.core.left
import arrow.core.right
import io.kotest.property.Arb
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.map

/**
 * Transforms an [Arb] of [Either] values by applying a mapping function to the [Either.Right] values, producing a new
 * [Arb] of [Either].
 *
 * @param f A mapping function that takes an [Either.Right] value and returns an [Arb] of the transformed value.
 * @return An [Arb] of [Either] where [Either.Right] values are transformed using the provided function and
 *   [Either.Left] values remain unchanged.
 */
inline fun <E, A, B> Arb<Either<E, A>>.traverseEither(crossinline f: (A) -> Arb<B>): Arb<Either<E, B>> =
    flatMap { e ->
        e.fold(
            ifLeft = { Arb.constant(it.left()) },
            ifRight = { a -> f(a).map { it.right() } }
        )
    }
