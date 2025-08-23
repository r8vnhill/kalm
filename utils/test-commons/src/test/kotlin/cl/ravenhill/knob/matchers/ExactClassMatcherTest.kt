/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.matchers

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.should
import io.kotest.matchers.shouldNot
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.char
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.pair
import io.kotest.property.arbitrary.set
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

class ExactClassMatcherTest : FreeSpec({

    "A haveSameClassAs matcher" - {
        "when comparing two instances of the same runtime class" - {
            "should pass (PBT)" {
                checkAll(sameClassPairs) { (a, b) ->
                    a shouldHaveSameClassAs b
                }
            }
        }

        "when comparing two instances of different runtime classes" - {
            "should fail" {
                checkAll(differentClassPairs) { (a, b) ->
                    a shouldNotHaveSameClassAs b
                    // And positive form should throw
                    shouldThrow<AssertionError> {
                        a shouldHaveSameClassAs b
                    }
                }
            }
        }

        "when the actual is null" - {
            "should not match any non-null other" {
                checkAll(Arb.choice(gens)) { a ->
                    null shouldNot haveSameClassAs(a)
                    // And the positive form should throw
                    shouldThrow<AssertionError> {
                        null should haveSameClassAs(a)
                    }
                }
            }
        }
    }

    "A haveClass(kClass) matcher" - {
        "when provided the exact runtime KClass" - {
            "should pass for any non-null instance (PBT)" {
                checkAll(Arb.choice(gens)) { a ->
                    a shouldHaveClass a::class
                }
            }
        }

        "when provided a different KClass" - {
            "should fail (PBT)" {
                // Pick two different instances and assert a mismatch.
                checkAll(differentClassPairs) { (a, b) ->
                    a shouldNotHaveClass b::class
                    // And positive form should throw
                    shouldThrow<AssertionError> {
                        a shouldHaveClass b::class
                    }
                }
            }
        }

        "when actual is null" - {
            "should fail for any expected KClass (unit)" {
                checkAll(Arb.choice(gens)) { k ->
                    null shouldNotHaveClass k::class
                    // And positive form should throw
                    shouldThrow<AssertionError> {
                        null shouldHaveClass k::class
                    }
                }
            }
        }
    }

    "A haveClass<T>() reified matcher" - {

        "when T matches the runtime class" - {

            "should pass for representative types (DDT)" - {

                withData(
                    nameFn = { "value: ${it.first} (${it.first::class.simpleName})" },
                    1 to Int::class,
                    1L to Long::class,
                    1.0 to Double::class,
                    "hi" to String::class,
                    // note: reified check uses the exact class; this list is an ArrayList
                    listOf(1, 2) to List::class,
                    Foo(3) to Foo::class,
                    Bar(4) to Bar::class,
                ) { (value, k) ->
                    when (k) {
                        Int::class -> value.shouldHaveClass<Int>()
                        Long::class -> value.shouldHaveClass<Long>()
                        Double::class -> value.shouldHaveClass<Double>()
                        String::class -> value.shouldHaveClass<String>()
                        Foo::class -> value.shouldHaveClass<Foo>()
                        Bar::class -> value.shouldHaveClass<Bar>()
                        // Use kClass overload for collections
                        else -> value should haveClass(value::class)
                    }
                }
            }
        }

        "when T does not match the runtime class" - {
            "should fail for clear mismatches (DDT)" - {
                data class TestCase(val testName: String, val first: Any, val second: Any)

                withData(
                    nameFn = { "value: ${it.first} (${it.first::class.simpleName})" },
                    TestCase("Int vs String", 1, "x"),
                    TestCase("Foo vs Bar", Foo(1), Bar(1)),
                    TestCase("String vs Int", "x", 1),
                ) { (_, a, b) ->
                    when (b) {
                        is String -> a.shouldNotHaveClass<String>()
                        is Int -> a.shouldNotHaveClass<Int>()
                        is Foo -> a.shouldNotHaveClass<Foo>()
                        is Bar -> a.shouldNotHaveClass<Bar>()
                        else -> a shouldNotHaveClass b::class
                    }
                }
            }
        }
    }
})

/**
 * Test fixture used to verify exact-class matching semantics.
 *
 * @property x Sample payload; only used to construct instances.
 */
private data class Foo(val x: Int)

/**
 * Companion test fixture to create a distinct runtime type for negative cases.
 *
 * @property y Sample payload; only used to construct instances.
 */
private data class Bar(val y: Int)


/* ---------- Base pools ---------- */

