plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

dependencies {
    // The single Krypton dependency — re-exports core + storage + protocol, and
    // (on JVM) pulls libsignal-client with its bundled native dylib transitively.
    implementation(project(":krypton"))
    implementation(libs.kotlinx.coroutines.core)
}

kotlin { jvmToolchain(17) }

application { mainClass.set("io.krypton.sample.MainKt") }
