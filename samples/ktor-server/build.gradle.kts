plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

dependencies {
    // The SERVER half of libsignal — JVM-only. Version-matched to Krypton's client
    // (org.signal:libsignal-server == org.signal:libsignal-client at the same version).
    implementation("org.signal:libsignal-server:${libs.versions.libsignal.get()}")

    // Ktor as the transport. Plain text endpoints keep the sample dependency-light
    // (no serialization plugin needed); swap in ContentNegotiation for real JSON.
    implementation("io.ktor:ktor-server-core:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-netty:${libs.versions.ktor.get()}")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.6")
}

kotlin { jvmToolchain(17) }

application { mainClass.set("io.krypton.sample.server.AppKt") }
