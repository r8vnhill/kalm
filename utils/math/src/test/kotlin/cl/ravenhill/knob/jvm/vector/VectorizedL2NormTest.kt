/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.jvm.vector

import cl.ravenhill.knob.matchers.shouldBeCloseToUlps
import io.kotest.core.spec.style.FreeSpec
import io.kotest.datatest.withData
import jdk.incubator.vector.DoubleVector
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

    "a vectorized L2 norm" - {
        "when the input is empty" - {
            "should return 0.0 for squaredL2Norm and l2Norm" {
                val a = doubleArrayOf()
                val vsq = vectorized.run { a.squaredL2Norm() }
                val v = vectorized.run { a.l2Norm() }
                vsq.shouldBeCloseToUlps(0.0)
                v.shouldBeCloseToUlps(0.0)
            }
        }

        "when the input length is not a multiple of the SIMD lanes" - {
            "should match the scalar reference" {
                // Pick a size that is definitely not a multiple of lanes
                val n = if (lanes == 1) 7 else (3 * lanes + 1)
                val a = DoubleArray(n) { i -> (i % 5 - 2).toDouble() * 0.125 } // some small varied values
                val vsq = vectorized.run { a.squaredL2Norm() }
                val rsq = a.refSquared()
                vsq shouldBeCloseToUlps rsq
                vectorized.run { a.l2Norm() } shouldBeCloseToUlps a.refNorm()
            }
        }

        "when comparing against known small examples" - {
            "should equal the scalar reference for" - {
                withData(
                    doubleArrayOf(0.0, 0.0, 0.0, 0.0),
                    doubleArrayOf(1.0, -1.0, 2.0, -2.0),
                    doubleArrayOf(1e-12, -2e-12, 3e-12),
                    doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0),
                ) {
                    vectorized.run {
                        it.squaredL2Norm() shouldBeCloseToUlps it.refSquared()
                        it.l2Norm() shouldBeCloseToUlps it.refNorm()
                    }
                }
            }
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

/**
 * Scalar implementation of the [L2Norm] interface for [DoubleArray].
 *
 * This object computes the squared L2 norm and L2 norm using a straightforward element-wise accumulation.
 * It uses [Math.fma] for the accumulation step, which improves both numerical accuracy and performance by combining
 * multiplication and addition into a single operation.
 */
private object ScalarL2Norm : L2Norm {

    override fun DoubleArray.squaredL2Norm(): Double {
        var s = 0.0
        var i = 0
        while (i < size) {
            val x = this[i]
            s = Math.fma(x, x, s) // fused multiply–add: s += x * x
            i++
        }
        return s
    }

    override fun DoubleArray.l2Norm(): Double = sqrt(this.squaredL2Norm())
}
