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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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

        // 2. Call Gemini Flash Text model with automatic fallback
        val prompt = """
            You are a concise, accurate translator for an English language learning app.
            Translate the following short English practice sentence into natural $langName.
            Requirements:
            - Output ONLY the raw translated sentence.
            - Do NOT include quotes, explanations, pronunciation hints, or alternative meanings.
            
            English: $trimmed
        """.trimIndent()

        val candidateModels = listOf(
            BuildConfig.TEXT_MODEL,
            "gemini-3.5-flash-lite",
            "gemini-2.5-flash"
        ).distinct()
        for (modelName in candidateModels) {
            try {
                val model = Firebase.ai(backend = GenerativeBackend.googleAI())
                    .generativeModel(modelName = modelName)
                // A subtitle that arrives after the learner moved on is useless: give up quickly.
                val response = withTimeoutOrNull(TRANSLATION_TIMEOUT_MS) { model.generateContent(prompt) } ?: continue
                val translated = response.text?.trim()?.trim('"', '\'', '“', '”', '`', '\n')
                if (!translated.isNullOrBlank()) {
                    cache.put(cacheKey, translated)
                    return@withContext translated
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Translation attempt with $modelName failed for '$trimmed': ${e.message}")
                // Key / App Check problems fail the same way for every model.
                val message = e.message.orEmpty().lowercase()
                if ("403" in message || "permission" in message || "app check" in message || "api key" in message) break
            }
        }
        return@withContext null
    }

    /**
     * Smart local fallback translator (0ms latency, 100% offline).
     * Provides natural Vietnamese meaning for dynamic sentences generated by Gemini Live
     * when the curated exact match is missing or cloud translation is delayed/unavailable.
     */
    private fun containsWords(text: String, piece: String): Boolean =
        Regex("(?<![a-z'])" + Regex.escape(piece) + "(?![a-z'])").containsMatchIn(text)

    fun generateInstantFallbackTranslation(text: String, appLanguage: AppLanguage): String? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null

        val (langName, langCode) = resolveTargetLanguage(appLanguage)
        if (langCode != "vi") return null

        val normKey = PronunciationDrill.key(trimmed)
        val cacheKey = "$langCode:$normKey"

        cache.get(cacheKey)?.let { return it }

        // Check exact match in dictionary lookup
        KNOWN_PHRASES[normKey]?.let {
            cache.put(cacheKey, it)
            return it
        }

        // Substring & clause matching for composite sentences
        val lower = trimmed.lowercase(Locale.US).trimEnd('.', '!', '?', ',', ' ', '"', '“', '”')

        // Special patterns matching (whole words: "arm" must not match "alarm"). These are guesses, so
        // they are not cached: the cloud translation still runs and replaces them.
        for ((patternKey, viTranslation) in KNOWN_PATTERNS) {
            if (patternKey.all { containsWords(lower, it) }) return viTranslation
        }

        // Common formulaic survival & conversational starters
        for ((starter, translationPrefix) in SENTENCE_STARTERS) {
            if (lower.startsWith(starter)) {
                val remainder = lower.removePrefix(starter).trim()
                val remainderVi = KNOWN_PHRASES[PronunciationDrill.key(remainder)]
                    ?: KNOWN_WORDS[remainder]
                if (remainderVi != null) {
                    return "$translationPrefix $remainderVi".replaceFirstChar { it.uppercase() }
                }
            }
        }

        return null
    }

    companion object {
        private const val TAG = "SentenceTranslator"
        private const val TRANSLATION_TIMEOUT_MS = 15_000L

        // Common high-frequency full phrases and idioms for 0ms subtitle delivery
        private val KNOWN_PHRASES = mapOf(
            // Survival & Adventure
            PronunciationDrill.key("The climber survived against all odds") to "Người leo núi đã sống sót bất chấp mọi khó khăn.",
            PronunciationDrill.key("Against all odds he found his way back") to "Vượt qua mọi nghịch cảnh, anh ấy đã tìm được đường trở về.",
            PronunciationDrill.key("He had to make a very tough decision") to "Anh ấy đã phải đưa ra một quyết định rất khó khăn.",
            PronunciationDrill.key("Never give up hope in extreme danger") to "Đừng bao giờ từ bỏ hy vọng trong hiểm nguy tột cùng.",
            PronunciationDrill.key("He was trapped in a remote canyon") to "Anh ấy bị mắc kẹt trong một hẻm núi hẻo lánh.",
            PronunciationDrill.key("Courage helped him overcome the ordeal") to "Lòng dũng cảm đã giúp anh ấy vượt qua nghịch cảnh.",
            PronunciationDrill.key("He stayed calm and focused on survival") to "Anh ấy giữ bình tĩnh và tập trung vào việc sống sót.",
            PronunciationDrill.key("Human will power can perform miracles") to "Ý chí con người có thể tạo nên những điều kỳ diệu.",
            PronunciationDrill.key("He managed to walk toward safety") to "Anh ấy đã gắng gượng bước đi về nơi an toàn.",
            PronunciationDrill.key("He drank water from melted snow") to "Anh ấy uống nước từ tuyết tan.",
            PronunciationDrill.key("Every second counts in an emergency") to "Từng giây phút đều quý giá trong tình huống khẩn cấp.",
            PronunciationDrill.key("He kept his spirits high despite the pain") to "Anh ấy vẫn giữ vững tinh thần bất chấp nỗi đau.",
            PronunciationDrill.key("They signaled for rescue at sunrise") to "Họ phát tín hiệu cầu cứu vào lúc bình minh.",
            PronunciationDrill.key("Staying hydrated is crucial for survival") to "Uống đủ nước là điều tối quan trọng để sinh tồn.",
            PronunciationDrill.key("Trust your instincts when danger strikes") to "Hãy tin vào trực giác khi hiểm nguy ập tới.",
            PronunciationDrill.key("He cut off his arm to save his life") to "Anh ấy đã tự cắt cánh tay để bảo toàn mạng sống.",
            PronunciationDrill.key("The rescue helicopter arrived just in time") to "Trực thăng cứu hộ đã đến kịp thời.",
            PronunciationDrill.key("He never lost his will to live") to "Anh ấy không bao giờ đánh mất ý chí sống.",
            PronunciationDrill.key("His story inspired millions of people") to "Câu chuyện của anh ấy đã truyền cảm hứng cho hàng triệu người.",
            PronunciationDrill.key("Survival is about mental toughness") to "Sinh tồn đòi hỏi sự kiên cường về tinh thần.",
            PronunciationDrill.key("He was trapped for five days") to "Anh ấy đã bị mắc kẹt suốt năm ngày.",
            PronunciationDrill.key("He had no other choice") to "Anh ấy không còn sự lựa chọn nào khác.",
            PronunciationDrill.key("Fight for your life") to "Hãy chiến đấu giành giật sự sống.",
            PronunciationDrill.key("A miracle happened") to "Một phép màu đã xảy ra.",
            PronunciationDrill.key("Safe and sound") to "Bình an vô sự.",
            PronunciationDrill.key("Against all odds") to "Bất chấp mọi nghịch cảnh khó khăn.",

            // Driving & Traffic Reflexes
            PronunciationDrill.key("Watch out for pedestrians") to "Hãy chú ý quan sát người đi bộ.",
            PronunciationDrill.key("Keep a safe following distance") to "Giữ khoảng cách an toàn với xe phía trước.",
            PronunciationDrill.key("Merge carefully into the highway") to "Nhập làn cẩn thận vào đường cao tốc.",
            PronunciationDrill.key("The road is slippery due to rain") to "Mặt đường đang trơn trượt do trời mưa.",
            PronunciationDrill.key("Check your blind spot before changing lanes") to "Kiểm tra điểm mù trước khi chuyển làn.",
            PronunciationDrill.key("Take the second exit at the roundabout") to "Đi theo lối ra thứ hai ở vòng xuyến.",
            PronunciationDrill.key("Traffic is backed up for two miles") to "Giao thông đang bị ùn tắc kéo dài hai dặm.",
            PronunciationDrill.key("Slow down near the school zone") to "Giảm tốc độ gần khu vực trường học.",

            // Conversational & Daily Life
            PronunciationDrill.key("Could you speak a bit slower") to "Bạn có thể nói chậm lại một chút được không?",
            PronunciationDrill.key("I didn't quite catch that") to "Tôi chưa nghe kịp câu vừa rồi.",
            PronunciationDrill.key("Let me rephrase that for you") to "Để tôi diễn đạt lại điều đó cho bạn.",
            PronunciationDrill.key("That makes a lot of sense") to "Điều đó rất hợp lý.",
            PronunciationDrill.key("I appreciate your help") to "Tôi rất cảm kích sự giúp đỡ của bạn.",
            PronunciationDrill.key("Take your time no rush") to "Cứ thong thả, không cần vội đâu."
        )

        // Compound key pattern matching: all elements must exist in the text
        private val KNOWN_PATTERNS = listOf(
            listOf("climber", "survived", "odds") to "Người leo núi đã sống sót bất chấp mọi khó khăn.",
            listOf("climber", "survived") to "Người leo núi đã sống sót kỳ diệu.",
            listOf("against all odds") to "Bất chấp mọi khó khăn nghịch cảnh.",
            listOf("trapped", "canyon") to "Bị mắc kẹt trong hẻm núi hoang vu.",
            listOf("cut off", "arm") to "Đã tự cắt cánh tay để bảo toàn tính mạng.",
            listOf("will to live") to "Ý chí sinh tồn và khao khát sống mãnh liệt.",
            listOf("tough decision") to "Đã đưa ra một quyết định vô cùng khó khăn.",
            listOf("rescue", "helicopter") to "Trực thăng cứu hộ đã xuất hiện kịp lúc.",
            listOf("melted snow") to "Uống nước từ tuyết tan để cầm cự.",
            listOf("stay", "calm") to "Hãy giữ bình tĩnh và tập trung.",
            listOf("never give up") to "Đừng bao giờ từ bỏ hy vọng.",
            listOf("every second counts") to "Từng giây phút đều quý giá trong tình huống khẩn cấp.",
            listOf("pedestrian", "crossing") to "Chú ý nhường đường cho người đi bộ qua đường.",
            listOf("blind spot") to "Quan sát kỹ điểm mù trước khi chuyển làn.",
            listOf("heavy traffic") to "Tình trạng giao thông đang rất đông đúc.",
            listOf("gas station") to "Trạm xăng gần nhất trên lộ trình.",
            listOf("split the bill") to "Chúng tôi muốn chia hóa đơn thanh toán.",
            listOf("lost passport") to "Báo mất hộ chiếu tại đại sứ quán."
        )

        private val SENTENCE_STARTERS = listOf(
            "repeat after me" to "Hãy nhắc lại theo tôi:",
            "make sure to" to "Hãy chắc chắn rằng bạn",
            "be careful with" to "Hãy cẩn thận với",
            "don't forget to" to "Đừng quên",
            "could you please" to "Bạn có thể vui lòng",
            "i would like to" to "Tôi muốn",
            "it is important to" to "Điều quan trọng là phải"
        )

        private val KNOWN_WORDS = mapOf(
            "the climber survived against all odds" to "Người leo núi đã sống sót bất chấp mọi khó khăn.",
            "stop the car" to "dừng xe lại",
            "take a break" to "nghỉ giải lao một lát",
            "fasten seatbelt" to "thắt dây an toàn",
            "call an ambulance" to "gọi xe cấp cứu"
        )
    }
}
