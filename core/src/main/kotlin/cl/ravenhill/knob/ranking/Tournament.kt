/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.ranking

import cl.ravenhill.knob.repr.Solution

public class Tournament<T> private constructor(
    public val items: List<Solution<T>>,
    private val win: Array<BooleanArray>,     // win[i][j] == items[i] beats items[j]
    private val outdegree: IntArray               // outdegree of each vertex
) {
    public val size: Int get() = items.size

    /** Index of a Condorcet winner if it exists, else null. */
    public fun condorcetIndex(): Int? = (0..<size).firstOrNull { outdegree[it] == size - 1 }
    public fun condorcet(): Solution<T>? = condorcetIndex()?.let(items::get)

    /** Smith set (minimal non-empty set dominating outside). Returns indices. */
    public fun smithSet(): Set<Int> {
        // Start from all; peel until minimal dominant set remains.
        var S = (0 until size).toMutableSet()
        var changed: Boolean
        do {
            changed = false
            val tryRemove = ArrayList<Int>(S.size)
            for (i in S) if (dominatesOutside(i, S)) tryRemove += i
            for (i in tryRemove) {
                val Snext = (S - i)
                if (Snext.isNotEmpty() && dominatesOutside(Snext)) {
                    S = Snext.toMutableSet()
                    changed = true
                }
            }
        } while (changed)
        return S
    }

    /** Schwartz set (minimal non-empty set with no incoming edges from outside). Returns indices. */
    public fun schwartzSet(): Set<Int> {
        var S = (0 until size).toMutableSet()
        var changed: Boolean
        do {
            changed = false
            val tryRemove = ArrayList<Int>(S.size)
            for (i in S) if (isUnbeatenByOutside(i, S)) tryRemove += i
            for (i in tryRemove) {
                val Snext = (S - i)
                if (Snext.isNotEmpty() && unbeatenByOutside(Snext)) {
                    S = Snext.toMutableSet()
                    changed = true
                }
            }
        } while (changed)
        return S
    }

    /** Copeland best index (max outdegree); ties broken by smallest index. */
    public fun copelandIndex(): Int = outdegree.indices.maxBy { outdegree[it] }
    public fun copeland(): Solution<T> = items[copelandIndex()]

    /**
     * Pick a single winner prioritizing: Condorcet → Smith-tiebreak → Copeland.
     * Inside Smith, default tiebreak is highest Copeland; if still tied, pick the one that beats the most *inside* Smith.
     */
    public fun pickWinner(): Solution<T> {
        condorcet()?.let { return it }
        val smith = smithSet()
        if (smith.size == 1) return items[smith.first()]
        // Tiebreak within Smith
        val best = smith.maxBy { i ->
            val copeland = outdegree[i]
            val insideWins = smith.count { j -> i != j && win[i][j] }
            (copeland shl 20) + insideWins // lexicographic combo without extra allocations
        }
        return items[best]
    }

    /** True if i beats everyone outside S. */
    private fun dominatesOutside(i: Int, S: Set<Int>): Boolean {
        for (j in 0 until size) if (j !in S && !win[i][j]) return false
        return true
    }

    /** True if S as a whole dominates its outside: for each j outside S, some i in S beats j. */
    private fun dominatesOutside(S: Set<Int>): Boolean {
        outer@ for (j in 0 until size) if (j !in S) {
            for (i in S) if (win[i][j]) continue@outer
            return false
        }
        return true
    }

    /** True if i has no losses coming from outside S. */
    private fun isUnbeatenByOutside(i: Int, S: Set<Int>): Boolean {
        for (j in 0 until size) if (j !in S && win[j][i]) return false
        return true
    }

    /** True if no vertex outside S beats any vertex in S. */
    private fun unbeatenByOutside(S: Set<Int>): Boolean {
        for (j in 0 until size) if (j !in S)
            for (i in S) if (win[j][i]) return false
        return true
    }

    public companion object {
        /** Build a tournament by evaluating the oracle on all unordered pairs. O(n^2) comparisons. */
        @JvmStatic
        public fun <T> of(
            items: List<Solution<T>>,
            oracle: Oracle<T>
        ): Tournament<T> {
            require(items.isNotEmpty()) { "Tournament requires a non-empty list" }
            val n = items.size
            val win = Array(n) { BooleanArray(n) }
            val out = IntArray(n)
            for (i in 0 until n) {
                for (j in i + 1 until n) {
                    val ij = with(oracle) {
                        items[i] isBetterThan items[j]
                    }
                    win[i][j] = ij
                    win[j][i] = !ij
                    if (ij) out[i]++ else out[j]++
                }
            }
            return Tournament(items, win, out)
        }
    }
}
