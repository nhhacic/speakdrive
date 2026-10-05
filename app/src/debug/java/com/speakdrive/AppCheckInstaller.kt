package com.speakdrive

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.initialize

/**
 * Debug builds: prints a debug token to Logcat ("DebugAppCheckProvider") that you register in
 * Firebase Console → App Check → Manage debug tokens. See docs/FIREBASE_SETUP.md.
 *
 * By default the SDK generates a NEW random token for every fresh install (reinstall, "Clear data",
 * a new device...), and the server then closes the Gemini Live connection with
 * "Firebase App Check token is invalid" (shown by the SDK as "Channel was closed by the server").
 * Setting `appcheck.debugToken=<uuid>` in local.properties pins one token for all installs, so it
 * only has to be registered once.
 */
object AppCheckInstaller {
    private const val TAG = "AppCheck"

    fun install(app: Application) {
        runCatching {
            Firebase.initialize(app)
            pinDebugToken(app)
            Firebase.appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        }.onFailure { Log.w(TAG, "App Check not installed", it) }
    }

    /** Pre-seeds the debug provider's own storage so it reuses our fixed token instead of a random one. */
    private fun pinDebugToken(app: Application) {
        val token = BuildConfig.APP_CHECK_DEBUG_TOKEN.trim()
        if (token.isEmpty()) return
        val persistenceKey = FirebaseApp.getInstance().persistenceKey
        val prefs = app.getSharedPreferences(
            "com.google.firebase.appcheck.debug.store.$persistenceKey",
            Context.MODE_PRIVATE
        )
        if (prefs.getString(DEBUG_SECRET_KEY, null) != token) {
            prefs.edit().putString(DEBUG_SECRET_KEY, token).commit()
        }
        Log.i(TAG, "Using pinned App Check debug token from local.properties: $token")
    }

    private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
}
