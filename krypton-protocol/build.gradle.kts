@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

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
    watchosX64()
    watchosArm64()
    watchosSimulatorArm64()
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

        jvmMain.dependencies {
            // libsignal-client provides the real JNI bridge for desktop JVM
            implementation("org.signal:libsignal-client:0.86.5")
        }

        androidMain.dependencies {
            // For Android, libsignal provides native .so via AAR or Maven dependency
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "io.krypton.protocol"
    compileSdk = io.krypton.Configuration.COMPILE_SDK
    defaultConfig { minSdk = io.krypton.Configuration.MIN_SDK }
}
