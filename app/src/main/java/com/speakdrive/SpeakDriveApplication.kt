package com.speakdrive

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SpeakDriveApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Debug builds use the App Check debug provider, release builds use Play Integrity.
        // The implementations live in src/debug and src/release.
        AppCheckInstaller.install(this)
    }
}
