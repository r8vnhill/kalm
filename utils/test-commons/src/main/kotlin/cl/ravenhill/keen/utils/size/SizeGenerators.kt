/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.utils.size

import arrow.core.Either
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map

/**
 * Produces an [Arb] that generates [Either]-wrapped [Size] values within [range].
 *
 * For each sampled [Int]:
 * - If it is valid for [Size] (e.g., non-negative), emits `Right(Size)`.
 * - Otherwise, emits `Left(SizeError)` describing the validation failure.
 *
 * @param range Inclusive range of candidate integer sizes (defaults to `0..1024`).
 * @return An [Arb] of `Either<SizeError, Size>` reflecting validation of each sampled value.
 */
fun Arb.Companion.size(range: IntRange = 0..1024): Arb<Either<SizeError, Size>> =
    Arb.int(range).map(Size::invoke)

/**
 * Convenience overload of `size(IntRange)` that accepts explicit bounds.
 *
 * @param min Minimum candidate size (inclusive). Default: `0`.
 * @param max Maximum candidate size (inclusive). Default: `1024`.
 * @return An [Arb] of `Either<SizeError, Size>` within `[min, max]`.
 */
fun Arb.Companion.size(
    min: Int = 0,
    max: Int = 1024
): Arb<Either<SizeError, Size>> =
    size(min..max)

/**
 * Creates an [Arb] that generates valid [Size] instances within the given [range].
 *
 * A size is considered valid if it can be constructed via [Size.ofOrThrow] without violating any constraints (e.g.,
 * non-negative).
 *
 * @param range Inclusive range of integer values to be converted into [Size] instances.
 *   Defaults to `0..1024`.
 * @return An [Arb] producing valid [Size] values within the specified range.
 * @throws IllegalArgumentException if a generated value is invalid for [Size].
 */
fun Arb.Companion.validSize(range: IntRange = 0..1024): Arb<Size> =
    Arb.int(range).map(Size::ofOrThrow)

/**
 * Creates an [Arb] that generates valid [Size] instances within the inclusive range `[min, max]`.
 *
 * This overload exists for convenience when you want to specify boundaries as separate parameters instead of an
 * [IntRange].
 *
 * @param min Minimum allowed size (inclusive). Defaults to `0`.
 * @param max Maximum allowed size (inclusive). Defaults to `1024`.
 * @return An [Arb] producing valid [Size] values between [min] and [max].
 * @throws IllegalArgumentException if a generated value is invalid for [Size].
 */
fun Arb.Companion.validSize(
    min: Int = 0,
    max: Int = 1024
): Arb<Size> = validSize(min..max)
