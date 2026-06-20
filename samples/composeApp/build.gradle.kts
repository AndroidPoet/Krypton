import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(17)

    jvm("desktop")

    // Bring-your-own-libsignal: Krypton ships no Signal binary, so a consumer (this
    // sample included) supplies libsignal_ffi.a at link time with -L. Here we point
    // at the in-repo build output; an external app points at its fetched dir
    // (see scripts/fetch-libsignal-ffi.sh and the README "Bring your own libsignal").
    val ffiDir = "${rootProject.projectDir}/krypton-protocol/libs/apple"
    iosArm64 {
        binaries.framework { baseName = "ComposeApp"; isStatic = true; linkerOpts("-L$ffiDir/ios-arm64") }
    }
    iosSimulatorArm64 {
        binaries.framework { baseName = "ComposeApp"; isStatic = true; linkerOpts("-L$ffiDir/ios-sim-arm64") }
    }

    // The repo disables the default hierarchy template (krypton-protocol needs a
    // custom one), so wire a shared iosMain by hand.
    sourceSets {
        val commonMain by getting {
            dependencies {
                // The single Krypton dependency — real libsignal E2EE, shared API.
                implementation(project(":krypton"))
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val iosMain by creating { dependsOn(commonMain) }
        val iosArm64Main by getting { dependsOn(iosMain) }
        val iosSimulatorArm64Main by getting { dependsOn(iosMain) }
        val desktopMain by getting {
            dependencies { implementation(compose.desktop.currentOs) }
        }
    }
}

compose.desktop {
    application {
        mainClass = "io.krypton.sample.app.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "KryptonChat"
            packageVersion = "1.0.0"
        }
    }
}
