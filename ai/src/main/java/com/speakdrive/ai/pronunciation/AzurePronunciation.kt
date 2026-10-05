package com.speakdrive.ai.pronunciation

import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.PronunciationStrictness
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
    /**
     * Checks if this word needs practice based on the chosen strictness level.
     */
    fun needsWork(strictness: PronunciationStrictness = PronunciationStrictness.AUTO): Boolean {
        if (errorType == ERROR_MISPRONUNCIATION || errorType == ERROR_OMISSION) return true
        if (errorType == ERROR_INSERTION) return false
        val resolved = strictness.resolveForLevel(DifficultyLevel.INTERMEDIATE)
        val (wordThreshold, phonemeThreshold) = when (resolved) {
            PronunciationStrictness.BEGINNER -> 35 to 0
            PronunciationStrictness.ELEMENTARY -> 40 to 0
            PronunciationStrictness.PRE_INTERMEDIATE -> 45 to 35
            PronunciationStrictness.INTERMEDIATE -> 50 to 45
            PronunciationStrictness.UPPER_INTERMEDIATE -> 55 to 50
            PronunciationStrictness.ADVANCED -> AzureAssessment.WORD_PASS_SCORE to AzureAssessment.PHONEME_PASS_SCORE
            else -> 50 to 45
        }
        if (accuracy < wordThreshold) return true
        return phonemeThreshold > 0 && phonemes.any { it.accuracy < phonemeThreshold }
    }

    /**
     * Strict: a flagged word, a low word score, or any badly pronounced sound needs work.
     * The sound check matters: saying "tree" for "three" scored the WORD 91/100 with no error
     * type, while its sounds scored th 18 and r 42 (real Azure response, see test resources).
     */
    val needsWork: Boolean
        get() = needsWork(PronunciationStrictness.AUTO)

    /** The weakest sounds of this word, e.g. "th 35". */
    fun weakestPhonemes(
        limit: Int = 2,
        strictness: PronunciationStrictness = PronunciationStrictness.AUTO
    ): List<AzurePhoneme> {
        val resolved = strictness.resolveForLevel(DifficultyLevel.INTERMEDIATE)
        val threshold = when (resolved) {
            PronunciationStrictness.BEGINNER -> 35
            PronunciationStrictness.ELEMENTARY -> 40
            PronunciationStrictness.PRE_INTERMEDIATE -> 45
            PronunciationStrictness.INTERMEDIATE -> 50
            PronunciationStrictness.UPPER_INTERMEDIATE -> 55
            PronunciationStrictness.ADVANCED -> AzureAssessment.PHONEME_PASS_SCORE
            else -> 50
        }
        return phonemes.filter { it.accuracy < threshold }.sortedBy { it.accuracy }.take(limit)
    }

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
    fun problemWords(strictness: PronunciationStrictness = PronunciationStrictness.AUTO): List<AzureWord> =
        words.filter { it.needsWork(strictness) }

    val problemWords: List<AzureWord> get() = problemWords(PronunciationStrictness.AUTO)

    fun isPassed(strictness: PronunciationStrictness = PronunciationStrictness.AUTO): Boolean {
        val resolved = strictness.resolveForLevel(DifficultyLevel.INTERMEDIATE)
        val passThreshold = when (resolved) {
            PronunciationStrictness.BEGINNER -> 50
            PronunciationStrictness.ELEMENTARY -> 60
            PronunciationStrictness.PRE_INTERMEDIATE -> 65
            PronunciationStrictness.INTERMEDIATE -> 70
            PronunciationStrictness.UPPER_INTERMEDIATE -> 75
            PronunciationStrictness.ADVANCED -> PASS_SCORE
            else -> 70
        }
        return pronunciationScore >= passThreshold && completenessScore >= passThreshold && problemWords(strictness).isEmpty()
    }

    val passed: Boolean get() = isPassed(PronunciationStrictness.AUTO)

    /** "three (th 35, r 60)" — the form the AI and the summary use. */
    fun describeProblems(strictness: PronunciationStrictness = PronunciationStrictness.AUTO): List<String> =
        problemWords(strictness).map { word ->
            val sounds = word.weakestPhonemes(strictness = strictness).joinToString { "${it.phoneme} ${it.accuracy}" }
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

        /** Every sound in every word must reach this accuracy. */
        const val PHONEME_PASS_SCORE = 60
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
