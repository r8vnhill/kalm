/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package api.core.operator
import arrow.core.NonEmptyList
import cl.ravenhill.knob.operator.Perturber
import cl.ravenhill.knob.repr.Solution
import cl.ravenhill.knob.repr.map
import kotlin.math.absoluteValue
import kotlin.random.Random

/**
 * Demonstrates the different ways a [Perturber] can be instantiated and composed.
 */
fun main() {
    val seed = 420
    val rng = Random(seed)
    val candidate = Solution.of(1.0, -2.0, 3.5)

    println("Original candidate       : $candidate")
    println("Lambda perturber         : ${lambdaPerturberExample(candidate, rng)}")
    println("Nominal class perturber  : ${nominalPerturberExample(candidate)}")
    println("Anonymous perturber      : ${anonymousPerturberExample(candidate)}")
    println("Method-reference wrapper : ${methodReferencePerturberExample(candidate)}")
    println("Composed perturber       : ${composedPerturberExample(candidate, rng)}")
}

/**
 * Builds a [Perturber] via lambda expression (thanks to Kotlin fun interfaces).
 */
fun lambdaPerturberExample(
    candidate: Solution<Double>,
    rng: Random,
    radius: Double = 0.25
): Solution<Double> {
    val gaussianLikePerturber = Perturber<Double> { solution ->
        solution
            .map { value -> value + rng.nextDouble(-radius, radius) }
            .toSolution()
    }
    return gaussianLikePerturber.perturb(candidate)
}

/**
 * Defines a named class that implements [Perturber] explicitly.
 */
class CoolingPerturber(
    private val coolingRate: Double
) : Perturber<Double> {
    override fun perturb(solution: Solution<Double>): Solution<Double> =
        solution
            .mapIndexed { index, value -> value - coolingRate * (index + 1) }
            .toSolution()
}

fun nominalPerturberExample(candidate: Solution<Double>): Solution<Double> {
    val cooling = CoolingPerturber(coolingRate = 0.1)
    return cooling.perturb(candidate)
}

/**
 * Uses an anonymous object expression to implement [Perturber] inline.
 * Not recommended for production code; prefer lambdas or named classes.
 */
fun anonymousPerturberExample(candidate: Solution<Double>): Solution<Double> {
    @Suppress("ObjectLiteralToLambda")
    val reflector = object : Perturber<Double> {
        override fun perturb(solution: Solution<Double>): Solution<Double> =
            solution.map { value -> value.absoluteValue }.toSolution()
    }
    return reflector.perturb(candidate)
}

/**
 * Reuses an existing function as a [Perturber] through a method reference.
 */
fun methodReferencePerturberExample(candidate: Solution<Double>): Solution<Double> {
    val clipper = Perturber(::clipToUnitInterval)
    return clipper.perturb(candidate)
}

private fun clipToUnitInterval(solution: Solution<Double>): Solution<Double> =
    solution
        .map { value -> value.coerceIn(0.0, 1.0) }
        .toSolution()

/**
 * Shows how simple perturbers can be composed (pipeline style).
 */
fun composedPerturberExample(candidate: Solution<Double>, rng: Random): Solution<Double> {
    val jitter = Perturber<Double> { solution ->
        solution.map { value -> value + rng.nextDouble(-0.05, 0.05) }.toSolution()
    }
    val clamp = Perturber(::clipToUnitInterval)
    val composed = Perturber { solution -> clamp.perturb(jitter.perturb(solution)) }
    return composed.perturb(candidate)
}

/**
 * Utility to rebuild a [Solution] from a non-empty list of values.
 */
private fun <T> List<T>.toSolution(): Solution<T> {
    require(isNotEmpty()) { "Cannot create a Solution from an empty list" }
    return Solution(NonEmptyList(first(), drop(1)))
}
