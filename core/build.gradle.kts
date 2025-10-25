/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

plugins {
    id("kalm.library")
    id("kalm.jvm")
    alias { libs.plugins.detekt }
}

val detektFormatting = libs.detekt.formatting.get().apply { "$group:$module:$version" }
val kotestBundle = libs.bundles.kotest

dependencies {
    detektPlugins(detektFormatting)
    testImplementation(kotestBundle)
}
