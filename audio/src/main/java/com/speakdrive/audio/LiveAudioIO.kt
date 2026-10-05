package com.speakdrive.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

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

    /** Whether the user has a wired or bluetooth headset/headphones connected. */
    fun isHeadsetConnected(): Boolean = false

    /** Whether an in-car audio output (Bluetooth A2DP, AUX, USB accessory/device) is connected. */
    fun isCarAudioConnected(): Boolean = false

    /**
     * True when the AI's voice and the microphone both run on the voice-call path, so the phone's
     * (or the car's hands-free) echo canceller removes the AI's own voice from the mic. Only then
     * is it safe to let the learner interrupt the AI with a normal speaking voice.
     */
    fun isEchoCancelled(): Boolean = false

    /** Sets output volume gain for AI speech playback, from 0.0 (muted) to 1.0 (full). */
    fun setVolume(volumeFraction: Float) = Unit

    fun release()
}

/**
 * Owns the microphone and speaker.
 *
 * Preferred route — the same one Gemini Live and ChatGPT voice use: the voice-call path
 * (`MODE_IN_COMMUNICATION`, mic source `VOICE_COMMUNICATION`, playback `USAGE_VOICE_COMMUNICATION`)
 * with the output device chosen **explicitly**:
 * - car / Bluetooth: hands-free (HFP/SCO) — the AI comes out of the car speakers and the car's
 *   hands-free unit cancels the echo, exactly like a phone call;
 * - phone alone: the loudspeaker (never the quiet earpiece), with the phone's echo canceller;
 * - wired/USB headset: the headset.
 * Because the echo is cancelled in hardware, the learner can talk over the AI without the AI
 * hearing itself.
 *
 * Fallback — if no call device can be selected or the call mic delivers only silence: the old
 * media route (`MODE_NORMAL`, `VOICE_RECOGNITION`, `USAGE_MEDIA`). Echo is then handled by
 * muting the mic while the AI talks (see MicGate).
 *
 * It also accurately detects when playback finishes so MicGate never locks the mic in mute state,
 * and keeps the learner's raw audio for pronunciation assessment.
 */
