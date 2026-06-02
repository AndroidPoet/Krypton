plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.kryptonchat"
    compileSdk = io.krypton.Configuration.COMPILE_SDK

    defaultConfig {
        applicationId = "com.example.kryptonchat"
        minSdk = io.krypton.Configuration.MIN_SDK
        targetSdk = io.krypton.Configuration.COMPILE_SDK
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":krypton-factory"))
    implementation(project(":krypton-protocol"))
    implementation(project(":krypton-storage"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
}
