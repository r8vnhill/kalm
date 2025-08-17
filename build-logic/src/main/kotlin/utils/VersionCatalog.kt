/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package utils

import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.ExtensionContainer

internal class VersionCatalog(extensions: ExtensionContainer) {
    val libs = extensions
        .getByType(VersionCatalogsExtension::class.java)
        .named("libs")
}
