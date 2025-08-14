/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.util.size

/**
 * Marks an API that allows unsafe creation of [Size] instances without validation.
 *
 * This annotation signals that the annotated function, property, or constructor can bypass the usual validation logic
 * when creating [Size] objects.
 * Such bypasses should only be used in controlled scenarios where the caller fully guarantees that the provided input
 * is valid.
 *
 * The annotation requires an explicit opt-in by the caller and emits a compiler **warning** when used without it.
 */
@RequiresOptIn(
    message = "This API allows unsafe creation of Size instances without validation. " +
            "Use only when you fully control the input.",
    level = RequiresOptIn.Level.WARNING
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR
)
public annotation class UnsafeSizeCreation
