@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    `maven-publish`
}

// ── Single-artifact coordinates: io.krypton:krypton:<version> ────────────────
// This module is the ONE dependency consumers add. It re-exports the full
// real public API (core + storage + protocol) via `api(...)`, so a single
// dependency line works on every KMP target. The stub modules
// (sealed-sender, zkgroup, double-ratchet) are intentionally NOT re-exported.
group = io.krypton.Configuration.GROUP
version = io.krypton.Configuration.VERSION

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
    tvosX64()
    tvosArm64()
    tvosSimulatorArm64()
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

// The Kotlin Multiplatform plugin creates one root publication ("kotlinMultiplatform")
// plus a per-target publication. Rename the root coordinate to the clean `krypton`
// artifact id so consumers depend on a single `io.krypton:krypton`.
publishing {
    publications.withType<MavenPublication>().configureEach {
        if (name == "kotlinMultiplatform") {
            artifactId = "krypton"
        }
    }
    repositories {
        mavenLocal()
    }
}
