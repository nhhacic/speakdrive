package com.speakdrive

import android.app.Application
import android.os.Build
import android.util.Log
import com.speakdrive.ai.diagnostics.LiveLog
import com.speakdrive.playback.CarConnectionAutoStarter
import com.speakdrive.reminder.PracticeReminder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class SpeakDriveApplication : Application() {

    @Inject
    lateinit var carConnectionAutoStarter: CarConnectionAutoStarter

    @Inject
    lateinit var practiceReminder: PracticeReminder

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Lesson logs stay on the phone; read them with: adb pull /sdcard/Android/data/<package>/files/logs
        LiveLog.init(File(getExternalFilesDir(null) ?: filesDir, "logs"))
        LiveLog.i(
            "SpeakDriveApplication",
            "App started: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) on ${Build.MANUFACTURER} ${Build.MODEL}, " +
                "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
        )
        // Debug builds use the App Check debug provider, release builds use Play Integrity.
        // The implementations live in src/debug and src/release.
        AppCheckInstaller.install(this)
        runCatching {
            carConnectionAutoStarter.start()
        }.onFailure { Log.w("SpeakDriveApplication", "Failed to start car connection starter", it) }
        runCatching {
            practiceReminder.start(appScope)
        }.onFailure { Log.w("SpeakDriveApplication", "Failed to start the practice reminder", it) }
    }
}
