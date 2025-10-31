/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

plugins {
    id("kalm.library")
    id("kalm.jvm")
    alias { libs.plugins.detekt }
}

val kotestBundle = libs.bundles.kotest

dependencies {
    detektPlugins(libs.detekt.formatting)
    testImplementation(kotestBundle)
}
