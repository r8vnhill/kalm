/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.solver

import cl.ravenhill.knob.problem.Problem
import cl.ravenhill.knob.repr.Solution

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
