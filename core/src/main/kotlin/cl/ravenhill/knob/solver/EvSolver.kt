/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.solver

import cl.ravenhill.knob.problem.Problem
import cl.ravenhill.knob.repr.Solution
import kotlin.random.Random

public interface GenerationalCohort<T> {
    public fun replace(toReplace: Solution<T>, newSolution: Solution<T>): GenerationalCohort<T>
}

public fun interface Initializer<T> {
    public fun initialize(problem: Problem<T>): GenerationalCohort<T>
}

public interface Sampler<T> {
    public fun sampleSingle(from: GenerationalCohort<T>): Solution<T>
}

public interface RemovalSampler<T> : Sampler<T>

public interface Perturber<T> {
    public fun perturb(solution: Solution<T>): Solution<T>
}

public interface Termination<T> {
    public fun shouldStop(problem: Problem<T>, cohort: GenerationalCohort<T>): Boolean
}

/**
 * EV: A Simple Evolutionary System
 *
 * 1. Introduction. (2006). En K. A. De Jong, Evolutionary computation: A unified approach (pp. 1–21). MIT Press.
 */
public interface EvSolver<T, P : Problem<T>> : Solver<T, P>, Perturber<T>, Termination<T>, Initializer<T> {
    public val sourceSampler: Sampler<T>
    public val replacementSampler: RemovalSampler<T>

    override fun invoke(problem: P): Solution<T> =
        evolve(problem)

    public fun evolve(problem: P, cohort: GenerationalCohort<T>? = null): Solution<T> {
        var activeCohort = cohort ?: initialize(problem)

        do {
            val source = sourceSampler.sampleSingle(activeCohort)
            val candidate = perturb(source)
            activeCohort = replacementSampler.sampleSingle(activeCohort).let {
                activeCohort.replace(it, candidate)
            }
        } while (!shouldStop(problem, activeCohort))
        return best(activeCohort)
    }

    public fun best(cohort: GenerationalCohort<T>): Solution<T>
}

/* =========================
 * Concrete EV components
 * ========================= */

/** Simple immutable cohort with per-member birth indices (for logging). */
private data class SimpleCohort<T>(
    val members: List<Solution<T>>,
    val birthIndex: List<Int>,          // same size as members
    val nextBirth: Int                  // monotonically increasing
) : GenerationalCohort<T> {

    override fun replace(toReplace: Solution<T>, newSolution: Solution<T>): GenerationalCohort<T> {
        val idx = members.indexOfFirst { it === toReplace }
        require(idx >= 0) { "Element to replace not found in cohort." }
        val newMembers = members.toMutableList().also { it[idx] = newSolution }
        val newBirths = birthIndex.toMutableList().also { it[idx] = nextBirth }
        return copy(members = newMembers, birthIndex = newBirths, nextBirth = nextBirth + 1)
    }
}

/** Uniform initializer over a 1-D interval [lo, hi] (population size n). */
private class UniformInitializer(
    private val n: Int,
    private val lo: Double,
    private val hi: Double,
    private val rng: Random
) : Initializer<Double> {
    override fun initialize(problem: Problem<Double>): GenerationalCohort<Double> {
        val xs = List(n) { lo + rng.nextDouble() * (hi - lo) }
        val sols = xs.map { Solution.of(it) }
        return SimpleCohort(members = sols, birthIndex = List(n) { it + 1 }, nextBirth = n + 1)
    }
}

/** Picks a random element (source or replacement). */
private class RandomSampler<T>(private val rng: Random) : Sampler<T> {
    override fun sampleSingle(from: GenerationalCohort<T>): Solution<T> {
        val c = from as SimpleCohort<T>
        return c.members[rng.nextInt(c.members.size)]
    }
}

/** Picks the worst element by the (single) objective (for replacement). */
private class WorstSampler<T>(
    private val objective: (Solution<T>) -> Double
) : RemovalSampler<T> {
    override fun sampleSingle(from: GenerationalCohort<T>): Solution<T> {
        val c = from as SimpleCohort<T>
        return c.members.minBy { objective(it) }
    }
}

