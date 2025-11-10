/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Keeps version properties that mirror the version catalog aligned with the TOML source.
 *
 * Properties that are missing from the catalog still trigger a warning so maintainers can reconcile
 * the duplication, while catalog updates automatically flow back into `gradle.properties`.
 */
abstract class SyncVersionPropertiesTask : DefaultTask() {

    @get:Input
    abstract val propertyMappings: MapProperty<String, String>

    @get:InputFile
    abstract val versionCatalogFile: RegularFileProperty

    @get:InputFile
    abstract val propertiesFile: RegularFileProperty

    init {
        group = "versioning"
        description =
            "Keeps property-defined versions in sync with the libs catalog and warns when aliases are missing."
    }

    @TaskAction
    fun syncProperties() {
        val mappings = propertyMappings.orNull ?: emptyMap()
        if (mappings.isEmpty()) {
            logger.lifecycle("No version property mappings configured, skipping sync.")
            return
        }

        val catalogFile = versionCatalogFile.asFile.get()
        if (!catalogFile.exists()) {
            throw GradleException("Version catalog '${catalogFile.path}' is missing.")
        }
        val catalogVersions = catalogFile.parseVersionCatalog()

        val propertyFile = propertiesFile.asFile.get()
        if (!propertyFile.exists()) {
            throw GradleException("Properties file '${propertyFile.path}' is missing.")
        }

        val snapshot = propertyFile.readPropertySnapshot()
        var changed = false

        mappings.forEach { (propertyKey, catalogAlias) ->
            val aliasVersion = catalogVersions[catalogAlias]
            val lineIndex = snapshot.lines.indexOfFirst { matchesPropertyKey(it, propertyKey) }

            if (aliasVersion == null) {
                if (lineIndex >= 0) {
                    logger.warn(
                        "Property '$propertyKey' is configured but version catalog alias '$catalogAlias' is missing."
                    )
                }
                return@forEach
            }

            val updatedLine = if (lineIndex >= 0) {
                rewriteLineWithValue(snapshot.lines[lineIndex], aliasVersion)
            } else {
                "$propertyKey=$aliasVersion"
            }

            if (lineIndex >= 0) {
                if (snapshot.lines[lineIndex] != updatedLine) {
                    snapshot.lines[lineIndex] = updatedLine
                    changed = true
                    logger.lifecycle(
                        "Updated '$propertyKey' to match libs alias '$catalogAlias' ($aliasVersion)."
                    )
                }
            } else {
                snapshot.lines.add(updatedLine)
                changed = true
                logger.lifecycle(
                    "Added '$propertyKey' from libs alias '$catalogAlias' ($aliasVersion)."
                )
            }
        }

        if (changed) {
            propertyFile.writeText(
                snapshot.lines.joinToString(
                    separator = snapshot.lineSeparator,
                    postfix = snapshot.lineSeparator
                )
            )
            logger.lifecycle("Synchronized version properties with libs.versions.toml.")
        } else {
            logger.lifecycle("Version properties already match the version catalog.")
        }
    }
}

private data class PropertyFileSnapshot(
    val lines: MutableList<String>,
    val lineSeparator: String
)

private fun File.readPropertySnapshot(): PropertyFileSnapshot {
    val content = readText()
    val lineSeparator = detectLineSeparator(content)
    val lines = if (content.isEmpty()) {
        mutableListOf()
    } else {
        content.split(Regex("\\r\\n|\\n|\\r")).toMutableList()
    }
    return PropertyFileSnapshot(lines = lines, lineSeparator = lineSeparator)
}

private fun File.parseVersionCatalog(): Map<String, String> {
    val versions = mutableMapOf<String, String>()
    var inVersionsSection = false
    forEachLine { line ->
        val trimmed = line.trim()
        if (trimmed.startsWith("[")) {
            inVersionsSection = trimmed == "[versions]"
            return@forEachLine
        }

        if (!inVersionsSection || trimmed.isEmpty() || trimmed.startsWith("#")) {
            return@forEachLine
        }

        val index = trimmed.indexOf('=')
        if (index <= 0) {
            return@forEachLine
        }

        val key = trimmed.substring(0, index).trim()
        val rawValue = trimmed.substring(index + 1).trim()
        if (rawValue.isEmpty()) {
            return@forEachLine
        }

        val valueWithoutComment = rawValue.substringBefore("#").trim()
        val cleanedValue = valueWithoutComment.trim().trim('"')
        versions[key] = cleanedValue
    }
    return versions
}

private fun rewriteLineWithValue(original: String, aliasVersion: String): String {
    val equalsIndex = original.indexOf('=')
    if (equalsIndex < 0) {
        return "$original=$aliasVersion"
    }
    val left = original.substring(0, equalsIndex)
    val afterEquals = original.substring(equalsIndex + 1)
    val commentIndex = afterEquals.indexOf('#')
    val comment = if (commentIndex >= 0) afterEquals.substring(commentIndex).trimStart() else ""
    val spacer = if (comment.isNotEmpty()) " " else ""
    return "$left=$aliasVersion$spacer$comment"
}

private fun matchesPropertyKey(line: String, key: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.startsWith("#") || trimmed.isEmpty()) {
        return false
    }
    val eqIndex = trimmed.indexOf('=')
    if (eqIndex <= 0) {
        return false
    }
    val currentKey = trimmed.substring(0, eqIndex).trim()
    return currentKey == key
}

private fun detectLineSeparator(text: String): String = when {
    text.contains("\r\n") -> "\r\n"
    text.contains("\n") -> "\n"
    text.contains("\r") -> "\r"
    else -> System.lineSeparator()
}
