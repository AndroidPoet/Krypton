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
    }
}

android {
    namespace = "io.krypton.protocol"
    compileSdk = io.krypton.Configuration.COMPILE_SDK
    defaultConfig { minSdk = io.krypton.Configuration.MIN_SDK }
}
