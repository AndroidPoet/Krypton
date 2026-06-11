@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
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
    tvosX64()
    tvosArm64()
    tvosSimulatorArm64()
    linuxX64()
    mingwX64()
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            api(project(":krypton-core"))
            api(project(":krypton-storage"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }

        // Shared source set for JVM + Android where libsignal classes are available
        val commonMain by getting
        val jvmAndAndroidMain by creating {
            dependsOn(commonMain)
            dependencies {
                // libsignal-client is a pure JAR — provides classes for compilation
                // Each platform brings its own runtime artifact (libsignal-client for JVM,
                // libsignal-android for Android) which includes native libs.
                compileOnly(libs.libsignal.client)
            }
        }

        jvmMain {
            dependsOn(jvmAndAndroidMain)
            dependencies {
                implementation(libs.libsignal.client)
            }
        }

        androidMain {
            dependsOn(jvmAndAndroidMain)
            dependencies {
                implementation(libs.libsignal.android)
            }
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        // ── Apple shared source set (iOS, macOS, tvOS, watchOS) ────────
        // Manually defined because the default hierarchy template is disabled
        // due to the explicit jvmAndAndroidMain dependsOn() calls.
        val appleMain by creating {
            dependsOn(commonMain)
            dependencies {
                // libsignal_ffi is linked via cinterop (not a Gradle dependency)
            }
        }

        // Connect each Apple target's main source set to appleMain
        listOf("iosX64", "iosArm64", "iosSimulatorArm64",
               "macosX64", "macosArm64",
               "tvosX64", "tvosArm64", "tvosSimulatorArm64").forEach { target ->
            getByName("${target}Main").dependsOn(appleMain)
        }
    }

    // ── Cinterop: link libsignal_ffi on all Apple platforms ──────────────
    fun KotlinNativeCompilation.configureCInterop() {
        cinterops {
            val libsignalFfi by creating {
                defFile(project.file("src/appleMain/cinterop/libsignal_ffi.def"))
                packageName("org.signal.libsignal.ffi")
                compilerOpts("-I${project.projectDir}/libs/apple")
            }
        }
    }

    // macOS (host architectures)
    macosArm64 { compilations.getByName("main").configureCInterop() }
    macosX64   { compilations.getByName("main").configureCInterop() }

    // iOS targets
    iosArm64          { compilations.getByName("main").configureCInterop() }
    iosX64            { compilations.getByName("main").configureCInterop() }
    iosSimulatorArm64 { compilations.getByName("main").configureCInterop() }

    // tvOS targets
    tvosArm64          { compilations.getByName("main").configureCInterop() }
    tvosX64            { compilations.getByName("main").configureCInterop() }
    tvosSimulatorArm64 { compilations.getByName("main").configureCInterop() }

    // watchOS targets
}

android {
    namespace = "io.krypton.protocol"
    compileSdk = io.krypton.Configuration.COMPILE_SDK
    defaultConfig { minSdk = io.krypton.Configuration.MIN_SDK }
}
