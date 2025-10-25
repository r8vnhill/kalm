/*
 * Tests for Solution representation.
 */

package cl.ravenhill.knob.repr

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import cl.ravenhill.knob.utils.size.SizeError
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.equality.shouldBeEqualToComparingFields
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class SolutionTest : FreeSpec({

    "construction via of(head, tail) builds non-empty" {
        val s = Solution.of(1, 2, 3)
        s.toNonEmptyList() shouldBe nonEmptyListOf(1, 2, 3)
    }

    "construction via invoke(NonEmptyList) keeps values" {
        val nel = nonEmptyListOf("a", "b")
        val s = Solution(nel)
        s.toNonEmptyList() shouldBe nel
    }

    "fromValidatedList returns Right for non-empty and Left for empty" {
        val right = Solution.fromValidatedList(listOf(42))
        right.isRight().shouldBeTrue()

        val left = Solution.fromValidatedList(emptyList<Int>())
        left.isLeft().shouldBeTrue()
        left.swap().getOrNull().shouldBeInstanceOf<SizeError.StrictlyPositiveExpected>()
    }

    "fromListOrNull returns Solution or null" {
        Solution.fromListOrNull(listOf(1)).shouldNotBeNull()
        Solution.fromListOrNull(emptyList<Int>()).shouldBeNull()
    }

    "fromListOrThrow returns Solution or throws" {
        Solution.fromListOrThrow(listOf("x")).toNonEmptyList() shouldBe nonEmptyListOf("x")
        shouldThrow<SizeError.StrictlyPositiveExpected> {
            Solution.fromListOrThrow(emptyList<String>())
        }
    }

    "fromIterableOrNull and fromSequenceOrNull respect emptiness" {
        Solution.fromIterableOrNull(listOf(1, 2)).shouldNotBeNull()
        Solution.fromIterableOrNull(emptyList<Int>()).shouldBeNull()

        Solution.fromSequenceOrNull(sequenceOf(1, 2)).shouldNotBeNull()
        Solution.fromSequenceOrNull(emptySequence<Int>()).shouldBeNull()
    }

    "equals compares by values against List and Solution" {
        val s1 = Solution.of(1, 2)
        val s2 = Solution.of(1, 2)
        val l = listOf(1, 2)

        // symmetry with List
        (s1 == l) shouldBe true
        (l == s1) shouldBe true

        // equality with another Solution
        (s1 == s2) shouldBe true
        (s1 != Solution.of(1, 3)) shouldBe true
    }

    "hashCode aligns with list hashCode for equal contents" {
        val s = Solution.of(10, 20, 30)
        s.hashCode() shouldBe listOf(10, 20, 30).hashCode()
    }

    "toString renders as Solution[...]" {
        Solution.of(1, 2, 3).toString() shouldBe "Solution[1, 2, 3]"
    }
})
