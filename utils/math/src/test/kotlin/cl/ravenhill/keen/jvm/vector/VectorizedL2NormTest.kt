/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.jvm.vector

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import jdk.incubator.vector.DoubleVector
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.properties.Delegates

class VectorizedL2NormTest : FreeSpec({

    lateinit var vectorized: VectorizedL2Norm
    var lanes by Delegates.notNull<Int>()

    beforeSpec {
        val species = DoubleVector.SPECIES_PREFERRED
        lanes = species.length()
        vectorized = VectorizedL2Norm(species, lanes)
    }

    // simple scalar reference
    fun DoubleArray.refSquared(): Double = ScalarL2Norm.run { squaredL2Norm() }
    fun DoubleArray.refNorm(): Double = ScalarL2Norm.run { l2Norm() }

    // close-check helper (absolute + relative)
    fun assertClose(actual: Double, expected: Double, absTol: Double = 1e-12, relTol: Double = 1e-12) {
        val scale = max(1.0, max(abs(actual), abs(expected)))
        val ok = abs(actual - expected) <= max(absTol, relTol * scale)
        if (!ok) {
            error("Not close:\n  actual=$actual\n  expected=$expected\n  absTol=$absTol relTol=$relTol (scale=$scale)")
        }
    }

    "a vectorized L2 norm" - {
        "when the input is empty" - {
            "should return 0.0 for squaredL2Norm and l2Norm" {
                val a = doubleArrayOf()
                val vsq = vectorized.run { a.squaredL2Norm() }
                val v = vectorized.run { a.l2Norm() }
                vsq shouldBe 0.0
                v shouldBe 0.0
            }
        }

        "when the input length is not a multiple of the SIMD lanes" - {
            TODO()
            "should match the scalar reference" {
                // Pick a size that is definitely not a multiple of lanes
                val n = if (lanes == 1) 7 else (3 * lanes + 1)
                val a = DoubleArray(n) { i -> (i % 5 - 2).toDouble() * 0.125 } // some small varied values
                val vsq = vectorized.run { a.squaredL2Norm() }
                val rsq = a.refSquared()
                assertClose(vsq, rsq)
                assertClose(vectorized.run { a.l2Norm() }, a.refNorm())
            }
        }

        "when comparing against known small examples" - {
            TODO()
//            "should equal the scalar reference for zeros and mixed signs" {
//                val cases = listOf(
//                    doubleArrayOf(0.0, 0.0, 0.0, 0.0),
//                    doubleArrayOf(1.0, -1.0, 2.0, -2.0),
//                    doubleArrayOf(1e-12, -2e-12, 3e-12),
//                    doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0),
//                )
//                for (a in cases) {
//                    assertClose(vectorized.run { a.squaredL2Norm() }, a.refSquared())
//                    assertClose(vectorized.run { a.l2Norm() }, a.refNorm())
//                }
//            }
        }

        "when comparing random finite arrays" - {
            TODO()
//            "should match the scalar squaredL2Norm (property-based)" {
//                checkAll(
//                    Arb.int(0..257),
//                    Arb.list(Arb.double(-1e6..1e6), 0..257)
//                ) { _, list ->
//                    val a = list.filter { it.isFinite() }.toDoubleArray()
//                    val vsq = vectorized.run { a.squaredL2Norm() }
//                    val rsq = a.refSquared()
//                    assertClose(vsq, rsq)
//                }
//            }
//
//            "should match the scalar l2Norm (property-based)" {
//                checkAll(
//                    Arb.int(0..257),
//                    Arb.list(Arb.double(-1e6..1e6), 0..257)
//                ) { _, list ->
//                    val a = list.filter { it.isFinite() }.toDoubleArray()
//                    val v = vectorized.run { a.l2Norm() }
//                    val r = a.refNorm()
//                    assertClose(v, r)
//                }
//            }
        }

        "when stressing tails and lane boundaries" - {
            TODO()
//            "should match the scalar reference for sizes around multiples of lanes" {
//                // Test sizes: k*lanes + delta, with small deltas around 0
//                val deltas = Exhaustive.of(-2, -1, 0, 1, 2)
//                checkAll(Exhaustive.of(0, 1, 2, 3, 4, 5), deltas) { k, d ->
//                    val n = (k * lanes + d).coerceAtLeast(0)
//                    val a = DoubleArray(n) { i -> (i - n / 2).toDouble() * 1e-3 }
//                    val vsq = vectorized.run { a.squaredL2Norm() }
//                    val rsq = a.refSquared()
//                    assertClose(vsq, rsq)
//                }
//            }
        }
    }
})

private object ScalarL2Norm : L2Norm {
    override fun DoubleArray.squaredL2Norm(): Double {
        var s = 0.0
        var i = 0
        while (i < size) {
            val x = this[i]
            s = Math.fma(x, x, s) // s += x*x (FMA aids both accuracy and throughput)
            i++
        }
        return s
    }

    override fun DoubleArray.l2Norm(): Double = sqrt(this.squaredL2Norm())
}
