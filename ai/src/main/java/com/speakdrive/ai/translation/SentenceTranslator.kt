package com.speakdrive.ai.translation

import android.util.Log
import android.util.LruCache
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.speakdrive.ai.BuildConfig
import com.speakdrive.ai.drill.DrillSentenceManager
import com.speakdrive.ai.model.AppLanguage
import com.speakdrive.ai.pronunciation.PronunciationDrill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Intelligent real-time translator for practice and repeat drill sentences.
 * Provides zero-latency lookup for curated sentences and cached queries,
 * with fast on-the-fly AI translation via Gemini Flash for dynamically generated sentences.
 */
@Singleton
class SentenceTranslator @Inject constructor(
    private val drillSentenceManager: DrillSentenceManager
) {
    // In-memory LRU cache: key = "langCode:normalizedKey" -> translation
    private val cache = LruCache<String, String>(250)

    /**
     * Resolves the target language name (for AI prompt) and language code from AppLanguage preference.
     */
    fun resolveTargetLanguage(appLanguage: AppLanguage): Pair<String, String> {
        val isVi = appLanguage == AppLanguage.VIETNAMESE ||
            (appLanguage == AppLanguage.SYSTEM && Locale.getDefault().language.equals("vi", ignoreCase = true))

        return when {
            isVi -> "Vietnamese" to "vi"
            appLanguage == AppLanguage.ENGLISH -> "English" to "en"
            appLanguage == AppLanguage.SPANISH -> "Spanish" to "es"
            appLanguage == AppLanguage.JAPANESE -> "Japanese" to "ja"
            appLanguage == AppLanguage.KOREAN -> "Korean" to "ko"
            appLanguage == AppLanguage.CHINESE -> "Chinese (Simplified)" to "zh"
            appLanguage == AppLanguage.FRENCH -> "French" to "fr"
            appLanguage == AppLanguage.GERMAN -> "German" to "de"
            else -> "Vietnamese" to "vi"
        }
    }

    /**
     * Synchronous instant lookup: checks in-memory cache and curated offline drill sentences.
     * Returns null if not cached yet.
     */
    fun getInstantTranslation(text: String, appLanguage: AppLanguage): String? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null

        val (langName, langCode) = resolveTargetLanguage(appLanguage)
        if (langCode == "en") return null

        val normKey = PronunciationDrill.key(trimmed)
        val cacheKey = "$langCode:$normKey"

        // 1. Memory Cache
        cache.get(cacheKey)?.let { return it }

        // 2. Curated Offline Library (for Vietnamese)
        if (langCode == "vi") {
            val curated = drillSentenceManager.findTranslationVi(trimmed)
            if (curated != null) {
                cache.put(cacheKey, curated)
                return curated
            }
        }
        return null
    }

    /**
     * Translates a practice sentence into the target language.
     * Tries instant cache first, then calls Gemini Flash Text model asynchronously.
     */
    suspend fun translate(text: String, appLanguage: AppLanguage): String? = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return@withContext null

        val (langName, langCode) = resolveTargetLanguage(appLanguage)
        if (langCode == "en") return@withContext null

        val normKey = PronunciationDrill.key(trimmed)
        val cacheKey = "$langCode:$normKey"

        // 1. Check instant cache / curated
        val instant = getInstantTranslation(trimmed, appLanguage)
        if (instant != null) return@withContext instant

        // 2. Call Gemini Flash Text model
        return@withContext try {
            val model = Firebase.ai(backend = GenerativeBackend.googleAI())
                .generativeModel(modelName = BuildConfig.TEXT_MODEL)

            val prompt = """
                You are a concise, accurate translator for an English language learning app.
                Translate the following short English practice sentence into natural $langName.
                Requirements:
                - Output ONLY the raw translated sentence.
                - Do NOT include quotes, explanations, pronunciation hints, or alternative meanings.
                
                English: $trimmed
            """.trimIndent()

            val response = model.generateContent(prompt)
            val translated = response.text?.trim()?.trim('"', '\'', '“', '”', '`', '\n')
            if (!translated.isNullOrBlank()) {
                cache.put(cacheKey, translated)
                translated
            } else {
                null
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to translate drill target '$trimmed' to $langName: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "SentenceTranslator"
    }
}
