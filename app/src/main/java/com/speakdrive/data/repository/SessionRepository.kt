package com.speakdrive.data.repository

import com.speakdrive.ai.model.CompletedSession
import com.speakdrive.ai.model.Correction
import com.speakdrive.ai.model.CustomScenario
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.LearnerMemory
import com.speakdrive.ai.model.MistakeReviewResult
import com.speakdrive.ai.model.ReviewMistake
import com.speakdrive.ai.model.ReviewWord
import com.speakdrive.ai.model.SessionMode
import com.speakdrive.ai.model.SessionSummary
import com.speakdrive.ai.session.SessionStore
import com.speakdrive.data.local.dao.MemoryDao
import com.speakdrive.data.local.dao.SessionDao
import com.speakdrive.data.local.dao.WordDao
import com.speakdrive.data.local.entity.CorrectionEntity
import com.speakdrive.data.local.entity.LearnedWordEntity
import com.speakdrive.data.local.entity.LearnerFactEntity
import com.speakdrive.data.local.entity.MessageEntity
import com.speakdrive.data.local.entity.MistakeEntity
import com.speakdrive.data.local.entity.PronunciationAttemptEntity
import com.speakdrive.data.local.entity.SessionEntity
import com.speakdrive.data.scenario.CustomScenarioManager
import com.speakdrive.domain.MemoryText
import com.speakdrive.domain.SpacedRepetition
import com.speakdrive.domain.WeeklyDigestGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class SessionDetail(
    val session: SessionEntity,
    val messages: List<MessageEntity>,
    val corrections: List<CorrectionEntity>,
    val words: List<LearnedWordEntity>,
    val attempts: List<PronunciationAttemptEntity> = emptyList()
)

