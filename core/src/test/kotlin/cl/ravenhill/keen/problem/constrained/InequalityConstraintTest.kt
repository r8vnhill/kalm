/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.problem.constrained

import cl.ravenhill.keen.repr.Solution
import cl.ravenhill.keen.utils.InequalityType
import io.kotest.core.spec.style.FreeSpec
import io.kotest.datatest.withData
import io.kotest.engine.names.WithDataTestName
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.enum


// Extension form = nicer   solution.leftSum()
private typealias DoubleFn = Solution<Int>.() -> Double

class InequalityConstraintTest : FreeSpec({

    fun assertConstraint(
        left: DoubleFn,
        right: DoubleFn,
        op: InequalityType,
        s: Solution<Int>,
        expect: Boolean
    ) {
        val ok = InequalityConstraint(left, right, op)
            .invoke(s)

        if (expect) ok.shouldBeTrue() else ok.shouldBeFalse()
    }

    fun case(
        op: InequalityType,
        sol: Solution<Int>,
        left: DoubleFn,
        right: DoubleFn,
        expect: Boolean
    ) = object : WithDataTestName {
        override fun dataTestName(): String = buildString {
            append("${sol.left()} ")
            append(op.symbol)
            append(" ${sol.right()}")
        }

        val run: () -> Unit = { assertConstraint(left, right, op, sol, expect) }
    }

    // region Lambdas used in many places

    val head: DoubleFn = { first().toDouble() }
    val tailSum: DoubleFn = { drop(1).sumOf(Int::toDouble) }
    val average: DoubleFn = { map(Int::toDouble).average() }
    val total: DoubleFn = { sumOf(Int::toDouble) }
    val minVal: DoubleFn = { minOrNull()!!.toDouble() }
    val maxVal: DoubleFn = { maxOrNull()!!.toDouble() }

    // endregion

    "An InequalityConstraint" - {
        "stores the requested type" {
            checkAll(Exhaustive.enum<InequalityType>()) { op ->
                InequalityConstraint(head, tailSum, op).type shouldBe op
            }
        }

        "invoking the constraint" - {
            withData(
                // region satisfied (true)
                // 1 < 2
                case(InequalityType.LESS_THAN, Solution(1, 2), head, maxVal, expect = true),
                // 6 <= 6
                case(InequalityType.LESS_THAN_OR_EQUAL, Solution(3, 2), { head() * 2 }, { tailSum() * 3 }, expect = true),
                // 21 > 10
                case(InequalityType.GREATER_THAN, Solution(10, 5, 6), total, head, expect = true),
                // 5 >= 5
                case(InequalityType.GREATER_THAN_OR_EQUAL, Solution(3, 7), average, { 5.0 }, expect = true),
                // 10 < -5
                case(InequalityType.LESS_THAN, Solution(-10, -5, -7), minVal, maxVal, expect = true),
                // 10001 > 10000
                case(InequalityType.GREATER_THAN, Solution(10000, 10000), { head() + 1 }, head, expect = true),
                // endregion
                // region violated (false)
                // 3 < 3
                case(InequalityType.LESS_THAN, Solution(3, 1), head, maxVal, expect = false),
                // 5 < 5
                case(InequalityType.LESS_THAN, Solution(5, 5), head, maxVal, expect = false),
                // 4 >= 5
                case(InequalityType.GREATER_THAN_OR_EQUAL, Solution(2, 6), average, { average() + 1 }, expect = false),
                // 7 > 7
                case(InequalityType.GREATER_THAN, Solution(5, 9), { total() / 2 }, { 7.0 }, expect = false),
                // -15 > -5
                case(InequalityType.GREATER_THAN, Solution(-15, -10, -5), minVal, maxVal, expect = false),
                // 20_000 <= 19_999
                case(
                    InequalityType.LESS_THAN_OR_EQUAL,
                    Solution(20_000, 20_000),
                    { tailSum() },
                    { head() - 1 },
                    expect = false
                )
                // endregion
            ) { it.run() }   // each Case carries its own runnable test
        }
    }
})
