/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package plugins

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.util.*

/**
 * A verification task that enforces consistency of the `keen.java.default` property across the **root
 * `gradle.properties`** and **`build-logic/gradle.properties`** files.
 *
 * ### Why this matters
 * - Ensures a single source of truth for the default Java version.
 * - Prevents mismatched toolchains between the main build and the build-logic project.
 * - Fails early during the build (`assemble` depends on this task).
 *
 * ### Behavior
 * - Reads the property from both files.
 * - Validates that it exists, is an integer, and lies within a supported range.
 * - Fails if the values differ between the two files.
 * - Provides guidance on how to fix issues.
 *
 * ### Notes
 * - Marked with [DisableCachingByDefault] because it is purely a verification task (outputs cannot be cached
 *   meaningfully).
 * - Configuration-cache friendly: no lambdas capturing outer scope.
 */
@DisableCachingByDefault(because = "Verification only, no cacheable outputs")
abstract class VerifyKeenJavaDefaultTask : DefaultTask() {

    /**
     * Path to the root `gradle.properties` file.
     *
     * Marked with [InputFile] so Gradle tracks changes, and [PathSensitive] to allow relocation of the project.
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val rootProps: RegularFileProperty

    /**
     * Path to the `build-logic/gradle.properties` file.
     *
     * Same annotations as [rootProps] for correctness and reproducibility.
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val buildLogicProps: RegularFileProperty

    /**
     * The property key to verify.
     *
     * Defaults to `"keen.java.default"`, but can be overridden to reuse this task for other properties.
     */
    @get:Input
    abstract val propertyKey: Property<String>

    /**
     * Skip flag, opt-in via `-PskipJavaDefaultCheck=true`.
     *
     * Useful for CI experiments or when testing locally.
     */
    @get:Input
    abstract val skipCheck: Property<Boolean>

    init {
        // Default values, so consumers don’t need to wire them explicitly.
        propertyKey.convention("keen.java.default")
        skipCheck.convention(false)
    }

    /**
     * Main verification logic.
     *
     * Runs when the task executes; short and readable thanks to helpers.
     */
    @TaskAction
    fun check() {
        if (skipCheck.get()) {
            logger.lifecycle("Skipping ${propertyKey.get()} consistency check (-PskipJavaDefaultCheck=true).")
            return
        }

        val key = propertyKey.get()
        val rootFile  = rootProps.get().asFile
        val logicFile = buildLogicProps.get().asFile

        // Ensure files exist before reading
        requireFileExists(rootFile,  key)
        requireFileExists(logicFile, key)

        // Load values (null → missing)
        val rootVal  = readProp(rootFile, key) ?: failMissing(key, rootFile)
        val logicVal = readProp(logicFile, key) ?: failMissing(key, logicFile)

        // Parse and validate both values
        val rootInt  = parseMajor(key, rootVal)
        val logicInt = parseMajor(key, logicVal)

        // Fail if inconsistent
        if (rootInt != logicInt) {
            throw GradleException(
                "Inconsistent '$key': root=$rootInt, build-logic=$logicInt.\n\n" +
                        guidance(key)
            )
        }

        logger.lifecycle("✔ '$key' = $rootInt (root & build-logic match).")
    }

    // --- Helpers -------------------------------------------------------------------------

    /**
     * Reads a property from a `.properties` file, returning null if not found or blank.
     */
    private fun readProp(file: File, key: String): String? =
        Properties().apply { file.inputStream().use(::load) }
            .getProperty(key)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    /**
     * Parses a Java major version, enforcing that it is an integer in a safe range.
     */
    private fun parseMajor(key: String, raw: String): Int {
        val v = raw.toIntOrNull() ?: throw GradleException(
            "Invalid $key value '$raw' (must be an integer, e.g., 17, 21, 22).\n\n" +
                    guidance(key)
        )
        require(v in 8..99) {
            "Unsupported $key value: $v. Expected major version in [8..99]."
        }
        return v
    }

    /**
     * Ensures a properties file exists, failing with clear guidance if not.
     */
    private fun requireFileExists(file: File, key: String) {
        if (!file.isFile) {
            throw GradleException(
                "Properties file not found: ${file.relative(project.rootDir)}.\n\n" +
                        guidance(key)
            )
        }
    }

    /**
     * Fails when a property is missing from a file.
     */
    private fun failMissing(key: String, missing: File): Nothing =
        throw GradleException(
            "Missing '$key' in ${missing.relative(project.rootDir)}.\n\n" +
                    guidance(key)
        )

    /**
     * Provides user-friendly instructions for how to define the property.
     */
    private fun guidance(key: String): String = """
        Define the property in BOTH files with the same value. Examples:
          • Root:        ${rootProps.get().asFile.relative(project.rootDir)}
                $key=22
          • Build-logic: ${buildLogicProps.get().asFile.relative(project.rootDir)}
                $key=22

        You can also set it per-invocation:
          ./gradlew assemble -P$key=22
    """.trimIndent()

    /**
     * Render a path relative to the project root for nicer log messages.
     */
    private fun File.relative(base: File): String =
        base.toPath().relativize(this.toPath()).toString()
}