/** Stores lessons and the vocabulary learned in them. */
@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val wordDao: WordDao,
    private val memoryDao: MemoryDao,
    private val weeklyDigestGenerator: WeeklyDigestGenerator? = null,
    private val customScenarioManager: CustomScenarioManager? = null
) : SessionStore {

    /** Replaceable in tests. */
    internal var clock: () -> Long = System::currentTimeMillis

    override suspend fun saveSession(session: CompletedSession) {
        val summary = session.summary
        val entity = SessionEntity(
            id = session.id,
            topicId = session.topicId,
            scenarioId = session.scenarioId,
            level = session.level.name,
            mode = session.mode.name,
            startedAt = session.startedAt,
            endedAt = session.endedAt,
            activeDurationMs = session.activeDurationMs,
            fluencyScore = summary?.fluencyScore,
            grammarScore = summary?.grammarScore,
            vocabularyScore = summary?.vocabularyScore,
            encouragement = summary?.encouragement,
            nextSuggestion = summary?.nextSuggestion,
            isCompleted = session.isCompleted,
            pronunciationScore = summary?.pronunciationScore,
            recommendedLevel = summary?.levelRecommendation?.targetLevel?.name,
            levelRecommendationDirection = summary?.levelRecommendation?.direction?.name,
            levelRecommendationReason = summary?.levelRecommendation?.reasonVi,
            wordsPerMinute = summary?.fluencyMetrics?.wordsPerMinute,
            fillerWordsCount = summary?.fluencyMetrics?.fillerWordsCount,
            fillerWordsRatio = summary?.fluencyMetrics?.fillerWordsRatio,
            meanLengthOfUtterance = summary?.fluencyMetrics?.meanLengthOfUtterance,
            vietnameseWordsRatio = summary?.fluencyMetrics?.vietnameseWordsRatio,
            comprehensionScore = summary?.comprehensionScore,
            ieltsBandScore = summary?.ieltsEvaluation?.overallBand
        )
        val messages = session.transcript.mapIndexed { index, turn ->
            MessageEntity(sessionId = session.id, speaker = turn.speaker.name, text = turn.text, timestamp = turn.timestamp, position = index)
        }
        val corrections = summary?.corrections.orEmpty().map {
            CorrectionEntity(sessionId = session.id, original = it.original, corrected = it.corrected, explanation = it.explanation)
        }
        val now = clock()
        val words = summary?.newWords.orEmpty().map {
            LearnedWordEntity(
                word = it.word,
                normalizedWord = normalize(it.word),
                meaning = it.meaning,
                exampleSentence = it.example,
                sessionId = session.id,
                learnedAt = now,
                reviewCount = 0,
                nextReviewAt = SpacedRepetition.nextReviewAt(reviewCount = 0, from = now),
                isCollocation = it.isCollocation
            )
        }
        val attempts = session.pronunciationAttempts.map {
            PronunciationAttemptEntity(
                sessionId = session.id,
                target = it.target,
                heard = it.heard,
                accuracyPercent = it.accuracyPercent,
                passed = it.passed,
                attemptNumber = it.attemptNumber,
                modelSaidCorrect = it.modelSaidCorrect,
                problemWords = it.problemWords.joinToString("|"),
                notes = listOfNotNull(it.modelNotes.takeIf { n -> n.isNotBlank() }, it.azureError).joinToString(" • "),
                timestamp = it.timestamp,
                azurePronScore = it.azure?.pronunciationScore,
                azureAccuracy = it.azure?.accuracyScore,
                azureFluency = it.azure?.fluencyScore,
                azureCompleteness = it.azure?.completenessScore,
                azureWeakSounds = it.azure?.describeProblems()?.joinToString("|")
            )
        }
        sessionDao.saveFullSession(entity, messages, corrections, words, attempts)
        if (summary != null) rememberFrom(session, summary)
    }

    /**
     * Keeps the lesson's mistakes for later review and the facts the learner shared. A mistake made again
     * in a later lesson goes back to the start of its review schedule.
     */
    private suspend fun rememberFrom(session: CompletedSession, summary: SessionSummary) {
        val now = clock()
        // In a pronunciation drill the "mistake" is what speech recognition heard, not something to review.
        if (session.mode != SessionMode.REPEAT_AFTER_ME) {
            val mistakes = summary.corrections
                .filter { MemoryText.normalize(it.original).isNotEmpty() && it.corrected.isNotBlank() }
                .distinctBy { MemoryText.normalize(it.original) }
            val existing = memoryDao.findMistakes(mistakes.map { MemoryText.normalize(it.original) })
                .associateBy { it.normalizedOriginal }
            mistakes.forEach { correction ->
                val key = MemoryText.normalize(correction.original)
                val old = existing[key]
                when {
                    old == null -> memoryDao.insertMistake(
                        MistakeEntity(
                            original = correction.original.trim(),
                            normalizedOriginal = key,
                            corrected = correction.corrected.trim(),
                            explanation = correction.explanation.trim(),
                            sessionId = session.id,
                            createdAt = now,
                            nextReviewAt = SpacedRepetition.nextReviewAt(reviewCount = 0, from = now)
                        )
                    )
                    // The same lesson saved twice must not count the mistake twice.
                    old.sessionId == session.id -> Unit
                    else -> memoryDao.updateMistake(
                        old.copy(
                            corrected = correction.corrected.trim(),
                            explanation = correction.explanation.trim().ifEmpty { old.explanation },
                            sessionId = session.id,
                            reviewCount = 0,
                            nextReviewAt = SpacedRepetition.nextReviewAt(reviewCount = 0, from = now),
                            timesMade = old.timesMade + 1
                        )
                    )
                }
            }
        }
        val facts = summary.learnerFacts
            .map { it.trim() }
            .filter { MemoryText.normalize(it).isNotEmpty() }
            .distinctBy { MemoryText.normalize(it) }
        if (facts.isNotEmpty()) {
            memoryDao.insertFacts(
                facts.map { LearnerFactEntity(fact = it, normalizedFact = MemoryText.normalize(it), sessionId = session.id, createdAt = now) }
            )
            memoryDao.trimFacts(MAX_STORED_FACTS)
        }
    }

    override suspend fun recentTopicIds(limit: Int): List<String> = sessionDao.recentTopicIds(limit)

    override suspend fun recentStorySessions(limit: Int): List<CompletedSession> {
        val entities = sessionDao.recentStorySessions(limit)
        return entities.map { entity ->
            CompletedSession(
                id = entity.id,
                topicId = entity.topicId,
                scenarioId = entity.scenarioId,
                level = DifficultyLevel.fromStored(entity.level),
                mode = SessionMode.STORY_LISTENING,
                startedAt = entity.startedAt,
                endedAt = entity.endedAt,
                activeDurationMs = entity.activeDurationMs,
                transcript = emptyList(),
                summary = null,
                reviewedWords = emptyList(),
                isCompleted = entity.isCompleted
            )
        }
    }

    override suspend fun latestUnfinishedStorySession(): CompletedSession? {
        val entity = sessionDao.latestUnfinishedStorySession() ?: return null
        val messageEntities = sessionDao.getMessagesForSession(entity.id)
        val transcript = messageEntities.map { msg ->
            com.speakdrive.ai.model.TranscriptTurn(
                id = msg.id,
                speaker = if (msg.speaker == "USER") com.speakdrive.ai.model.Speaker.USER else com.speakdrive.ai.model.Speaker.AI,
                text = msg.text,
                timestamp = msg.timestamp
            )
        }
        return CompletedSession(
            id = entity.id,
            topicId = entity.topicId,
            scenarioId = entity.scenarioId,
            level = DifficultyLevel.fromStored(entity.level),
            mode = SessionMode.STORY_LISTENING,
            startedAt = entity.startedAt,
            endedAt = entity.endedAt,
            activeDurationMs = entity.activeDurationMs,
            transcript = transcript,
            summary = null,
            reviewedWords = emptyList(),
            isCompleted = false
        )
    }

    override suspend fun recentDrillTargets(limit: Int): List<String> =
        sessionDao.recentDrillTargets(limit)

    override suspend fun wordsDueForReview(limit: Int): List<ReviewWord> =
        wordDao.dueWords(clock(), limit).map { ReviewWord(it.word, it.meaning) }

    override suspend fun recentWords(limit: Int): List<ReviewWord> =
        wordDao.recentWords(limit).map { ReviewWord(it.word, it.meaning) }

    override suspend fun wordsByWordNames(words: List<String>): List<ReviewWord> =
        wordDao.findByNormalized(words.map(::normalize)).map { ReviewWord(it.word, it.meaning) }

    override suspend fun mistakesDueForReview(limit: Int): List<ReviewMistake> =
        memoryDao.dueMistakes(clock(), limit).map { it.toReviewMistake() }

    override suspend fun recentMistakes(limit: Int): List<ReviewMistake> =
        memoryDao.recentMistakes(limit).map { it.toReviewMistake() }

    override suspend fun recordMistakeReviews(results: List<MistakeReviewResult>) {
        if (results.isEmpty()) return
        val now = clock()
        val byId = memoryDao.mistakesByIds(results.map { it.mistakeId }).associateBy { it.id }
        results.forEach { result ->
            val mistake = byId[result.mistakeId] ?: return@forEach
            val count = if (result.fixed) mistake.reviewCount + 1 else 0
            memoryDao.updateMistake(
                mistake.copy(
                    reviewCount = count,
                    nextReviewAt = SpacedRepetition.nextReviewAt(reviewCount = count, from = now),
                    lastReviewedAt = now
                )
            )
        }
    }

    override suspend fun learnerMemory(): LearnerMemory {
        val mistakes = memoryDao.activeMistakes(MASTERED_REVIEWS, MEMORY_MISTAKES)
            .map { Correction(it.original, it.corrected, it.explanation) }
        // Words failed in at least two drill attempts recently, most often failed first.
        val weakWords = memoryDao.recentProblemWords(RECENT_FAILED_ATTEMPTS)
            .flatMap { it.split('|') }
            .map { it.trim().lowercase() }
            .filter { it.length > 1 }
            .groupingBy { it }
            .eachCount()
            .filterValues { it >= 2 }
            .entries
            .sortedByDescending { it.value }
            .take(MEMORY_WEAK_WORDS)
            .map { it.key }
        val facts = memoryDao.recentFacts(MEMORY_FACTS).map { it.fact }
        return LearnerMemory(recurringMistakes = mistakes, weakWords = weakWords, facts = facts)
    }

    override suspend fun weeklyDigest(now: Long): String? {
        val generator = weeklyDigestGenerator ?: WeeklyDigestGenerator(sessionDao, wordDao)
        val stats = generator.generateDigest(now)
        return stats.spokenSpokenSummaryVi
    }

    override suspend fun saveCustomScenario(
        titleVi: String,
        titleEn: String,
        aiRole: String,
        learnerRole: String,
        customContext: String,
        missionObjective: String?
    ): CustomScenario? {
        return customScenarioManager?.createScenario(
            titleVi = titleVi,
            titleEn = titleEn,
            aiRole = aiRole,
            learnerRole = learnerRole,
            customContext = customContext,
            missionObjective = missionObjective
        )
    }

    override suspend fun customScenarios(): List<CustomScenario> {
        return customScenarioManager?.getCustomScenarios().orEmpty()
    }

    override suspend fun deleteCustomScenario(id: String) {
        customScenarioManager?.deleteScenario(id)
    }

    fun observeDueMistakeCount(): Flow<Int> = memoryDao.observeDueMistakeCount(clock())

    fun observeMistakes(): Flow<List<MistakeEntity>> = memoryDao.observeAllMistakes()

    fun observeLearnerFacts(): Flow<List<LearnerFactEntity>> = memoryDao.observeFacts()

    suspend fun deleteMistake(id: Long) = memoryDao.deleteMistake(id)

    suspend fun deleteLearnerFact(id: Long) = memoryDao.deleteFact(id)

    suspend fun addLearnerFact(fact: String) {
        val trimmed = fact.trim()
        val normalized = MemoryText.normalize(trimmed)
        if (normalized.isNotBlank()) {
            memoryDao.insertFacts(
                listOf(
                    LearnerFactEntity(
                        fact = trimmed,
                        normalizedFact = normalized,
                        sessionId = null,
                        createdAt = clock()
                    )
                )
            )
            memoryDao.trimFacts(MAX_STORED_FACTS)
        }
    }

    /** Forgets every personal fact; mistakes stay because they drive the review schedule. */
    suspend fun forgetLearnerFacts() = memoryDao.deleteAllFacts()

    override suspend fun markWordsReviewed(words: List<String>) {
        if (words.isEmpty()) return
        val now = clock()
        wordDao.findByNormalized(words.map(::normalize)).forEach { word ->
            val count = word.reviewCount + 1
            wordDao.updateSchedule(word.id, count, SpacedRepetition.nextReviewAt(count, now))
        }
    }

    suspend fun addCustomWord(word: String, meaning: String, example: String = ""): Long {
        val now = clock()
        val normalized = normalize(word)
        val existing = wordDao.findByNormalized(listOf(normalized)).firstOrNull()
        val entity = LearnedWordEntity(
            id = existing?.id ?: 0,
            word = word.trim(),
            normalizedWord = normalized,
            meaning = meaning.trim(),
            exampleSentence = example.trim(),
            sessionId = null,
            learnedAt = existing?.learnedAt ?: now,
            reviewCount = existing?.reviewCount ?: 0,
            nextReviewAt = existing?.nextReviewAt ?: SpacedRepetition.nextReviewAt(0, now)
        )
        return wordDao.upsertWord(entity)
    }

    suspend fun deleteWord(id: Long) {
        wordDao.deleteWord(id)
    }

    suspend fun updateWordExample(id: Long, example: String) {
        wordDao.updateExample(id, example.trim())
    }

    suspend fun markWordMastered(id: Long) {
        val now = clock()
        // 5 reviews marks it mastered; schedule far out (e.g. 180 days)
        val farFuture = now + 180L * 24 * 60 * 60 * 1000
        wordDao.updateSchedule(id, reviewCount = 5, nextReviewAt = farFuture)
    }

    suspend fun resetWordSchedule(id: Long) {
        val now = clock()
        wordDao.updateSchedule(id, reviewCount = 0, nextReviewAt = now)
    }

    fun observeSessionDetail(sessionId: String): Flow<SessionDetail?> = combine(
        sessionDao.observeSession(sessionId),
        sessionDao.observeMessages(sessionId),
        sessionDao.observeCorrections(sessionId),
        wordDao.observeWordsForSession(sessionId),
        sessionDao.observeAttempts(sessionId)
    ) { session, messages, corrections, words, attempts ->
        session?.let { SessionDetail(it, messages, corrections, words, attempts) }
    }

    fun observeHistory(): Flow<List<SessionEntity>> = sessionDao.observeAllSessions()

    /** Number of corrections stored for each session, keyed by session id. */
    fun observeCorrectionCounts(): Flow<Map<String, Int>> =
        sessionDao.observeCorrectionCounts().map { rows -> rows.associate { it.sessionId to it.count } }

    fun observeAllWords(): Flow<List<LearnedWordEntity>> = wordDao.observeAllWords()

    /** Deletes every lesson, transcript and word stored on this device. */
    suspend fun deleteAllData() {
        sessionDao.deleteAllSessions()
        wordDao.deleteAll()
        memoryDao.deleteAllMistakes()
        memoryDao.deleteAllFacts()
    }

    private fun normalize(word: String) = word.trim().lowercase()

    private fun MistakeEntity.toReviewMistake() = ReviewMistake(id, original, corrected, explanation)

    private companion object {
        /** Reviews in a row after which a mistake counts as fixed and leaves the AI's notes. */
        const val MASTERED_REVIEWS = 4
        const val MEMORY_MISTAKES = 4
        const val MEMORY_WEAK_WORDS = 6
        const val MEMORY_FACTS = 8
        const val MAX_STORED_FACTS = 30
        const val RECENT_FAILED_ATTEMPTS = 60
    }
}
