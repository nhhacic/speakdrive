# SpeakDrive ProGuard Rules

# Keep Hilt generated classes
-keepclasseswithmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}

# Keep Room entities
-keep class com.speakdrive.data.local.entity.** { *; }

# Keep Firebase AI classes
-keep class com.google.firebase.ai.** { *; }

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep Media3 classes
-keep class androidx.media3.** { *; }

# Keep Car App Library
-keep class androidx.car.app.** { *; }
