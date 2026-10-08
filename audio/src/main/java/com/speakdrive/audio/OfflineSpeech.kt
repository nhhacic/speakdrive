package com.speakdrive.audio

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Speaking and listening without a network, so the learner can keep practising while the AI is
 * unreachable (tunnels, mountain roads). Speech uses the phone's own text-to-speech; listening uses
 * the on-device speech recogniser when the phone has one.
 */
interface OfflineSpeech {
    /** False when offline practice is impossible altogether (no speech engine at all, tests). */
    val isAvailable: Boolean get() = true

    /** Speaks [text] and returns once it has been said, or could not be. */
    suspend fun speak(text: String, vietnamese: Boolean = false)

    /** Whether listening may work offline on this phone (a recogniser exists and the mic is allowed). */
    suspend fun canListen(): Boolean

    /** Listens for one English utterance of at most about [timeoutMs]. */
    suspend fun listen(timeoutMs: Long): ListenResult

    /** Frees the recogniser and the speech engine, so the microphone is free for the AI again. */
    fun release()
}

/** No offline speech: the lesson just waits quietly for the network, as before. */
object NoOfflineSpeech : OfflineSpeech {
    override val isAvailable: Boolean get() = false
    override suspend fun speak(text: String, vietnamese: Boolean) = Unit
    override suspend fun canListen(): Boolean = false
    override suspend fun listen(timeoutMs: Long): ListenResult = ListenResult.Unavailable
    override fun release() = Unit
}

sealed interface ListenResult {
    data class Heard(val text: String) : ListenResult

    /** Nothing was said, or nothing could be understood. */
    data object Silence : ListenResult

    /** Recognition does not work right now, e.g. no offline English model is installed. */
    data object Unavailable : ListenResult
}

@Singleton
class AndroidOfflineSpeech @Inject constructor(
    @param:ApplicationContext private val context: Context
) : OfflineSpeech {

    private var tts: TextToSpeech? = null
    private var ttsReady: CompletableDeferred<Boolean>? = null
    private var recognizer: SpeechRecognizer? = null

    override suspend fun speak(text: String, vietnamese: Boolean) {
        val engine = textToSpeech() ?: return
        withContext(Dispatchers.Main) {
            engine.setAudioAttributes(speechAttributes())
            val wanted = if (vietnamese && engine.isLanguageAvailable(VIETNAMESE) >= TextToSpeech.LANG_AVAILABLE) VIETNAMESE else Locale.US
            if (engine.voice?.locale?.language != wanted.language) engine.language = wanted
            engine.setSpeechRate(if (wanted == Locale.US) ENGLISH_SPEECH_RATE else 1f)
        }
        // Never wait forever on an engine that forgets to report the end of an utterance.
        val finished = withTimeoutOrNull(MAX_SPEAK_MS + text.length * MS_PER_CHARACTER) {
            suspendCancellableCoroutine<Unit> { cont ->
                val id = "offline-${System.nanoTime()}"
                val finish = { utteranceId: String? -> if (utteranceId == id && cont.isActive) cont.resume(Unit) }
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) = finish(utteranceId)

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) = finish(utteranceId)
                    override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId)
                    override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId)
                })
                if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS && cont.isActive) {
                    cont.resume(Unit)
                }
                cont.invokeOnCancellation { engine.stop() }
            }
        }
        if (finished == null) engine.stop()
    }

    override suspend fun canListen(): Boolean = withContext(Dispatchers.Main) {
        val micAllowed = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        micAllowed && (onDeviceAvailable() || SpeechRecognizer.isRecognitionAvailable(context))
    }

    override suspend fun listen(timeoutMs: Long): ListenResult = withContext(Dispatchers.Main) {
        val rec = recognizer ?: createRecognizer()?.also { recognizer = it } ?: return@withContext ListenResult.Unavailable
        val result = withTimeoutOrNull(timeoutMs + RESULT_GRACE_MS) {
            suspendCancellableCoroutine { cont ->
                val done = { result: ListenResult -> if (cont.isActive) cont.resume(result) }
                rec.setRecognitionListener(object : RecognitionListener {
                    override fun onResults(results: Bundle?) {
                        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        done(if (text.isNullOrBlank()) ListenResult.Silence else ListenResult.Heard(text))
                    }

                    override fun onError(error: Int) {
                        Log.i(TAG, "Recognition error $error")
                        done(
                            when (error) {
                                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> ListenResult.Silence
                                else -> ListenResult.Unavailable
                            }
                        )
                    }

                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
                rec.startListening(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                        .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, END_OF_SPEECH_SILENCE_MS)
                )
                cont.invokeOnCancellation { rec.cancel() }
            }
        }
        if (result == null) rec.cancel()
        result ?: ListenResult.Silence
    }

    override fun release() {
        val rec = recognizer
        val engine = tts
        recognizer = null
        tts = null
        ttsReady = null
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            runCatching { rec?.destroy() }
            runCatching { engine?.stop() }
            runCatching { engine?.shutdown() }
        }
    }

    private suspend fun textToSpeech(): TextToSpeech? = withContext(Dispatchers.Main) {
        val pending = ttsReady
        val existing = tts
        val ready = if (existing != null && pending != null) {
            pending
        } else {
            CompletableDeferred<Boolean>().also { deferred ->
                ttsReady = deferred
                tts = TextToSpeech(context) { status -> deferred.complete(status == TextToSpeech.SUCCESS) }
            }
        }
        val engine = tts
        if (withTimeoutOrNull(TTS_INIT_TIMEOUT_MS) { ready.await() } == true) {
            engine
        } else {
            Log.w(TAG, "Text-to-speech is not available")
            runCatching { engine?.shutdown() }
            tts = null
            ttsReady = null
            null
        }
    }

    private fun onDeviceAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    private fun createRecognizer(): SpeechRecognizer? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context) ->
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        SpeechRecognizer.isRecognitionAvailable(context) -> SpeechRecognizer.createSpeechRecognizer(context)
        else -> null
    }

    /** Same output path as the AI: the voice-call route while a lesson holds it, otherwise media. */
    private fun speechAttributes(): AudioAttributes {
        val inCall = (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.mode == AudioManager.MODE_IN_COMMUNICATION
        return AudioAttributes.Builder()
            .setUsage(if (inCall) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }

    private companion object {
        const val TAG = "OfflineSpeech"
        val VIETNAMESE: Locale = Locale.forLanguageTag("vi-VN")
        const val ENGLISH_SPEECH_RATE = 0.9f
        const val MAX_SPEAK_MS = 8_000L
        const val MS_PER_CHARACTER = 120L
        const val TTS_INIT_TIMEOUT_MS = 5_000L
        const val RESULT_GRACE_MS = 3_000L
        const val END_OF_SPEECH_SILENCE_MS = 1_500L
    }
}
