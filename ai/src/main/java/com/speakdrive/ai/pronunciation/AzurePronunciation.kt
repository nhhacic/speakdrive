package com.speakdrive.ai.pronunciation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** Region and key of an Azure AI Speech resource (Azure portal → resource → Keys and Endpoint). */
data class AzureSpeechConfig(val region: String, val key: String) {
    val isComplete: Boolean get() = region.isNotBlank() && key.isNotBlank()
}

data class AzurePhoneme(val phoneme: String, val accuracy: Int)

data class AzureWord(
    val word: String,
    val accuracy: Int,
    /** None, Omission, Insertion or Mispronunciation. */
    val errorType: String,
    val phonemes: List<AzurePhoneme>
) {
    /** Strict: a flagged word, or one pronounced noticeably off, needs work. */
    val needsWork: Boolean
        get() = errorType == ERROR_MISPRONUNCIATION || errorType == ERROR_OMISSION ||
            (errorType != ERROR_INSERTION && accuracy < AzureAssessment.WORD_PASS_SCORE)

    /** The weakest sounds of this word, e.g. "th 35". */
    fun weakestPhonemes(limit: Int = 2): List<AzurePhoneme> =
        phonemes.filter { it.accuracy < AzureAssessment.WORD_PASS_SCORE }.sortedBy { it.accuracy }.take(limit)

    companion object {
        const val ERROR_MISPRONUNCIATION = "Mispronunciation"
        const val ERROR_OMISSION = "Omission"
        const val ERROR_INSERTION = "Insertion"
    }
}

/** Azure Pronunciation Assessment of one attempt; scores are 0–100. */
data class AzureAssessment(
    val pronunciationScore: Int,
    val accuracyScore: Int,
    val fluencyScore: Int,
    val completenessScore: Int,
    val recognizedText: String,
    val words: List<AzureWord>
) {
    val problemWords: List<AzureWord> get() = words.filter { it.needsWork }

    val passed: Boolean
        get() = pronunciationScore >= PASS_SCORE && completenessScore >= PASS_SCORE && problemWords.isEmpty()

    /** "three (th 35, r 60)" — the form the AI and the summary use. */
    fun describeProblems(): List<String> = problemWords.map { word ->
        val sounds = word.weakestPhonemes().joinToString { "${it.phoneme} ${it.accuracy}" }
        when {
            word.errorType == AzureWord.ERROR_OMISSION -> "${word.word} (missing)"
            sounds.isNotEmpty() -> "${word.word} ($sounds)"
            else -> "${word.word} (${word.accuracy}/100)"
        }
    }

    companion object {
        /** Overall and completeness score needed to pass. Strict on purpose. */
        const val PASS_SCORE = 80

        /** Every word must reach this accuracy. */
        const val WORD_PASS_SCORE = 60
    }
}

/** Grades pronunciation against a reference sentence. Blocking; call off the main thread. */
interface PronunciationAssessor {
    fun assess(referenceText: String, pcm16kMono: ByteArray, config: AzureSpeechConfig): AzureAssessment

    /** Checks the region and key without sending any audio. */
    fun testConnection(config: AzureSpeechConfig): Result<Unit>
}

/** Azure AI Speech "REST API for short audio" with the Pronunciation-Assessment header. */
@Singleton
class AzurePronunciationAssessor @Inject constructor() : PronunciationAssessor {

    override fun assess(referenceText: String, pcm16kMono: ByteArray, config: AzureSpeechConfig): AzureAssessment {
        require(config.isComplete) { "Azure Speech region/key not set" }
        val params = buildJsonObject {
            put("ReferenceText", referenceText)
            put("GradingSystem", "HundredMark")
            put("Granularity", "Phoneme")
            put("Dimension", "Comprehensive")
            put("EnableMiscue", "True")
        }.toString()
        val url = URL(
            "https://${config.region.trim()}.stt.speech.microsoft.com/speech/recognition/conversation/cognitiveservices/v1" +
                "?language=en-US&format=detailed"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("Ocp-Apim-Subscription-Key", config.key.trim())
            setRequestProperty("Content-Type", "audio/wav; codecs=audio/pcm; samplerate=16000")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Pronunciation-Assessment", Base64.getEncoder().encodeToString(params.toByteArray()))
        }
        try {
            connection.outputStream.use { it.write(Wav.pcm16Mono(pcm16kMono, sampleRate = 16_000)) }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw AzureSpeechException("Azure Speech HTTP $code: ${body.take(200)}")
            return AzureResponseParser.parse(body)
        } finally {
            connection.disconnect()
        }
    }

