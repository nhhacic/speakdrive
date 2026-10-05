package com.speakdrive.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.AudioDeviceInfoBuilder
import org.robolectric.shadows.ShadowAudioManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LiveAudioIOTest {

    private lateinit var context: Context
    private lateinit var audioManager: AudioManager
    private lateinit var shadowAudioManager: ShadowAudioManager
    private lateinit var liveAudio: LiveAudioIO

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        shadowAudioManager = shadowOf(audioManager)
        liveAudio = LiveAudioIO(context)
    }

    @Test
    fun constants_areCorrectForLiveConversation() {
        assertThat(LiveAudioIO.IN_RATE).isEqualTo(16_000)
        assertThat(LiveAudioIO.OUT_RATE).isEqualTo(24_000)
        // 100 ms of 16 kHz mono PCM16 = 1600 samples * 2 bytes = 3200 bytes
        assertThat(LiveAudioIO.CHUNK_BYTES).isEqualTo(3200)
    }

    @Test
    fun isHeadsetConnected_returnsFalseWhenNoExternalDevices() {
        assertThat(liveAudio.isHeadsetConnected()).isFalse()
        assertThat(liveAudio.isCarAudioConnected()).isFalse()
    }

    @Test
    fun isHeadsetConnected_detectsBluetoothA2dpCarAudio() {
        val bluetoothA2dp = AudioDeviceInfoBuilder.newBuilder()
            .setType(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            .build()
        shadowAudioManager.setOutputDevices(listOf(bluetoothA2dp))

        assertThat(liveAudio.isHeadsetConnected()).isTrue()
        assertThat(liveAudio.isCarAudioConnected()).isTrue()
    }

    @Test
    fun isCarAudioConnected_detectsAuxLine() {
        val auxDevice = AudioDeviceInfoBuilder.newBuilder()
            .setType(AudioDeviceInfo.TYPE_AUX_LINE)
            .build()
        shadowAudioManager.setOutputDevices(listOf(auxDevice))

        assertThat(liveAudio.isHeadsetConnected()).isTrue()
        assertThat(liveAudio.isCarAudioConnected()).isTrue()
    }

    @Test
    fun isHeadsetConnected_detectsWiredHeadset() {
        val wiredHeadset = AudioDeviceInfoBuilder.newBuilder()
            .setType(AudioDeviceInfo.TYPE_WIRED_HEADSET)
            .build()
        shadowAudioManager.setOutputDevices(listOf(wiredHeadset))

        assertThat(liveAudio.isHeadsetConnected()).isTrue()
        // Wired headset is not car audio
        assertThat(liveAudio.isCarAudioConnected()).isFalse()
    }

    @Test
    fun initialPlayingState_isFalse() {
        assertThat(liveAudio.isPlaying()).isFalse()
    }

    @Test
    fun flushPlayback_resetsPlayingStateToFalse() {
        liveAudio.flushPlayback()
        assertThat(liveAudio.isPlaying()).isFalse()
    }

    @Test
    fun pickCommunicationDevice_prefersCarHandsFreeOverSpeaker() {
        val picked = LiveAudioIO.pickCommunicationDeviceType(
            listOf(
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            )
        )
        assertThat(picked).isEqualTo(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
    }

    @Test
    fun pickCommunicationDevice_phoneAloneUsesLoudspeakerNotEarpiece() {
        val picked = LiveAudioIO.pickCommunicationDeviceType(
            listOf(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
        )
        assertThat(picked).isEqualTo(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
    }

    @Test
    fun pickCommunicationDevice_wiredHeadsetBeatsSpeaker() {
        val picked = LiveAudioIO.pickCommunicationDeviceType(
            listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_WIRED_HEADSET)
        )
        assertThat(picked).isEqualTo(AudioDeviceInfo.TYPE_WIRED_HEADSET)
    }

    @Test
    fun pickCommunicationDevice_onlyEarpieceGivesNull() {
        assertThat(LiveAudioIO.pickCommunicationDeviceType(listOf(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE))).isNull()
        assertThat(LiveAudioIO.pickCommunicationDeviceType(emptyList())).isNull()
    }

    @Test
    fun isEchoCancelled_falseBeforeCapture() {
        assertThat(liveAudio.isEchoCancelled()).isFalse()
    }
}
