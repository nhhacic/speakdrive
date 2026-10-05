// Top-level build file where you can add configuration options common to all sub-projects/modules.
// Kotlin support is built into AGP 9, so the org.jetbrains.kotlin.android plugin is not applied.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.google.services) apply false
}

subprojects {
    tasks.matching { it.name.startsWith("ksp") }.configureEach {
        doFirst {
            file("${layout.buildDirectory.get()}/generated/ksp/debug/java/hilt_aggregated_deps").mkdirs()
            file("${layout.buildDirectory.get()}/generated/ksp/release/java/hilt_aggregated_deps").mkdirs()
        }
    }
}