@Singleton
class LiveAudioIO @Inject constructor(
    @param:ApplicationContext private val context: Context
) : LiveAudio {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var previousAudioMode = AudioManager.MODE_NORMAL
    private var previousSpeakerphoneOn = false
    private var previousVoiceVolume = -1
    private var startedBluetoothSco = false

    /** True while the voice-call route (hardware echo cancellation) is active. */
    @Volatile private var communicationRoute = false
    @Volatile private var routeName = "-"

    private val lock = Any()
    private var recorder: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var gainControl: AutomaticGainControl? = null
    private var captureThread: Thread? = null
    @Volatile private var capturing = false

    private var track: AudioTrack? = null
    private var trackUsage = -1
    private var playbackThread: Thread? = null
    private val playbackQueue = LinkedBlockingQueue<ByteArray>()
    @Volatile private var framesWritten = 0L
    @Volatile private var playbackRunning = false
    @Volatile private var playbackGeneration = 0

    @Volatile private var volumeGain = 0.8f

    @Volatile private var lastWriteTimestampMs = 0L
    @Volatile private var lastPlaybackHeadPosition = -1L
    @Volatile private var lastPositionChangeTimestampMs = 0L

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            Log.d(TAG, "Audio devices added: ${addedDevices?.joinToString { it.type.toString() }}")
            applyAudioRouting()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            Log.d(TAG, "Audio devices removed: ${removedDevices?.joinToString { it.type.toString() }}")
            applyAudioRouting()
        }
    }

    override fun startCapture(onChunk: (ByteArray) -> Unit): Unit = synchronized(lock) {
        if (capturing) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("RECORD_AUDIO permission is not granted")
        }
        previousAudioMode = audioManager.mode
        @Suppress("DEPRECATION")
        previousSpeakerphoneOn = audioManager.isSpeakerphoneOn

        val minBuffer = AudioRecord.getMinBufferSize(IN_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufferSize = maxOf(minBuffer, CHUNK_BYTES * 4)

        var record: AudioRecord? = null
        if (runCatching { enterCommunicationRoute() }.onFailure { Log.w(TAG, "Call route failed", it) }.getOrDefault(false)) {
            // The call mic carries the hardware echo canceller. Bluetooth hands-free needs up to a
            // second to connect, so it gets a longer probe before being declared silent.
            record = openRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, bufferSize, COMM_PROBE_CHUNKS)
            if (record == null) {
                Log.w(TAG, "Call mic unusable on route $routeName; falling back to the media route")
                leaveCommunicationRoute()
            }
        }
        if (record == null) {
            enterMediaRoute()
            // Some HALs hand back pure digital silence for a source (the AI then "never hears"
            // the learner), so each candidate is probed and skipped if it delivers only zeros.
            val sourcesToTry = listOf(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.VOICE_COMMUNICATION
            )
            for ((index, source) in sourcesToTry.withIndex()) {
                val isLast = index == sourcesToTry.lastIndex
                record = openRecord(source, bufferSize, if (isLast) 0 else PROBE_CHUNKS)
                if (record != null) break
            }
        }
        runCatching { audioManager.registerAudioDeviceCallback(audioDeviceCallback, null) }
        checkNotNull(record) { "AudioRecord failed to initialize with any available audio source" }

        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(record.audioSessionId)?.apply { enabled = true }
        }
        if (AcousticEchoCanceler.isAvailable()) {
            echoCanceler = AcousticEchoCanceler.create(record.audioSessionId)?.apply { enabled = true }
        }
        if (AutomaticGainControl.isAvailable()) {
            // Quiet microphones otherwise sit below the server's speech-detection threshold.
            gainControl = AutomaticGainControl.create(record.audioSessionId)?.apply { enabled = true }
        }

        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) record.startRecording()
        recorder = record
        capturing = true
        AudioDiagnostics.reset(sourceName(record.audioSource), routeName)
        Log.i(
            AudioDiagnostics.TAG,
            "Mic capture started: source=${sourceName(record.audioSource)} route=$routeName " +
                "echoCancelled=${isEchoCancelled()} mode=${audioManager.mode}"
        )
        captureThread = Thread({
            val buffer = ByteArray(CHUNK_BYTES)
            var chunkCount = 0L
            while (capturing) {
                var filled = 0
                while (filled < CHUNK_BYTES && capturing) {
                    val read = record.read(buffer, filled, CHUNK_BYTES - filled)
                    if (read <= 0) {
                        if (read < 0) Log.w(TAG, "AudioRecord read returned error code $read")
                        try {
                            Thread.sleep(10)
                        } catch (_: InterruptedException) {
                            break
                        }
                        break
                    }
                    filled += read
                }
                if (filled > 0 && capturing) {
                    chunkCount++
                    AudioDiagnostics.onCaptured(PcmLevel.rms(buffer, filled))
                    if (chunkCount % 50L == 0L) {
                        // Level 0 for a long time means the OS/HAL is silencing the mic.
                        Log.d(TAG, "Recording active: $chunkCount chunks, last level rms=${PcmLevel.rms(buffer, filled)}")
                    }
                    onChunk(buffer.copyOf(filled))
                }
            }
        }, "speakdrive-mic").apply { start() }
        Unit
    }

    /**
     * Creates and starts a recorder for [source]. With [probeChunks] > 0 it must deliver real
     * sound (not only zeros) within that many 100 ms chunks, otherwise null is returned.
     */
    private fun openRecord(source: Int, bufferSize: Int, probeChunks: Int): AudioRecord? {
        val candidate = runCatching {
            AudioRecord(source, IN_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
        }.getOrNull()
        if (candidate == null || candidate.state != AudioRecord.STATE_INITIALIZED) {
            candidate?.release()
            return null
        }
        if (probeChunks > 0 && !deliversSound(candidate, probeChunks)) {
            Log.w(TAG, "Audio source ${sourceName(source)} delivers only silence; trying the next one")
            candidate.release()
            return null
        }
        Log.d(TAG, "AudioRecord initialized with source $source")
        return candidate
    }

    private fun sourceName(source: Int): String = when (source) {
        MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
        MediaRecorder.AudioSource.MIC -> "MIC"
        MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "VOICE_COMMUNICATION"
        else -> source.toString()
    }

    /**
     * Starts [record] and reads up to [chunks] x 100 ms of it. False only if every sample was
     * exactly zero, i.e. the source is muted. The record is left running either way.
     */
    private fun deliversSound(record: AudioRecord, chunks: Int): Boolean {
        return runCatching {
            record.startRecording()
            val probe = ByteArray(CHUNK_BYTES)
            repeat(chunks) {
                var filled = 0
                while (filled < probe.size) {
                    val read = record.read(probe, filled, probe.size - filled)
                    if (read <= 0) return@runCatching true // Cannot tell; do not skip a working source.
                    filled += read
                }
                if (!PcmLevel.isDigitalSilence(probe)) return@runCatching true
            }
            false
        }.getOrDefault(true)
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
        gainControl?.release()
        gainControl = null
        runCatching { audioManager.unregisterAudioDeviceCallback(audioDeviceCallback) }
        restoreAudioManager()
    }

    override fun play(pcm: ByteArray) {
        ensurePlayback()
        playbackQueue.offer(pcm)
    }

    override fun flushPlayback() {
        playbackQueue.clear()
        lastWriteTimestampMs = 0L
        lastPlaybackHeadPosition = -1L
        lastPositionChangeTimestampMs = 0L
        synchronized(lock) {
            track?.let {
                runCatching {
                    it.pause()
                    it.flush()
                    it.play()
                }
                framesWritten = it.playbackHeadPosition.toLong() and 0xFFFFFFFFL
            } ?: run {
                framesWritten = 0L
            }
        }
    }

    override fun isPlaying(): Boolean {
        if (playbackQueue.isNotEmpty()) return true
        val current = track ?: return false
        val played = current.playbackHeadPosition.toLong() and 0xFFFFFFFFL
        val remainingFrames = framesWritten - played
        if (remainingFrames <= STILL_PLAYING_FRAMES) return false

        val now = System.currentTimeMillis()
        // If the playback head hasn't moved for over 80 ms when queue is empty, the hardware buffer has completed.
        if (played != lastPlaybackHeadPosition) {
            lastPlaybackHeadPosition = played
            lastPositionChangeTimestampMs = now
        } else if (lastPositionChangeTimestampMs != 0L && now - lastPositionChangeTimestampMs > 80L) {
            return false
        }

        // Safety timeout: if no audio chunks have been written for over 250 ms and queue is empty, AI speech has ended.
        if (lastWriteTimestampMs != 0L && now - lastWriteTimestampMs > 250L) {
            return false
        }

        return true
    }

    override fun isHeadsetConnected(): Boolean {
        return runCatching {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            devices.any {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY ||
                it.type == AudioDeviceInfo.TYPE_AUX_LINE ||
                it.type == AudioDeviceInfo.TYPE_LINE_ANALOG ||
                it.type == AudioDeviceInfo.TYPE_LINE_DIGITAL ||
                it.type == AudioDeviceInfo.TYPE_HEARING_AID
            }
        }.getOrDefault(false)
    }

    override fun isCarAudioConnected(): Boolean {
        return runCatching {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            devices.any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_AUX_LINE ||
                it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY ||
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE
            }
        }.getOrDefault(false)
    }

    override fun isEchoCancelled(): Boolean =
        communicationRoute && recorder?.audioSource == MediaRecorder.AudioSource.VOICE_COMMUNICATION

    override fun release() {
        stopCapture()
        playbackQueue.clear()
        lastWriteTimestampMs = 0L
        lastPlaybackHeadPosition = -1L
        lastPositionChangeTimestampMs = 0L
        synchronized(lock) { releaseTrackLocked() }
        restoreAudioManager()
    }

    override fun setVolume(volumeFraction: Float) {
        val clamped = volumeFraction.coerceIn(0.0f, 1.0f)
        volumeGain = clamped
        synchronized(lock) {
            runCatching { track?.setVolume(clamped) }
        }
        Log.d(TAG, "setVolume: volumeGain set to $clamped")
    }

    // ---------------------------------------------------------------- routing

    /**
     * Switches to the voice-call path and explicitly selects hands-free Bluetooth, a headset or
     * the loudspeaker. Returns false (and leaves the mode untouched) if none could be selected.
     */
    private fun enterCommunicationRoute(): Boolean {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (!selectCommunicationDevice()) {
            audioManager.mode = AudioManager.MODE_NORMAL
            routeName = "-"
            return false
        }
        communicationRoute = true
        boostVoiceVolume()
        return true
    }

    private fun leaveCommunicationRoute() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { audioManager.clearCommunicationDevice() }
        }
        stopLegacySco()
        restoreVoiceVolume()
        communicationRoute = false
        runCatching { audioManager.mode = AudioManager.MODE_NORMAL }
    }

    private fun enterMediaRoute() {
        communicationRoute = false
        runCatching {
            // Keep MODE_NORMAL so Media streams (A2DP for car audio, built-in loudspeaker)
            // are never downgraded to low-bandwidth voice-call SCO or forced into the quiet earpiece.
            audioManager.mode = AudioManager.MODE_NORMAL
        }
        routeName = if (isCarAudioConnected()) "MEDIA_CAR" else if (isHeadsetConnected()) "MEDIA_HEADSET" else "MEDIA_SPEAKER"
        applyAudioRouting()
    }

    /** Picks and selects the call output device; never the earpiece. */
    private fun selectCommunicationDevice(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val devices = audioManager.availableCommunicationDevices
            val type = pickCommunicationDeviceType(devices.map { it.type }) ?: return false
            val device = devices.first { it.type == type }
            if (audioManager.communicationDevice?.id == device.id) {
                routeName = deviceName(type)
                return true
            }
            val ok = audioManager.setCommunicationDevice(device)
            Log.i(TAG, "Communication device ${deviceName(type)} selected=$ok")
            if (ok) routeName = deviceName(type)
            return ok
        }
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }
        val type = pickCommunicationDeviceType(outputs) ?: AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        @Suppress("DEPRECATION")
        when (type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> {
                audioManager.isSpeakerphoneOn = false
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
                startedBluetoothSco = true
            }
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> {
                stopLegacySco()
                audioManager.isSpeakerphoneOn = true
            }
            else -> {
                stopLegacySco()
                audioManager.isSpeakerphoneOn = false
            }
        }
        routeName = deviceName(type)
        return true
    }

    @Suppress("DEPRECATION")
    private fun stopLegacySco() {
        if (!startedBluetoothSco) return
        runCatching {
            audioManager.isBluetoothScoOn = false
            audioManager.stopBluetoothSco()
        }
        startedBluetoothSco = false
    }

    /** The call volume is separate from media volume and often low; make sure the AI is audible. */
    private fun boostVoiceVolume() {
        runCatching {
            // NEVER automatically boost voice volume when a headset (Bluetooth or wired) is connected.
            // Headsets sit right in the learner's ear, so forcing 80-100% volume blasts uncomfortably loud.
            if (isHeadsetConnected()) {
                Log.i(TAG, "Headset/Bluetooth connected, skipping boostVoiceVolume to avoid loud blast in ear")
                return@runCatching
            }
            val stream = AudioManager.STREAM_VOICE_CALL
            val max = audioManager.getStreamMaxVolume(stream)
            val current = audioManager.getStreamVolume(stream)
            val wanted = ceil(max * MIN_VOICE_VOLUME_FRACTION).toInt()
            if (current < wanted) {
                previousVoiceVolume = current
                audioManager.setStreamVolume(stream, wanted, 0)
            }
        }.onFailure { Log.w(TAG, "Could not raise call volume", it) }
    }

    private fun restoreVoiceVolume() {
        if (previousVoiceVolume < 0) return
        runCatching { audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, previousVoiceVolume, 0) }
        previousVoiceVolume = -1
    }

    private fun restoreAudioManager() {
        runCatching {
            if (communicationRoute) leaveCommunicationRoute()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }
            audioManager.mode = previousAudioMode
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = previousSpeakerphoneOn
        }
        communicationRoute = false
    }

    private fun applyAudioRouting() {
        runCatching {
            if (communicationRoute) {
                // A car / headset connected or disconnected mid-lesson: move the call to it.
                selectCommunicationDevice()
                Log.d(TAG, "applyAudioRouting: call route now $routeName")
                return@runCatching
            }
            val externalAudio = isHeadsetConnected()
            Log.d(TAG, "applyAudioRouting: externalAudio=$externalAudio, isCar=${isCarAudioConnected()}")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (externalAudio) {
                    // Car Bluetooth (A2DP), Android Auto, USB or Headset connected:
                    // Clear any communication device override so Android routes media naturally to external audio.
                    audioManager.clearCommunicationDevice()
                }
            } else {
                @Suppress("DEPRECATION")
                if (!externalAudio) {
                    audioManager.isSpeakerphoneOn = true
                }
            }
        }.onFailure { Log.w(TAG, "applyAudioRouting failed", it) }
    }

    // ---------------------------------------------------------------- playback

    private fun ensurePlayback(): Unit = synchronized(lock) {
        // The AI voice must play on the same path as the mic's echo canceller expects.
        val usage = if (communicationRoute) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_MEDIA
        if (track != null && playbackRunning && trackUsage == usage) return
        releaseTrackLocked()
        val minBuffer = AudioTrack.getMinBufferSize(OUT_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val builder = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(usage)
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

        val newTrack = builder.build()
        runCatching {
            newTrack.setVolume(volumeGain)
        }
        newTrack.play()
        track = newTrack
        trackUsage = usage
        framesWritten = 0
        lastWriteTimestampMs = System.currentTimeMillis()
        lastPlaybackHeadPosition = -1L
        lastPositionChangeTimestampMs = 0L
        playbackRunning = true
        val generation = ++playbackGeneration
        Log.d(TAG, "Playback track created: usage=$usage route=$routeName volumeGain=$volumeGain")
        playbackThread = Thread({
            while (playbackRunning && playbackGeneration == generation) {
                val chunk = try {
                    playbackQueue.poll(200, TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                    break
                } ?: continue
                if (playbackGeneration != generation) {
                    // The track was rebuilt while waiting; hand the chunk to the new one.
                    playbackQueue.offer(chunk)
                    break
                }
                val gain = volumeGain
                val effectiveChunk = if (gain < 0.999f) {
                    scalePcm16(chunk, gain)
                } else {
                    chunk
                }
                val written = runCatching { newTrack.write(effectiveChunk, 0, effectiveChunk.size) }.getOrDefault(-1)
                if (written > 0) {
                    framesWritten += written / 2
                    lastWriteTimestampMs = System.currentTimeMillis()
                } else if (written < 0) {
                    Log.w(TAG, "AudioTrack write error $written")
                }
            }
        }, "speakdrive-speaker").apply { start() }
        Unit
    }

    private fun scalePcm16(pcm: ByteArray, gain: Float): ByteArray {
        if (gain <= 0.001f) return ByteArray(pcm.size)
        val output = ByteArray(pcm.size)
        val count = pcm.size / 2
        for (i in 0 until count) {
            val idx = i * 2
            val low = pcm[idx].toInt() and 0xFF
            val high = pcm[idx + 1].toInt()
            val sample = ((high shl 8) or low).toShort()
            val scaled = (sample * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            output[idx] = (scaled.toInt() and 0xFF).toByte()
            output[idx + 1] = ((scaled.toInt() shr 8) and 0xFF).toByte()
        }
        return output
    }

    private fun releaseTrackLocked() {
        playbackRunning = false
        playbackGeneration++
        playbackThread?.interrupt()
        playbackThread = null
        runCatching { track?.stop() }
        track?.release()
        track = null
        trackUsage = -1
        framesWritten = 0
    }

    companion object {
        private const val TAG = "LiveAudioIO"
        const val IN_RATE = 16_000
        const val OUT_RATE = 24_000

        /** 100 ms of 16 kHz mono PCM16. */
        const val CHUNK_BYTES = IN_RATE / 10 * 2

        /** Less than 20 ms left in the speaker counts as finished. */
        private const val STILL_PLAYING_FRAMES = OUT_RATE / 50

        /** Chunks read when checking that an audio source is not muted (the HAL may start with a little silence). */
        private const val PROBE_CHUNKS = 4

        /** Bluetooth hands-free can take about a second to start delivering the car's mic. */
        private const val COMM_PROBE_CHUNKS = 15

        /** Call volume is raised to at least this share of its maximum during a lesson. */
        private const val MIN_VOICE_VOLUME_FRACTION = 0.8

        /**
         * Output for the voice-call route, best first. Bluetooth hands-free covers cars (incl.
         * Android Auto) and earbuds; the earpiece is never chosen — it is far too quiet.
         */
        private val COMMUNICATION_DEVICE_PRIORITY = listOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_HEARING_AID,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        )

        /** Best call output among [available] device types, or null if only unsuitable ones exist. */
        fun pickCommunicationDeviceType(available: List<Int>): Int? =
            COMMUNICATION_DEVICE_PRIORITY.firstOrNull { it in available }

        fun deviceName(type: Int): String = when (type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BT_HANDSFREE"
            AudioDeviceInfo.TYPE_BLE_HEADSET -> "BLE_HEADSET"
            AudioDeviceInfo.TYPE_BLE_SPEAKER -> "BLE_SPEAKER"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB_HEADSET"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "WIRED_HEADSET"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "WIRED_HEADPHONES"
            AudioDeviceInfo.TYPE_HEARING_AID -> "HEARING_AID"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "SPEAKER"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "EARPIECE"
            else -> "TYPE_$type"
        }
    }
}
