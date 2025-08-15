/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.repr.gen

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.right
import cl.ravenhill.keen.gen.flatTraverseEither
import cl.ravenhill.keen.gen.traverseEither
import cl.ravenhill.keen.repr.Gradient
import cl.ravenhill.keen.util.size.Size
import cl.ravenhill.keen.util.size.SizeError
import cl.ravenhill.keen.util.size.UnsafeSizeCreation
import io.kotest.property.Arb
import io.kotest.property.arbitrary.choose
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.doubleArray
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map

//#region Type aliases (test ergonomics)

/**
 * Either-wrapped [DoubleArray]; `Left` holds a [SizeError].
 */
private typealias ArrayEither = Either<SizeError, DoubleArray>

/**
 * Either-wrapped pair of a backing [DoubleArray] and its corresponding [Gradient].
 */
private typealias PairEither = Either<SizeError, Pair<DoubleArray, Gradient>>

/**
 * Kotest arbitrary for [ArrayEither].
 */
private typealias ArrayArb = Arb<ArrayEither>

/**
 * Kotest arbitrary for [PairEither].
 */
private typealias PairArb = Arb<PairEither>

/**
 * Represents an index into an array or list structure.
 *
 * This alias is **not validated**, meaning values may be negative or outside the bounds of any particular collection.
 * It is the caller's responsibility to ensure correctness before use.
 *
 * Primarily used to improve type clarity in contexts where raw [Int] values serve as positional references.
 */
private typealias Index = Int
//#endregion

//#region Generators

/**
 * Builds an [Arb] that yields `Either<SizeError, DoubleArray>` using context-provided generators.
 *
 * @receiver [Arb.Companion]
 * @return An [Arb] of `Either<SizeError, DoubleArray>`.
 */
context(sizeCtx: Arb<Either<SizeError, Size>>, contentCtx: Arb<Double>)
fun Arb.Companion.sizedDoubleArrayEither(): Arb<Either<SizeError, DoubleArray>> =
    sizeCtx.traverseEither { sz ->
        Arb.doubleArray(length = Arb.constant(sz.toInt()), content = contentCtx)
    }

/**
 * Pairs each successful array sample with a freshly **copied** [Gradient] via [Gradient.fromArray].
 *
 * The resulting pair is `(originalArray, gradient)`, where the gradient's storage is a **defensive copy**. Mutating the
 * original array **does not** affect the gradient.
 *
 * @return An [Arb] yielding a [PairEither] where the [Gradient] is built with [Gradient.fromArray].
 */
internal fun ArrayArb.pairedWithGradient(): PairArb =
    flatTraverseEither { arr ->
        Arb.constant(
            Gradient.fromArray(arr).map { g -> arr to g }
        )
    }

/**
 * Attaches a valid random index to each successfully generated array-gradient pair.
 *
 * This generator transforms an [Arb] of [PairEither] —which wraps either a [SizeError] or a successful [Pair] of
 * [DoubleArray] and [Gradient]— into an [Arb] of [Either] containing a [Triple] of:
 *
 * 1. **Index** ([Int]) — A random position in the array, uniformly sampled in the range `0 until arr.size`.
 * 2. **Array** ([DoubleArray]) — The original array associated with the generated [Gradient].
 * 3. **Gradient** ([Gradient]) — The [Gradient] derived from the array.
 *
 * If the original [Either] is a `Left` containing a [SizeError], the error is preserved.
 *
 * @receiver An [Arb] producing either a [SizeError] or a ([DoubleArray], [Gradient]) pair.
 * @return An [Arb] producing either a [SizeError] or a ([Int], [DoubleArray], [Gradient]) triple.
 */
internal fun Arb<PairEither>.withIndex(): Arb<Either<SizeError, Triple<Index, DoubleArray, Gradient>>> =
    flatTraverseEither { (arr, g) ->
        Arb.int(0 until arr.size)
            .map { i -> Triple(i, arr, g).right() }
    }

