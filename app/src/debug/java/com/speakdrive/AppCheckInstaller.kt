package com.speakdrive

import android.app.Application
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.initialize

/**
 * Debug builds: prints a debug token to Logcat ("DebugAppCheckProvider") that you register in
 * Firebase Console → App Check → Manage debug tokens. See docs/FIREBASE_SETUP.md.
 */
object AppCheckInstaller {
    fun install(app: Application) {
        runCatching {
            Firebase.initialize(app)
            Firebase.appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        }.onFailure { Log.w("AppCheck", "App Check not installed", it) }
    }
}
