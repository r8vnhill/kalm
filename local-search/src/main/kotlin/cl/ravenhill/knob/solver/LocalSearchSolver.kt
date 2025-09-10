/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.solver

import cl.ravenhill.knob.eval.SolutionEvaluator
import cl.ravenhill.knob.problem.Problem
import cl.ravenhill.knob.repr.Solution
import cl.ravenhill.knob.termination.Termination

public typealias LocalSearchAlgorithm<T, P> = LocalSearchSolver<T, P>
public typealias LocalSearcher<T, P> = LocalSearchSolver<T, P>

public interface LocalSearchSolver<T, P : Problem<T>> :
    Solver<T, P>,
    NeighborGenerator<T>,
    SolutionEvaluator<T>,
    Termination<T, P> {

    override fun invoke(problem: P): Solution<T> =
        localSearch(problem)

    public fun localSearch(problem: P, currentSolution: Solution<T>? = null): Solution<T> {
        var best = currentSolution ?: generateNeighbor(null)
        var bestScore = evaluate(best)

        do {
            val candidate = generateNeighbor(best)
            val candidateScore = evaluate(candidate)
            if (candidateScore > bestScore) {
                best = candidate
                bestScore = candidateScore
            }
        } while (!shouldStop(problem, best))
        return best
    }

    public companion object {
        public operator fun <T, P : Problem<T>> invoke(
            neighborGenerator: NeighborGenerator<T>,
            solutionEvaluator: SolutionEvaluator<T>,
            termination: Termination<T, P>
        ): LocalSearchSolver<T, P> =
            Impl(neighborGenerator, solutionEvaluator, termination)
    }

    private class Impl<T, P : Problem<T>>(
        private val neighborGenerator: NeighborGenerator<T>,
        private val solutionEvaluator: SolutionEvaluator<T>,
        private val termination: Termination<T, P>
    ) : LocalSearchSolver<T, P>,
        NeighborGenerator<T> by neighborGenerator,
        SolutionEvaluator<T> by solutionEvaluator,
        Termination<T, P> by termination {

        override fun toString(): String =
            "LocalSearchSolver($neighborGenerator, $solutionEvaluator, $termination)"
    }
}
