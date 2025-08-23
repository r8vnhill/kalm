/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.utils

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FreeSpec
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.doubleArray
import io.kotest.property.arbitrary.positiveInt
import io.kotest.property.checkAll

@OptIn(ExperimentalKotest::class)
class DoubleArraySliceTest : FreeSpec({

    "a DoubleArraySlice" - {
        "when indexing" - {
            "should return the same value as the backing array at (offset + index) [PBT]" {
                TODO()
//                // Generate safe combinations: n>=1, 0<=offset<n, 0<=index<n-offset
//                val sizes = Arb.positiveInt(128)
//                checkAll(
//                    PropTestConfig(iterations = 300),
//                    Arb.doubleArray(),
//                    sizes.flatMap { n ->
//                        // For a given array length, pick an offset within range
//                        Arb.int(0 until n).map { off -> n to off }
//                    },
//                ) { (arr, pair) ->
//                    val (n, off) = pair
//                    val base = if (arr.size >= n) arr.copyOf(n) else {
//                        // ensure the generated array length matches 'n'
//                        DoubleArray(n) { i -> if (i < arr.size) arr[i] else i * 1.0 }
//                    }
//                    val remaining = n - off
//                    val idx = Arb.int(0 until remaining).next()
//                    val slice = DoubleArraySlice(base, off)
//                    slice[idx] shouldBe base[off + idx]
//                }
            }
        }

        "when checking equality" - {
            TODO()
//            "should be reflexive [PBT]" {
//                checkAll(doubleArrayArb(0..64), Arb.int(0..64)) { arr, off ->
//                    val o = off.coerceAtMost(arr.size)
//                    val s = DoubleArraySlice(arr, o)
//                    (s == s).shouldBeTrue()
//                }
//            }
//
//            "should be symmetric for same content arrays and same offset [PBT]" {
//                checkAll(doubleArrayArb(0..64), Arb.int(0..64), Arb.boolean()) { arr, off, copy ->
//                    val o = off.coerceAtMost(arr.size)
//                    val arr2 = if (copy) arr.copyOf() else arr // same content, maybe different reference
//                    val a = DoubleArraySlice(arr, o)
//                    val b = DoubleArraySlice(arr2, o)
//                    (a == b).shouldBeTrue()
//                    (b == a).shouldBeTrue()
//                }
//            }
//
//            "should be transitive for same content arrays [PBT]" {
//                checkAll(doubleArrayArb(0..64), Arb.int(0..64)) { arr, off ->
//                    val o = off.coerceAtMost(arr.size)
//                    val a1 = DoubleArraySlice(arr, o)
//                    val a2 = DoubleArraySlice(arr.copyOf(), o)
//                    val a3 = DoubleArraySlice(arr.copyOf(), o)
//                    (a1 == a2 && a2 == a3).shouldBeTrue()
//                    (a1 == a3).shouldBeTrue()
//                }
//            }
//
//            "should differ if offsets differ [PBT]" {
//                checkAll(doubleArrayArb(1..64), Arb.int(0..63)) { arr, off ->
//                    val o1 = off.coerceAtMost(arr.size - 1)
//                    val o2 = (o1 + 1).coerceAtMost(arr.size)
//                    val a = DoubleArraySlice(arr, o1)
//                    val b = DoubleArraySlice(arr, o2)
//                    (a == b).shouldBeFalse()
//                }
//            }
//
//            "should differ if contents differ [PBT]" {
//                checkAll(doubleArrayArb(1..64), Arb.int(0..64), finiteDoubleArb()) { arr, off, delta ->
//                    val o = off.coerceAtMost(arr.size - 1)
//                    val mutated = arr.copyOf()
//                    mutated[o] = mutated[o] + delta + 1.0 // ensure difference
//                    val a = DoubleArraySlice(arr, o)
//                    val b = DoubleArraySlice(mutated, o)
//                    (a == b).shouldBeFalse()
//                }
//            }
        }

        "when checking hashCode" - {
            TODO()
//            "should match for equal slices (same content, same offset) [PBT]" {
//                checkAll(doubleArrayArb(0..64), Arb.int(0..64)) { arr, off ->
//                    val o = off.coerceAtMost(arr.size)
//                    val a = DoubleArraySlice(arr, o)
//                    val b = DoubleArraySlice(arr.copyOf(), o)
//                    (a == b).shouldBeTrue()
//                    a.hashCode() shouldBe b.hashCode()
//                }
//            }
        }

        "when comparing special values (NaN & infinities) [DDT]" - {
            TODO()
            // contentEquals uses '==' under the hood; NaN != NaN -> arrays with NaN at same index are not equal
//            withData(
//                nameFn = { (x, y) -> "arr0=$x arr1=$y" },
//                listOf(
//                    doubleArrayOf(Double.NaN) to doubleArrayOf(Double.NaN),
//                    doubleArrayOf(Double.POSITIVE_INFINITY) to doubleArrayOf(Double.NEGATIVE_INFINITY),
//                    doubleArrayOf(1.0, Double.NaN) to doubleArrayOf(1.0, Double.NaN)
//                )
//            ) { (a0, a1) ->
//                val s0 = DoubleArraySlice(a0, 0)
//                val s1 = DoubleArraySlice(a1, 0)
//                (s0 == s1).shouldBeFalse()
//            }
        }

        "when using equals with different types [unit]" - {
            TODO()
//            "should return false" {
//                val s = DoubleArraySlice(doubleArrayOf(1.0), 0)
//                (s.equals("not a slice")).shouldBeFalse()
//            }
        }
    }
})
