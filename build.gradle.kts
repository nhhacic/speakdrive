// Top-level build file where you can add configuration options common to all sub-projects/modules.
// Kotlin support is built into AGP 9, so the org.jetbrains.kotlin.android plugin is not applied.
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import java.time.Duration

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
        val variant = name.removePrefix("ksp").removeSuffix("Kotlin").replaceFirstChar { it.lowercase() }
        doFirst {
            // KSP on Windows sometimes fails when these folders are missing after a clean.
            file("${layout.buildDirectory.get()}/generated/ksp/debug/java/hilt_aggregated_deps").mkdirs()
            file("${layout.buildDirectory.get()}/generated/ksp/release/java/hilt_aggregated_deps").mkdirs()
            if (variant.isNotEmpty()) file("${layout.buildDirectory.get()}/kspCaches/$variant").mkdirs()
        }
    }

    // A hung test must fail the build with its name in the log instead of eating the whole CI timeout.
    tasks.withType<Test>().configureEach {
        timeout.set(Duration.ofMinutes(15))
        testLogging {
            events(TestLogEvent.STARTED, TestLogEvent.FAILED, TestLogEvent.SKIPPED)
            exceptionFormat = TestExceptionFormat.FULL
        }
    }
}
