plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

// Model names can be overridden in gradle.properties without touching code,
// e.g. speakdrive.liveModel=gemini-2.5-flash-native-audio-preview-12-2025
val liveModel = providers.gradleProperty("speakdrive.liveModel").getOrElse("gemini-3.1-flash-live-preview")
// Conversation lessons try this newer model first and fall back to liveModel if it is refused.
val liveModelConversation = providers.gradleProperty("speakdrive.liveModelConversation").getOrElse("gemini-3.8-live")
val textModel = providers.gradleProperty("speakdrive.textModel").getOrElse("gemini-3.8-flash")

android {
    namespace = "com.speakdrive.ai"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        buildConfigField("String", "LIVE_MODEL", "\"$liveModel\"")
        buildConfigField("String", "LIVE_MODEL_CONVERSATION", "\"$liveModelConversation\"")
        buildConfigField("String", "TEXT_MODEL", "\"$textModel\"")
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            it.maxParallelForks = 1
            it.maxHeapSize = "384m"
            it.jvmArgs("-XX:+UseParallelGC", "-Xms64m")
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":audio"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    // Firebase AI Logic (Gemini Live API + generateContent)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.ai)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
