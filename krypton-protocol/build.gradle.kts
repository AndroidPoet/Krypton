@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import io.krypton.Configuration
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation

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

        // Apple shared TEST source set — real-FFI tests run on every Apple target.
        val appleTest by creating {
            dependsOn(commonTest.get())
        }

        // Connect each Apple target's main/test source sets to appleMain/appleTest
        listOf("iosX64", "iosArm64", "iosSimulatorArm64",
               "macosX64", "macosArm64").forEach { target ->
            getByName("${target}Main").dependsOn(appleMain)
            getByName("${target}Test").dependsOn(appleTest)
        }
    }

    // ── Cinterop: link libsignal_ffi on every Apple target ───────────────
    // Each target links its OWN-arch static lib from libs/apple/<arch>/ and the
    // `.def`'s `staticLibraries` directive EMBEDS that .a into the produced klib,
    // so it ships inside the published artifact — consumers link it with no setup.
    // The per-arch .a files are built fresh in CI (scripts/build-libsignal-ffi.sh,
    // run by .github/workflows/release.yml); they are gitignored, never committed.
    fun KotlinNativeCompilation.configureCInterop(libArchDir: String) {
        cinterops {
            val libsignalFfi by creating {
                defFile(project.file("src/appleMain/cinterop/libsignal_ffi.def"))
                packageName("org.signal.libsignal.ffi")
                compilerOpts("-I${project.projectDir}/libs/apple")
                extraOpts("-libraryPath", "${project.projectDir}/libs/apple/$libArchDir")
            }
        }
    }

    // Every Apple target links its own real per-arch .a (no cross-arch stand-ins).
    macosArm64        { compilations.getByName("main").configureCInterop("macos-arm64") }  // Apple Silicon desktop
    macosX64          { compilations.getByName("main").configureCInterop("macos-x64") }    // Intel desktop
    iosArm64          { compilations.getByName("main").configureCInterop("ios-arm64") }    // device
    iosSimulatorArm64 { compilations.getByName("main").configureCInterop("ios-sim-arm64") } // Apple Silicon sim
    iosX64            { compilations.getByName("main").configureCInterop("ios-sim-x64") }  // Intel sim
}

android {
    namespace = "io.krypton.protocol"
    compileSdk = io.krypton.Configuration.COMPILE_SDK
    defaultConfig { minSdk = io.krypton.Configuration.MIN_SDK }
}

mavenPublishing {
    coordinates(Configuration.GROUP, "krypton-protocol", Configuration.VERSION)
}

// ── libsignal version-skew guard ─────────────────────────────────────────────
// Asserts the Apple/native libsignal_ffi.a (recorded in libs/apple/VERSION) is
// the SAME libsignal version as the JVM/Android Maven coordinates (catalog).
// Mismatched versions can produce incompatible session/wire formats, so an
// iOS user and an Android user could silently fail to talk to each other.
// Wired into `check` so it fails the build instead of failing users.
val verifyLibsignalVersion by tasks.registering {
    group = "verification"
    description = "Fail if the native libsignal_ffi.a version differs from the JVM/Android Maven version."
    val mavenVersion = libs.versions.libsignal.get()
    val versionFile = layout.projectDirectory.file("libs/apple/VERSION").asFile
    // Capture at configuration time so the task has no project reference at execution.
    inputs.property("mavenVersion", mavenVersion)
    doLast {
        if (!versionFile.exists()) {
            logger.warn(
                "verifyLibsignalVersion: ${versionFile.path} not found — native libsignal_ffi.a " +
                    "not present/built. Skipping skew check (Maven version = $mavenVersion)."
            )
            return@doLast
        }
        val ffiVersion = versionFile.readText().trim()
        check(ffiVersion == mavenVersion) {
            "libsignal version skew: JVM/Android Maven = '$mavenVersion' but native libsignal_ffi.a " +
                "(libs/apple/VERSION) = '$ffiVersion'. Rebuild the native .a at '$mavenVersion' via " +
                "scripts/build-libsignal-ffi.sh $mavenVersion, then update libs/apple/VERSION."
        }
        logger.lifecycle("verifyLibsignalVersion: OK — both tracks on libsignal $mavenVersion.")
    }
}

tasks.named("check") { dependsOn(verifyLibsignalVersion) }
