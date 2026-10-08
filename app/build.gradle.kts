import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

// The Azure Speech key is NOT compiled into the app (APKs are published publicly and the key would be
// extractable). Learners enter their own key in Settings; only the default region is built in.
val defaultAzureRegion = localProperties.getProperty("azure.speechRegion")?.takeIf { it.isNotBlank() }
    ?: "southeastasia"

// Versioning configuration: Single source of truth from version.properties with Git fallback
val versionProperties = Properties().apply {
    val file = rootProject.file("version.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun getGitVersionCode(): Int {
    return try {
        val process = ProcessBuilder("git", "rev-list", "--count", "HEAD")
            .redirectErrorStream(true)
            .start()
        val text = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        text.toIntOrNull()?.takeIf { it > 0 } ?: 20
    } catch (_: Exception) {
        20
    }
}

fun getGitVersionName(): String {
    return try {
        val process = ProcessBuilder("git", "describe", "--tags", "--abbrev=0")
            .redirectErrorStream(true)
            .start()
        val tag = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        val clean = tag.removePrefix("v").replace("-debug", "").replace("-release", "")
        if (clean.matches(Regex("^\\d+\\.\\d+\\.\\d+.*"))) clean else "1.2.12"
    } catch (_: Exception) {
        "1.2.12"
    }
}

val appVersionCode = versionProperties.getProperty("versionCode")?.toIntOrNull() ?: getGitVersionCode()
val appVersionName = versionProperties.getProperty("versionName")?.trim()?.takeIf { it.isNotEmpty() } ?: getGitVersionName()

// Release signing is read from keystore.properties (git-ignored). See docs/RELEASE.md.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.speakdrive"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.speakdrive.ai"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "AZURE_SPEECH_REGION", "\"$defaultAzureRegion\"")
    }

    signingConfigs {
        // Only sign when keystore.properties is complete; a partial file must not break configuration.
        if (!keystoreProperties.getProperty("storeFile").isNullOrBlank()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // No App Check debug token is pinned into builds either: each install generates its own and shows it
        // on the About screen (debug builds only) so it can be registered in Firebase Console.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.maxParallelForks = 1
            it.forkEvery = 1L
            it.maxHeapSize = "2048m"
        }
    }
    sourceSets {
        // Room migration tests read the exported schemas as assets (debug builds only, never released).
        getByName("debug").assets.directories.add("$projectDir/schemas")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

kotlin {
    jvmToolchain(17)
}

// Room schema history, used to write and test migrations. Commit the JSON files.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    testImplementation(composeBom)
    androidTestImplementation(composeBom)

    // Project modules
    implementation(project(":audio"))
    implementation(project(":ai"))
    implementation(project(":auto"))

    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.guava)

    // Compose
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.lifecycle.viewmodel.compose)

    // Firebase — Gemini is called from :ai; App Check protects those calls
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)

    // Android Auto connection state
    implementation(libs.car.app.library)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Daily practice reminder
    implementation(libs.androidx.work.runtime.ktx)

    // Coroutines / serialization
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
