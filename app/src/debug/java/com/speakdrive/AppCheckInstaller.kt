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
 * Debug builds use the App Check debug provider. Each install generates its own random debug token
 * (nothing is compiled into the APK, which is published publicly). The token is shown on the About
 * screen so it can be registered once in Firebase Console → App Check → Manage debug tokens.
 * See docs/FIREBASE_SETUP.md.
 */
object AppCheckInstaller {
    private const val TAG = "AppCheck"

    fun install(app: Application) {
        runCatching {
            Firebase.initialize(app)
            dropTokenPinnedByOlderBuilds(app)
            Firebase.appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        }.onFailure { Log.w(TAG, "App Check not installed", it) }
    }

    /**
     * The debug token of this install, or null until the SDK has generated it (it does so on the first
     * request to Gemini). Release builds always return null.
     */
    fun debugToken(context: Context): String? = runCatching {
        debugStore(context).getString(DEBUG_SECRET_KEY, null)?.takeIf { it.isNotBlank() }
    }.getOrNull()

    /**
     * Builds up to 1.2.20 pinned one shared token from local.properties into every APK. That token is
     * public now and will be revoked, so forget it once and let the SDK generate a private one.
     */
    private fun dropTokenPinnedByOlderBuilds(app: Application) {
        val flags = app.getSharedPreferences(FLAGS_PREFS, Context.MODE_PRIVATE)
        if (flags.getBoolean(KEY_PER_INSTALL_TOKEN, false)) return
        debugStore(app).edit().remove(DEBUG_SECRET_KEY).commit()
        flags.edit().putBoolean(KEY_PER_INSTALL_TOKEN, true).apply()
    }

    private fun debugStore(context: Context) = context.getSharedPreferences(
        "com.google.firebase.appcheck.debug.store.${FirebaseApp.getInstance().persistenceKey}",
        Context.MODE_PRIVATE
    )

    private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
    private const val FLAGS_PREFS = "speakdrive_appcheck"
    private const val KEY_PER_INSTALL_TOKEN = "per_install_token_v2"
}
