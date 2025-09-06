/*
 * Copyright (c) 2025,
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.repr.gen

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.right
import cl.ravenhill.knob.generators.doubleArrayExact
import cl.ravenhill.knob.generators.flatTraverseEither
import cl.ravenhill.knob.generators.traverseEither
import cl.ravenhill.knob.repr.Gradient
import cl.ravenhill.knob.utils.size.Size
import cl.ravenhill.knob.utils.size.SizeError
import cl.ravenhill.knob.utils.size.UnsafeSizeCreation
import io.kotest.property.Arb
import io.kotest.property.arbitrary.choose
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map

//#region Type aliases (test ergonomics)

/** Either-wrapped [DoubleArray]; Left holds a [cl.ravenhill.knob.utils.size.SizeError]. */
private typealias ArrayEither = Either<SizeError, DoubleArray>
/** Either-wrapped pair of a backing [DoubleArray] and its corresponding [Gradient]. */
private typealias PairEither = Either<SizeError, Pair<DoubleArray, Gradient>>
/** Kotest arbitrary for [ArrayEither]. */
private typealias ArrayArb = Arb<ArrayEither>
/** Kotest arbitrary for [PairEither]. */
private typealias PairArb = Arb<PairEither>

/** Named pairs for clarity in tests. */
internal typealias NELGradientPair = Pair<NonEmptyList<Double>, Gradient>
internal typealias ArrayGradientPair = Pair<DoubleArray, Gradient>

/**
 * Represents an index into an array or list structure (not validated).
 * Callers must ensure bounds when using this as a position.
 */
internal typealias Index = Int
//#endregion

//#region Generators

/**
 * Pairs each successful array sample with a freshly **copied** [Gradient] via [Gradient.fromArray].
 *
 * The resulting pair is `(originalArray, gradient)`, where the gradient's storage is a **defensive copy**.
 */
internal fun ArrayArb.pairedWithGradient(): PairArb =
    flatTraverseEither { arr ->
        Arb.constant(Gradient.fromArray(arr).map { g -> arr to g })
    }

/**
 * Attaches a valid random index (0 until size) to each successful (array, gradient) pair.
 *
 * @return Either preserving left errors or `Right(index, array, gradient)`.
 */
internal fun Arb<PairEither>.withIndex():
        Arb<Either<SizeError, Triple<Index, DoubleArray, Gradient>>> =
    flatTraverseEither { (arr, g) ->
        Arb.int(0 until arr.size).map { i -> Triple(i, arr, g).right() }
    }

/**
 * Pairs each successful array sample with an **aliased** [Gradient] via [Gradient.unsafeFromOwnedArray] (no copy).
 * Mutations to the array are visible through the gradient.
 */
@UnsafeSizeCreation
internal fun ArrayArb.pairedWithAliasedGradient(): PairArb =
    flatTraverseEither { arr ->
        Arb.constant((arr to Gradient.unsafeFromOwnedArray(arr)).right())
    }

/**
 * Pairs each generated [NonEmptyList] of [Double] values with a [Gradient] built from those same values.
 */
internal fun Arb<NonEmptyList<Double>>.withGradient(): Arb<NELGradientPair> =
    map { nel -> nel to Gradient(nel) }

/**
 * Pairs each generated [NonEmptyList] with an **aliased** [Gradient] that reuses the same [DoubleArray] storage.
 */
@UnsafeSizeCreation
internal fun Arb<NonEmptyList<Double>>.withAliasedGradient(): Arb<ArrayGradientPair> =
    map { nel ->
        val arr = nel.toDoubleArray()
        arr to Gradient.unsafeFromOwnedArray(arr)
    }

/**
 * Generates [GradientNelWithIndex] instances with an index guaranteed to be **within bounds**
 * for a `(NonEmptyList, Gradient)` pair.
 */
@JvmName("withValidIndexNel")
internal fun Arb<NELGradientPair>.withValidIndex(): Arb<GradientNelWithIndex> =
    flatMap { (nel, gradient) ->
        Arb.int(0 until nel.size).map { index ->
            GradientNelWithIndex(nel, gradient, index)
        }
    }

/**
 * Generates [ArrayGradientPair] with a valid index in the range `0 until array.size`.
 *
 * Useful when testing aliasing behaviors with gradients built from arrays.
 */
