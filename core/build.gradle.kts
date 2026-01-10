/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
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