    override fun testConnection(config: AzureSpeechConfig): Result<Unit> = runCatching {
        require(config.isComplete) { "Chưa nhập Region hoặc Key" }
        val region = URLEncoder.encode(config.region.trim(), "UTF-8")
        val connection = (URL("https://$region.api.cognitive.microsoft.com/sts/v1.0/issueToken").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("Ocp-Apim-Subscription-Key", config.key.trim())
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        try {
            connection.outputStream.use { it.write(ByteArray(0)) }
            val code = connection.responseCode
            if (code !in 200..299) throw AzureSpeechException(
                when (code) {
                    401 -> "Key không đúng hoặc không thuộc region này (HTTP 401)"
                    403 -> "Bị từ chối (HTTP 403): kiểm tra resource còn hoạt động"
                    else -> "Azure trả về HTTP $code"
                }
            )
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000
    }
}

class AzureSpeechException(message: String) : Exception(message)

/** Parses the detailed recognition result. Handles both the flat and the nested score layouts. */
object AzureResponseParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): AzureAssessment {
        val root = json.parseToJsonElement(body).jsonObject
        val status = root["RecognitionStatus"]?.jsonPrimitive?.content
        if (status != null && status != "Success") {
            // Silence or noise: nothing was pronounced.
            return AzureAssessment(0, 0, 0, 0, "", emptyList())
        }
        val best = (root["NBest"] as? JsonArray)?.firstOrNull()?.jsonObject
            ?: throw AzureSpeechException("Azure response has no NBest result")
        val scores = (best["PronunciationAssessment"] as? JsonObject) ?: best
        val words = (best["Words"] as? JsonArray).orEmpty().map { element ->
            val word = element.jsonObject
            val wordScores = (word["PronunciationAssessment"] as? JsonObject) ?: word
            AzureWord(
                word = word.string("Word"),
                accuracy = wordScores.score("AccuracyScore"),
                errorType = wordScores["ErrorType"]?.jsonPrimitive?.content ?: "None",
                phonemes = (word["Phonemes"] as? JsonArray).orEmpty().map { p ->
                    val phoneme = p.jsonObject
                    val phonemeScores = (phoneme["PronunciationAssessment"] as? JsonObject) ?: phoneme
                    AzurePhoneme(phoneme.string("Phoneme"), phonemeScores.score("AccuracyScore"))
                }
            )
        }
        return AzureAssessment(
            pronunciationScore = scores.score("PronScore"),
            accuracyScore = scores.score("AccuracyScore"),
            fluencyScore = scores.score("FluencyScore"),
            completenessScore = scores.score("CompletenessScore"),
            recognizedText = best.string("Display").ifEmpty { best.string("Lexical") },
            words = words
        )
    }

    private fun JsonObject.string(key: String): String = this[key]?.jsonPrimitive?.content.orEmpty()

    private fun JsonObject.score(key: String): Int =
        (this[key]?.jsonPrimitive?.doubleOrNull ?: 0.0).roundToInt().coerceIn(0, 100)

    private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
}

/** Wraps raw PCM in a WAV header. */
object Wav {
    fun pcm16Mono(pcm: ByteArray, sampleRate: Int): ByteArray {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(36 + pcm.size)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1) // PCM
            putShort(1) // mono
            putInt(sampleRate)
            putInt(sampleRate * 2)
            putShort(2)
            putShort(16)
            put("data".toByteArray())
            putInt(pcm.size)
        }
        return ByteArrayOutputStream(44 + pcm.size).apply {
            write(header.array())
            write(pcm)
        }.toByteArray()
    }
}