/** Delta mutation: x' = x ± step (1D). */
private class DeltaPerturber(
    private val step: Double,
    private val rng: Random
) : Perturber<Double> {
    override fun perturb(solution: Solution<Double>): Solution<Double> {
        val x = solution.first()
        val dir = if (rng.nextBoolean()) 1.0 else -1.0
        return Solution.of(x + dir * step)
    }
}

/** Stops after a fixed number of births (replacements). */
private class BirthBudget(
    private val maxBirths: Int
) : Termination<Double> {
    override fun shouldStop(problem: Problem<Double>, cohort: GenerationalCohort<Double>): Boolean {
        val c = cohort as SimpleCohort<Double>
        // nextBirth counts from 1; number of births so far is nextBirth - 1 minus initial size
        // We stop when we've produced maxBirths births after initialization.
        val birthsAfterInit = (c.nextBirth - 1) - c.members.size
        return birthsAfterInit >= maxBirths
    }
}

/** EV loop specialized to a single-objective, 1-D example. */
private abstract class SimpleEv(
    override val sourceSampler: Sampler<Double>,
    override val replacementSampler: RemovalSampler<Double>,
    private val objective: (Solution<Double>) -> Double,
) : EvSolver<Double, Problem<Double>> {

    override fun best(cohort: GenerationalCohort<Double>): Solution<Double> {
        val c = cohort as SimpleCohort<Double>
        return c.members.maxBy { objective(it) }
    }
}

/* =============
 * Example main
 * ============= */

public fun main() {
    // Problem: maximize f(x) = 50 - x^2  (inverted parabola)
    val objective: (Solution<Double>) -> Double = { s -> 50.0 - s.first() * s.first() }
    val problem: Problem<Double> = Problem(
        objective = { sol -> objective(sol) } // single objective
    )

    // Parameters aligned with the book excerpt
    val seed = 12345
    val rng = Random(seed)
    val populationSize = 10
    val initLo = -100.0
    val initHi = 100.0
    val mutationStep = 1.0
    val birthLimit = 1_000

    // Components
    val initializer = UniformInitializer(populationSize, initLo, initHi, rng)
    val sourceSampler = RandomSampler<Double>(rng)               // pick a random source
    val replacementSampler = WorstSampler(objective)             // replace the current worst
    val perturber = DeltaPerturber(step = mutationStep, rng)
    val termination = BirthBudget(maxBirths = birthLimit)

    // Solver composition
    val solver = object : SimpleEv(sourceSampler, replacementSampler, objective),
        Initializer<Double> by initializer,
        Perturber<Double> by perturber,
        Termination<Double> by termination {}

    // Run once and print a short report similar to the book’s style
    val initial = (initializer.initialize(problem) as SimpleCohort<Double>)
    println("Simulation time limit (# births): $birthLimit")
    println("Random number seed (positive integer): $seed")
    println("Using an inverted parabolic landscape f(x) = 50 - x^2 with step size $mutationStep")
    println("Population size: $populationSize")
    println()
    println("Initial cohort:")
    dumpCohort(initial, objective)

    val best = solver.evolve(problem, initial)
    println()
    println("Best solution after $birthLimit births:")
    println("x = ${best.first()}, f(x) = ${objective(best)}")
}

/* =========
 * Utilities
 * ========= */

private fun dumpCohort(c: SimpleCohort<Double>, f: (Solution<Double>) -> Double) {
    val fitnesses = c.members.map(f)
    val max = fitnesses.max()
    val min = fitnesses.min()
    val ave = fitnesses.average()

    println("Global fitness: max = ${"%.5f".format(max)}, ave = ${"%.5f".format(ave)}, min = ${"%.5f".format(min)}")
    println()
    println("Indiv  birth  fitness      gene value")
    c.members.forEachIndexed { i, s ->
        val birth = c.birthIndex[i]
        val fit = f(s)
        val x = s.first()
        println("${i + 1}".padEnd(6) +
                "${birth}".padEnd(7) +
                "${"%.5f".format(fit)}".padEnd(12) +
                "${"%.5f".format(x)}")
    }
}
