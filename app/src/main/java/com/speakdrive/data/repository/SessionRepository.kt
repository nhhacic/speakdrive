package com.speakdrive.data.repository

import com.speakdrive.ai.model.CompletedSession
import com.speakdrive.ai.model.ReviewWord
import com.speakdrive.ai.session.SessionStore
import com.speakdrive.data.local.dao.SessionDao
import com.speakdrive.data.local.dao.WordDao
import com.speakdrive.data.local.entity.CorrectionEntity
import com.speakdrive.data.local.entity.LearnedWordEntity
import com.speakdrive.data.local.entity.MessageEntity
import com.speakdrive.data.local.entity.PronunciationAttemptEntity
import com.speakdrive.data.local.entity.SessionEntity
import com.speakdrive.domain.SpacedRepetition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
    private val wordDao: WordDao
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
            pronunciationScore = summary?.pronunciationScore
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
                nextReviewAt = SpacedRepetition.nextReviewAt(reviewCount = 0, from = now)
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
    }

    override suspend fun recentTopicIds(limit: Int): List<String> = sessionDao.recentTopicIds(limit)

    override suspend fun wordsDueForReview(limit: Int): List<ReviewWord> =
        wordDao.dueWords(clock(), limit).map { ReviewWord(it.word, it.meaning) }

    override suspend fun markWordsReviewed(words: List<String>) {
        if (words.isEmpty()) return
        val now = clock()
        wordDao.findByNormalized(words.map(::normalize)).forEach { word ->
            val count = word.reviewCount + 1
            wordDao.updateSchedule(word.id, count, SpacedRepetition.nextReviewAt(count, now))
        }
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

    fun observeAllWords(): Flow<List<LearnedWordEntity>> = wordDao.observeAllWords()

    /** Deletes every lesson, transcript and word stored on this device. */
    suspend fun deleteAllData() {
        sessionDao.deleteAllSessions()
        wordDao.deleteAll()
    }

    private fun normalize(word: String) = word.trim().lowercase()
}
