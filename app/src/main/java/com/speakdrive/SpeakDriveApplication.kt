package com.speakdrive

import android.app.Application
import android.util.Log
import com.speakdrive.playback.CarConnectionAutoStarter
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SpeakDriveApplication : Application() {

    @Inject
    lateinit var carConnectionAutoStarter: CarConnectionAutoStarter

    override fun onCreate() {
        super.onCreate()
        // Debug builds use the App Check debug provider, release builds use Play Integrity.
        // The implementations live in src/debug and src/release.
        AppCheckInstaller.install(this)
        runCatching {
            carConnectionAutoStarter.start()
        }.onFailure { Log.w("SpeakDriveApplication", "Failed to start car connection starter", it) }
    }
}
