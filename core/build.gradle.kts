/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

plugins {
    id("kalm.library")
    id("kalm.jvm")
    id("kalm.detekt-redmadrobot")
}

dependencies {
    testImplementation(libs.bundles.kotest)
}