/**
 * Pairs each successful array sample with an **aliased** [Gradient] via [Gradient.unsafeFromOwnedArray] (no copy).
 *
 * The resulting pair is `(backingArray, gradient)`, and both **share** the same storage:
 * mutating the array is immediately visible through the gradient and vice versa.
 *
 * @return An [Arb] yielding a [PairEither] where the [Gradient] **aliases** the provided array.
 */
@UnsafeSizeCreation
internal fun ArrayArb.pairedWithAliasedGradient(): PairArb =
    flatTraverseEither { arr ->
        Arb.constant(
            (arr to Gradient.unsafeFromOwnedArray(arr)).right()
        )
    }

/**
 * Creates an [Arb] that pairs each generated [NonEmptyList] of [Double] values with a [Gradient] built from those same
 * values.
 *
 * This is primarily intended for property-based tests where both the original list of components and its corresponding
 * gradient are needed.
 *
 * @receiver An [Arb] that generates non-empty lists of [Double] values.
 * @return An [Arb] producing pairs where:
 * - The first element is the generated [NonEmptyList] of [Double] values.
 * - The second element is a [Gradient] constructed from the same values.
 */
internal fun Arb<NonEmptyList<Double>>.withGradient():
        Arb<Pair<NonEmptyList<Double>, Gradient>> = map { nel ->
    nel to Gradient(nel)
}

/**
 * Generates triples containing a valid index, a [NonEmptyList] of [Double] values, and a corresponding [Gradient]
 * derived from that list.
 *
 * This generator ensures that the index is always within the bounds of the provided [NonEmptyList] and [Gradient],
 * making it suitable for testing index-based access without risk of out-of-bounds errors.
 *
 * @receiver An [Arb] producing pairs of [NonEmptyList] of [Double] and their
 * corresponding [Gradient].
 * @return An [Arb] producing triples where:
 * - the first element is a valid [Index] within the [NonEmptyList],
 * - the second element is the original [NonEmptyList] of [Double],
 * - the third element is the corresponding [Gradient].
 */
internal fun Arb<Pair<NonEmptyList<Double>, Gradient>>.withValidIndex(): Arb<GradientWithIndex> =
    flatMap { (nel, gradient) ->
        Arb.int(0..<nel.size).map { index ->
            GradientWithIndex(nel, gradient, index)
        }
    }

// Generates OOB indices with strong shrinking and good edge-case bias.
internal fun Arb<Pair<NonEmptyList<Double>, Gradient>>.withOutOfBoundsIndex(
    undershoot: IntRange = 1..5,  // produces negatives like -1..-5
    overshoot: IntRange = 1..5   // produces `(size + 1)..(size + 5)`
): Arb<GradientWithIndex> =
    flatMap { (nel, gradient) ->
        val size = nel.size

        // Compute safe high bounds to avoid overflow when size is huge
        val hiStart = (size + overshoot.first).let { if (it < size) Int.MAX_VALUE else it }
        val hiEnd = (size + overshoot.last).let { if (it < size) Int.MAX_VALUE else it }

        val edges = Arb.element(-1, size) // the two canonical OOBs
        val low = Arb.int(-undershoot.last..-undershoot.first)
        val high = if (hiStart <= hiEnd) Arb.int(hiStart..hiEnd) else Arb.constant(Int.MAX_VALUE)

        // Bias towards edges; keep some spread on low/high
        Arb.choose(
            5 to edges,
            3 to low,
            3 to high
        ).map { idx -> GradientWithIndex(nel, gradient, idx) }
    }

//#endregion

//#region Data classes

/**
 * Represents a [Gradient] together with its original components and a valid index.
 *
 * @property components The non-empty list of doubles from which the gradient was created.
 * @property gradient The gradient instance corresponding to [components].
 * @property index A valid index within the range `0 until components.size`.
 */
internal data class GradientWithIndex(
    val components: NonEmptyList<Double>,
    val gradient: Gradient,
    val index: Index
)

//#endregion
