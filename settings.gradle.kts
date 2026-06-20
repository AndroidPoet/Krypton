pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "krypton"

include(":krypton-gradle-plugin")
include(":krypton-core")
include(":krypton")
include(":krypton-storage")
include(":krypton-protocol")
include(":krypton-sealed-sender")
include(":krypton-double-ratchet")
include(":krypton-zkgroup")
include(":samples:encrypted-chat")
include(":samples:jvm-demo")
include(":samples:ktor-server")
include(":samples:composeApp")
