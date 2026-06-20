import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(17)

    jvm("desktop")

    // device + Apple-Silicon simulator. Each links Krypton's per-arch libsignal_ffi.
    iosArm64()
    iosSimulatorArm64()
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
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
