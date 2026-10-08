package com.speakdrive

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.initialize

/** Release builds attest with Play Integrity, so only the genuine app can call Gemini. */
object AppCheckInstaller {
    fun install(app: Application) {
        runCatching {
            Firebase.initialize(app)
            Firebase.appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
        }.onFailure { Log.w("AppCheck", "App Check not installed", it) }
    }

    /** Debug tokens only exist in debug builds. */
    @Suppress("UNUSED_PARAMETER")
    fun debugToken(context: Context): String? = null
}
