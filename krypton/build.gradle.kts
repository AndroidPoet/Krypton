@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import io.krypton.Configuration

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.vanniktech.publish)
}

kotlin {
    explicitApi()
    jvmToolchain(17)

    androidTarget { publishLibraryVariants("release") }
    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    macosX64()
    macosArm64()
    linuxX64()
    mingwX64()
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            // The real, working surface — exposed transitively to consumers.
            api(project(":krypton-core"))
            api(project(":krypton-storage"))
            api(project(":krypton-protocol"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "io.krypton"
    compileSdk = io.krypton.Configuration.COMPILE_SDK
    defaultConfig { minSdk = io.krypton.Configuration.MIN_SDK }
}

// ── Single-artifact coordinates: io.krypton:krypton:<version> ────────────────
// This module is the ONE dependency consumers add. It re-exports the full real
// public API (core + storage + protocol) via `api(...)`, so a single dependency
// line works on every KMP target — the re-exported modules resolve transitively
// because they are published under the same group. The stub modules
// (sealed-sender, zkgroup, double-ratchet) are intentionally NOT re-exported.
//
// vanniktech maps the KMP root publication ("kotlinMultiplatform") to this clean
// artifact id automatically, signs releases (RELEASE_SIGNING_ENABLED), reads POM
// metadata from gradle.properties, and targets the Central Portal (SONATYPE_HOST).
mavenPublishing {
    coordinates(Configuration.GROUP, "krypton", Configuration.VERSION)
}
