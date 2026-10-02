# SpeakDrive R8 rules.
# Hilt, Room, Media3, Firebase and kotlinx.serialization ship their own consumer rules,
# so only app-specific needs are listed here.

# Keep line numbers so Play Console crash reports are readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Navigation routes are serialized by name.
-keep class com.speakdrive.ui.navigation.** { *; }
