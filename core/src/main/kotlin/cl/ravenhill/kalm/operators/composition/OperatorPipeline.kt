/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

package cl.ravenhill.kalm.operators.composition

import cl.ravenhill.kalm.operators.ContravariantOperator
import cl.ravenhill.kalm.operators.CovariantOperator
import cl.ravenhill.kalm.operators.InvariantOperator
import cl.ravenhill.knob.repr.Solution

/**
 * Composes a producer and a consumer into a pipeline that produces and processes solutions.
 *
 * This demonstrates a key benefit of variance: a covariant producer can be safely composed
 * with a contravariant consumer because the producer's output type can be made compatible
 * with the consumer's input type.
 *
 * ## Type Safety Through Variance:
 * ```kotlin
 * val dogProducer: CovariantOperator<Solution<Dog>> = ...
 * val animalConsumer: ContravariantOperator<Solution<Animal>> = ...
 * 
 * // Composable! Dog is a subtype of Animal
 * val pipeline = OperatorPipeline(dogProducer, animalConsumer)
 * ```
 *
 * @param T The solution type that flows through the pipeline.
 * @param producer The operator that produces solutions.
 * @param consumer The operator that consumes solutions.
 */
class OperatorPipeline<T : Solution<*>>(
    private val producer: CovariantOperator<T>,
    private val consumer: ContravariantOperator<T>
) {
    /**
     * Executes the pipeline: produce a solution and immediately consume it.
     */
    fun execute() {
        val solution = producer.produce()
        consumer.consume(solution)
    }
    
    /**
     * Executes the pipeline multiple times.
     */
    fun execute(iterations: Int) {
        repeat(iterations) { execute() }
    }
}

/**
 * Creates a pipeline that chains a producer to a consumer.
 */
infix fun <T : Solution<*>> CovariantOperator<T>.then(
    consumer: ContravariantOperator<T>
): OperatorPipeline<T> = OperatorPipeline(this, consumer)