@JvmName("withValidIndexArray")
internal fun Arb<ArrayGradientPair>.withValidIndex(): Arb<GradientArrayWithIndex> =
    flatMap { (arr, gradient) ->
        Arb.int(0 until arr.size).map { idx ->
            GradientArrayWithIndex(arr, gradient, idx)
        }
    }

/**
 * Generates [GradientNelWithIndex] with an index **out of bounds** for a `(NonEmptyList, Gradient)` pair.
 *
 * - Edges: `-1` and `size`.
 * - Undershoot: `-undershoot.last .. -undershoot.first`.
 * - Overshoot: `size + overshoot.first .. size + overshoot.last` (overflow-guarded).
 */
@JvmName("withOutOfBoundsIndexNel")
internal fun Arb<NELGradientPair>.withOutOfBoundsIndex(
    undershoot: IntRange = 1..5,
    overshoot: IntRange = 1..5
): Arb<GradientNelWithIndex> =
    flatMap { (nel, gradient) ->
        val size = nel.size

        val hiStart = (size + overshoot.first).let { if (it < size) Int.MAX_VALUE else it }
        val hiEnd = (size + overshoot.last).let { if (it < size) Int.MAX_VALUE else it }

        val edges = Arb.element(-1, size)
        val low = Arb.int(-undershoot.last..-undershoot.first)
        val high = if (hiStart <= hiEnd) Arb.int(hiStart..hiEnd) else Arb.constant(Int.MAX_VALUE)

        Arb.choose(
            5 to edges,
            3 to low,
            3 to high
        ).map { idx -> GradientNelWithIndex(nel, gradient, idx) }
    }

/**
 * Generates out-of-bounds indices for `(DoubleArray, Gradient)` pairs.
 *
 * Mirrors [withOutOfBoundsIndex] for NEL pairs, using the array size for bounds.
 */
@JvmName("withOutOfBoundsIndexArray")
internal fun Arb<ArrayGradientPair>.withOutOfBoundsIndex(
    undershoot: IntRange = 1..5,
    overshoot: IntRange = 1..5
): Arb<GradientArrayWithIndex> =
    flatMap { (arr, gradient) ->
        val size = arr.size

        val hiStart = (size + overshoot.first).let { if (it < size) Int.MAX_VALUE else it }
        val hiEnd = (size + overshoot.last).let { if (it < size) Int.MAX_VALUE else it }

        val edges = Arb.element(-1, size)
        val low = Arb.int(-undershoot.last..-undershoot.first)
        val high = if (hiStart <= hiEnd) Arb.int(hiStart..hiEnd) else Arb.constant(Int.MAX_VALUE)

        Arb.choose(
            5 to edges,
            3 to low,
            3 to high
        ).map { idx -> GradientArrayWithIndex(arr, gradient, idx) }
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
internal data class GradientNelWithIndex(
    val components: NonEmptyList<Double>,
    val gradient: Gradient,
    val index: Index
)

/**
 * Triple-like container for property-based tests holding:
 * - A raw [DoubleArray] of component values.
 * - The associated [Gradient].
 * - An [Index] for lookup.
 *
 * ### Suppression notice:
 * The `@Suppress("ArrayInDataClass")` annotation is intentional.
 * Normally, having an `Array` (or primitive array) in a `data class` triggers a warning because array equality is
 * reference-based, not structural, and default `equals()` / `hashCode()` generated by the compiler will therefore
 * not behave as expected for identity comparisons.
 *
 * In this case, **the class is used exclusively for destructuring in property-based tests** and never for
 * identity-based comparisons, so this is harmless.
 *
 * ### Maintainer note:
 * If future requirements involve comparing instances of this class for equality (e.g., storing them in sets, using as
 * map keys, or asserting equality), you **must** override `equals()` and `hashCode()` to perform content-based
 * comparison on [array] (e.g., using `contentEquals()` / `contentHashCode()`), or refactor to use [List] instead of
 * [DoubleArray].
 */
@Suppress("ArrayInDataClass")
internal data class GradientArrayWithIndex(
    val array: DoubleArray,
    val gradient: Gradient,
    val index: Index
)

//#endregion