/**
 * Builds an [Arb] that always emits a concrete [ArrayList] of elements from [gen].
 *
 * Unlike `Arb.list`, which may return different runtime classes depending on size (e.g., `EmptyList`, `SingletonList`,
 * `ArrayList`), this helper normalizes the result to a stable `java.util.ArrayList`.
 * This is useful for property tests that assert **exact** runtime class equality.
 *
 * @param gen Element generator.
 * @param size Inclusive [IntRange] for the list size (default `0..3`).
 * @return An [Arb] of [ArrayList] with a stable concrete type.
 */
private fun <T> stableArrayList(gen: Arb<T>, size: IntRange = 0..3): Arb<ArrayList<T>> =
    Arb.list(gen, size).map { ArrayList(it) }

/**
 * Builds an [Arb] that always emits a concrete [LinkedHashSet] of elements from [gen].
 *
 * Normalizes the set’s runtime type to `java.util.LinkedHashSet` (instead of whatever
 * `Arb.set` might produce) to keep **exact-class** assertions stable in property tests.
 * Uses `map` so shrinking behavior is preserved. The default small [size] keeps tests fast.
 *
 * @param gen  Element generator.
 * @param size Inclusive size range for the set (default `0..3`).
 * @return An [Arb] of [LinkedHashSet] with a stable concrete type and iteration order.
 */
private fun <T> stableLinkedHashSet(gen: Arb<T>, size: IntRange = 0..3): Arb<LinkedHashSet<T>> =
    Arb.set(gen, size).map { it.toCollection(LinkedHashSet()) }

/**
 * Pool of representative [Arb] generators for property-based tests.
 *
 * This list includes primitive types, text, and stable concrete collections, plus custom domain classes ([Foo], [Bar]).
 * It provides variety for exercising matchers across different runtime classes while avoiding pitfalls like `EmptyList`
 * vs `SingletonList` by using helpers such as [stableArrayList] and [stableLinkedHashSet].
 */
private val gens: List<Arb<Any>> = listOf(
    Arb.int(),
    Arb.long(),
    Arb.double(),
    Arb.boolean(),
    Arb.char(),
    Arb.string(),
    stableArrayList(Arb.int(), 0..3),
    stableLinkedHashSet(Arb.int(), 0..3),
    Arb.int().map(::Foo),
    Arb.int().map(::Bar),
)

/** Builds an [Arb] that always produces pairs of values with the **same runtime class**.
 *
 * Wraps [Arb.Companion.pair] using the same element generator on both sides, so both values are guaranteed to come from
 * the same distribution and thus share the same class.
 *
 * @param gen Element generator to use for both pair components.
 * @return An [Arb] of [Pair] where both elements are non-null and of the same runtime type.
 */
private fun <T : Any> sameClassPairOf(gen: Arb<T>): Arb<Pair<Any, Any>> =
    Arb.pair(gen, gen)

/**
 * Pool of pair generators where both values share the **exact same runtime class**.
 *
 * @see sameClassPairOf
 */
private val sameClassPairs: Arb<Pair<Any, Any>> = Arb.choice(
    sameClassPairOf(Arb.int()),
    sameClassPairOf(Arb.long()),
    sameClassPairOf(Arb.double()),
    sameClassPairOf(Arb.boolean()),
    sameClassPairOf(Arb.char()),
    sameClassPairOf(Arb.string()),
    sameClassPairOf(stableArrayList(Arb.int())),
    sameClassPairOf(stableLinkedHashSet(Arb.int())),
    sameClassPairOf(Arb.int().map(::Foo)),
    sameClassPairOf(Arb.int().map(::Bar)),
)

/**
 * Generator of index pairs pointing to two **different generators** in [gens].
 *
 * Ensures that the selected indices never collide, so the resulting values will come from generators of distinct
 * runtime classes.
 * This avoids the need for filtering or discarding samples, which improves test performance and shrinkability.
 *
 * The trick: sample `i` from `[0, n)` and `j0` from `[0, n-1)`. If `j0 >= i`, shift `j0` by `+1` to guarantee `j != i`.
 *
 * @throws IllegalArgumentException if [gens] has fewer than 2 entries.
 */
private val differentGenIndices: Arb<Pair<Int, Int>> = run {
    val n = gens.lastIndex + 1
    require(n >= 2) { "Need at least two generators to build differentClassPairs." }
    Arb.bind(Arb.int(0..<n), Arb.int(0..<(n - 1))) { i, j0 ->
        val j = if (j0 >= i) j0 + 1 else j0
        i to j
    }
}

/**
 * Generator of pairs of values coming from **different runtime classes**.
 *
 * Relies on [differentGenIndices] to choose two distinct generator indices from [gens], then binds them into a pair.
 * This guarantees that both elements of the pair originate from different distributions, so `a::class != b::class`.
 *
 * @see gens
 * @see differentGenIndices
 */
private val differentClassPairs: Arb<Pair<Any, Any>> = differentGenIndices.flatMap { (i, j) ->
    Arb.pair(gens[i], gens[j])
}
