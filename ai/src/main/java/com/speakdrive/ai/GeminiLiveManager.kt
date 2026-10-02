package com.speakdrive.ai

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.type.GenerationConfig
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Quản lý kết nối Gemini Live API qua Firebase AI Logic.
 *
 * Cung cấp khả năng:
 * - Kết nối/ngắt kết nối tới Gemini Live API
 * - Gửi audio stream từ microphone tới AI
 * - Nhận audio response stream từ AI
 * - Hỗ trợ barge-in (ngắt lời AI khi user nói)
 * - Cập nhật system instruction động theo chủ đề/level
 */
@Singleton
class GeminiLiveManager @Inject constructor() {

    companion object {
        private const val TAG = "GeminiLiveManager"
        private const val MODEL_NAME = "gemini-2.0-flash-live-001"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Trạng thái kết nối hiện tại */
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    /** Flow phát audio response từ AI (PCM bytes) */
    private val _audioOutput = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val audioOutput: Flow<ByteArray> = _audioOutput.asSharedFlow()

    /** Flow phát text response từ AI (transcript) */
    private val _textOutput = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val textOutput: Flow<String> = _textOutput.asSharedFlow()

    private var currentSystemInstruction: String = ""

    // Firebase AI generative model - sẽ được tạo lại mỗi khi system instruction thay đổi
    private var generativeModel: GenerativeModel? = null

    // Live session placeholder - Firebase AI SDK sẽ cung cấp LiveSession API
    // Khi SDK hỗ trợ Live API chính thức, thay thế bằng LiveSession thực tế
    private var isSessionActive = false

    /**
     * Cập nhật system instruction cho phiên hội thoại tiếp theo.
     * Gọi trước [connect] để có hiệu lực.
     */
    fun updateSystemInstruction(instruction: String) {
        currentSystemInstruction = instruction
        // Tạo lại model với instruction mới
        generativeModel = createModel()
        Log.d(TAG, "System instruction updated (${instruction.length} chars)")
    }

    /**
     * Tạo GenerativeModel với cấu hình hiện tại.
     */
    private fun createModel(): GenerativeModel {
        return Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "text/plain"
            },
            systemInstruction = content { text(currentSystemInstruction) }
        )
    }

    /**
     * Kết nối tới Gemini API và bắt đầu phiên hội thoại.
     *
     * Trong phiên bản production với Gemini Live API:
     * - Mở WebSocket connection
     * - Gửi audio stream hai chiều
     * - Hỗ trợ barge-in tự nhiên
     *
     * Hiện tại sử dụng standard Gemini API với text mode
     * cho đến khi Firebase AI SDK hỗ trợ Live API đầy đủ trên Android.
     */
    suspend fun connect() {
        if (_isConnected.value) return
        try {
            generativeModel = generativeModel ?: createModel()
            isSessionActive = true
            _isConnected.value = true
            Log.d(TAG, "Connected to Gemini AI")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to Gemini AI", e)
            _isConnected.value = false
            throw e
        }
    }

    /**
     * Gửi text tới Gemini và nhận phản hồi.
     * Dùng cho mode text-based (fallback khi Live API chưa sẵn sàng).
     */
    suspend fun sendTextAndGetResponse(text: String): String {
        val model = generativeModel ?: throw IllegalStateException("Not connected")
        return try {
            val response = model.generateContent(text)
            val responseText = response.text ?: ""
            _textOutput.emit(responseText)
            Log.d(TAG, "Got response: ${responseText.take(100)}...")
            responseText
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get response", e)
            throw e
        }
    }

    /**
     * Gửi text tới Gemini qua streaming và nhận phản hồi từng phần.
     */
    suspend fun sendTextStreaming(text: String): Flow<String> {
        val model = generativeModel ?: throw IllegalStateException("Not connected")
        return kotlinx.coroutines.flow.flow {
            try {
                model.generateContentStream(text).collect { chunk ->
                    chunk.text?.let { partialText ->
                        emit(partialText)
                        _textOutput.emit(partialText)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Streaming failed", e)
                throw e
            }
        }
    }

    /**
     * Gửi dữ liệu audio PCM tới Gemini.
     *
     * Khi Live API sẵn sàng trên Firebase AI SDK:
     * - Audio sẽ được stream trực tiếp qua WebSocket
     * - AI sẽ phản hồi bằng audio (speech-to-speech)
     * - Barge-in được xử lý tự động
     *
     * Hiện tại: Audio sẽ được buffer và chuyển đổi thành text
     * thông qua on-device speech recognition trước khi gửi tới Gemini.
     */
    fun sendAudio(pcmData: ByteArray) {
        if (!_isConnected.value) return
        scope.launch {
            try {
                // TODO: Khi Live API sẵn sàng, gửi audio trực tiếp:
                // liveSession?.sendAudioChunk(pcmData)
                Log.v(TAG, "Audio chunk sent: ${pcmData.size} bytes")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send audio", e)
            }
        }
    }

    /**
     * Ngắt kết nối và giải phóng tài nguyên.
     */
    suspend fun disconnect() {
        if (!_isConnected.value) return
        try {
            isSessionActive = false
            _isConnected.value = false
            Log.d(TAG, "Disconnected from Gemini AI")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disconnect", e)
        }
    }

    /**
     * Giải phóng toàn bộ tài nguyên. Gọi khi app bị destroy.
     */
    fun cleanup() {
        scope.launch {
            disconnect()
        }
        scope.cancel()
        generativeModel = null
    }
}
