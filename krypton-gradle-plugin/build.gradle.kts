import io.krypton.Configuration

plugins {
    `java-gradle-plugin`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.vanniktech.publish)
}

group = Configuration.GROUP
version = Configuration.VERSION

kotlin { jvmToolchain(17) }

mavenPublishing {
    // Publishes the plugin + its marker so consumers can apply
    //   plugins { id("io.krypton.libsignal") version "<version>" }
    coordinates(Configuration.GROUP, "krypton-gradle-plugin", Configuration.VERSION)
}

dependencies {
    // Needs the Kotlin MPP types to auto-wire linkerOpts onto Apple native targets.
    // compileOnly: the consumer's build already brings the Kotlin Gradle plugin.
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
}

gradlePlugin {
    plugins {
        create("kryptonLibsignal") {
            id = "io.krypton.libsignal"
            implementationClass = "io.krypton.gradle.LibsignalPlugin"
            displayName = "Krypton — bring-your-own-libsignal"
            description = "Fetches libsignal_ffi from Signal's official source on the consumer's " +
                "machine and wires it onto Apple native targets. Krypton ships no Signal binary."
        }
    }
}
