package com.speakdrive.audio

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Speaks short status messages ("connection lost…") when the AI itself cannot talk. */
interface VoiceAnnouncer {
    fun announce(text: String)
    fun shutdown()
}

/**
 * On-device text-to-speech, so announcements still work without a network connection.
 * The engine is created lazily on first use and reused afterwards.
 */
@Singleton
class TextToSpeechAnnouncer @Inject constructor(
    @ApplicationContext private val context: Context
) : VoiceAnnouncer {

    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    @Synchronized
    override fun announce(text: String) {
        val engine = tts
        when {
            engine == null -> {
                pending = text
                tts = TextToSpeech(context) { status -> onInit(status) }
            }
            ready -> speak(engine, text)
            else -> pending = text
        }
    }

    @Synchronized
    private fun onInit(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            Log.w(TAG, "TextToSpeech unavailable (status $status)")
            return
        }
        engine.language = Locale.US
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        ready = true
        pending?.let { speak(engine, it) }
        pending = null
    }

    private fun speak(engine: TextToSpeech, text: String) {
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "speakdrive-announcement")
    }

    @Synchronized
    override fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = false
        pending = null
    }

    private companion object {
        const val TAG = "VoiceAnnouncer"
    }
}
