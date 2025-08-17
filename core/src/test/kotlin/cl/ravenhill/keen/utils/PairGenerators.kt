/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.utils

import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.pair

/**
 * Builds an [Arb] that produces ordered pairs (a <= b) from two generators.
 *
 * If [strict] is true, only (a < b) pairs are emitted..
 */
internal fun <T : Comparable<T>> Arb.Companion.orderedPair(
    arb1: Arb<T>,
    arb2: Arb<T>,
    strict: Boolean = false
): Arb<Pair<T, T>> {
    // Generate a raw pair, then order it
    val ordered = Arb.pair(arb1, arb2).map { (a, b) ->
        if (a <= b) a to b else b to a
    }

    // If strict, reject equal pairs
    return if (strict) ordered.filter { (a, b) -> a < b } else ordered
}

/**
 * Builds an [Arb] that produces ordered pairs (a <= b) from a single generator.
 * If [strict] is true, only (a < b) pairs are emitted.
 *
 * @param arb The generator that produces values for the pairs.
 * @param strict If true, only strict inequality pairs (a < b) are emitted; otherwise, pairs with equality (a <= b) are
 *   also allowed.
 * @return An [Arb<Pair<T, T>>] that generates ordered pairs based on the given conditions.
 */
internal fun <T : Comparable<T>> Arb.Companion.orderedPair(arb: Arb<T>, strict: Boolean = false): Arb<Pair<T, T>> =
    orderedPair(arb, arb, strict)

/**
 * Produces an [Arb] that generates pairs of elements from two given arbitraries, where the elements in each generated
 * pair are arranged in descending order (b, a).
 */
internal fun <T : Comparable<T>> Arb.Companion.descendingPair(
    a1: Arb<T>,
    a2: Arb<T>,
    strict: Boolean = false
): Arb<Pair<T, T>> = orderedPair(a1, a2, strict)
    .map { (a, b) -> b to a }

/**
 * Produces an [Arb] that generates pairs of elements from a single arbitrary, where the elements in each generated
 * pair are arranged in descending order (b, a).
 */
internal fun <T : Comparable<T>> Arb.Companion.descendingPair(
    arb: Arb<T>,
    strict: Boolean = false
): Arb<Pair<T, T>> = descendingPair(arb, arb, strict)
