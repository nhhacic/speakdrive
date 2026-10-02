package com.speakdrive.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Microphone and speaker for a live conversation. */
interface LiveAudio {
    /** Starts recording 16 kHz mono PCM16; [onChunk] gets ~100 ms chunks on a background thread. */
    fun startCapture(onChunk: (ByteArray) -> Unit)
    fun stopCapture()

    /** Queues 24 kHz mono PCM16 from the AI for playback. */
    fun play(pcm: ByteArray)

    /** Drops everything queued or still buffered, e.g. when the learner interrupts. */
    fun flushPlayback()

    /** True while AI audio is queued or still coming out of the speaker. */
    fun isPlaying(): Boolean

    fun release()
}

/**
 * Owns the microphone and speaker so the app can (1) mute the microphone exactly while the AI's
 * voice is audible, which stops it hearing its own echo, and (2) keep the learner's raw audio for
 * pronunciation assessment.
 */
@Singleton
class LiveAudioIO @Inject constructor(
    @param:ApplicationContext private val context: Context
) : LiveAudio {

    private val lock = Any()
    private var recorder: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var captureThread: Thread? = null
    @Volatile private var capturing = false

    private var track: AudioTrack? = null
    private var playbackThread: Thread? = null
    private val playbackQueue = LinkedBlockingQueue<ByteArray>()
    @Volatile private var framesWritten = 0L
    @Volatile private var playbackRunning = false

    override fun startCapture(onChunk: (ByteArray) -> Unit): Unit = synchronized(lock) {
        if (capturing) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("RECORD_AUDIO permission is not granted")
        }
        val minBuffer = AudioRecord.getMinBufferSize(IN_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            IN_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, CHUNK_BYTES * 4)
        )
        check(record.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord failed to initialise" }
        if (AcousticEchoCanceler.isAvailable()) echoCanceler = AcousticEchoCanceler.create(record.audioSessionId)?.apply { enabled = true }
        if (NoiseSuppressor.isAvailable()) noiseSuppressor = NoiseSuppressor.create(record.audioSessionId)?.apply { enabled = true }
        record.startRecording()
        recorder = record
        capturing = true
        captureThread = Thread({
            val buffer = ByteArray(CHUNK_BYTES)
            while (capturing) {
                var filled = 0
                while (filled < CHUNK_BYTES && capturing) {
                    val read = record.read(buffer, filled, CHUNK_BYTES - filled)
                    if (read <= 0) break
                    filled += read
                }
                if (filled > 0 && capturing) onChunk(buffer.copyOf(filled))
            }
        }, "speakdrive-mic").apply { start() }
        Unit
    }

    override fun stopCapture(): Unit = synchronized(lock) {
        capturing = false
        captureThread?.join(500)
        captureThread = null
        runCatching { recorder?.stop() }
        recorder?.release()
        recorder = null
        echoCanceler?.release()
        echoCanceler = null
        noiseSuppressor?.release()
        noiseSuppressor = null
    }

    override fun play(pcm: ByteArray) {
        ensurePlayback()
        playbackQueue.offer(pcm)
    }

    override fun flushPlayback() {
        playbackQueue.clear()
        synchronized(lock) {
            track?.let {
                runCatching {
                    it.pause()
                    it.flush()
                    it.play()
                }
            }
            framesWritten = 0
        }
    }

    override fun isPlaying(): Boolean {
        if (playbackQueue.isNotEmpty()) return true
        val current = track ?: return false
        val played = current.playbackHeadPosition.toLong() and 0xFFFFFFFFL
        return framesWritten - played > STILL_PLAYING_FRAMES
    }

    override fun release() {
        stopCapture()
        playbackRunning = false
        playbackThread?.interrupt()
        playbackThread = null
        playbackQueue.clear()
        synchronized(lock) {
            track?.release()
            track = null
            framesWritten = 0
        }
    }

    private fun ensurePlayback(): Unit = synchronized(lock) {
        if (track != null && playbackRunning) return
        val minBuffer = AudioTrack.getMinBufferSize(OUT_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val newTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(OUT_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(minBuffer * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        newTrack.play()
        track = newTrack
        framesWritten = 0
        playbackRunning = true
        playbackThread = Thread({
            while (playbackRunning) {
                val chunk = try {
                    playbackQueue.poll(200, TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                    break
                } ?: continue
                val written = newTrack.write(chunk, 0, chunk.size)
                if (written > 0) framesWritten += written / 2
                else if (written < 0) Log.w(TAG, "AudioTrack write error $written")
            }
        }, "speakdrive-speaker").apply { start() }
        Unit
    }

    companion object {
        private const val TAG = "LiveAudioIO"
        const val IN_RATE = 16_000
        const val OUT_RATE = 24_000

        /** 100 ms of 16 kHz mono PCM16. */
        const val CHUNK_BYTES = IN_RATE / 10 * 2

        /** Less than 20 ms left in the speaker counts as finished. */
        private const val STILL_PLAYING_FRAMES = OUT_RATE / 50
    }
}
