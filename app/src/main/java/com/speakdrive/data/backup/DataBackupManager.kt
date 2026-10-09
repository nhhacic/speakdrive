package com.speakdrive.data.backup

import com.speakdrive.data.local.dao.MemoryDao
import com.speakdrive.data.local.dao.ScenarioDao
import com.speakdrive.data.local.dao.SessionDao
import com.speakdrive.data.local.dao.WordDao
import com.speakdrive.data.local.entity.CustomScenarioEntity
import com.speakdrive.data.local.entity.LearnedWordEntity
import com.speakdrive.data.local.entity.LearnerFactEntity
import com.speakdrive.data.local.entity.MistakeEntity
import com.speakdrive.data.local.entity.SessionEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataBackupManager @Inject constructor(
    private val sessionDao: SessionDao,
    private val wordDao: WordDao,
    private val memoryDao: MemoryDao,
    private val scenarioDao: ScenarioDao
) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    data class BackupData(
        val version: Int = 1,
        val exportedAt: Long = System.currentTimeMillis(),
        val sessions: List<SessionBackupDto> = emptyList(),
        val words: List<WordBackupDto> = emptyList(),
        val mistakes: List<MistakeBackupDto> = emptyList(),
        val facts: List<FactBackupDto> = emptyList(),
        val scenarios: List<ScenarioBackupDto> = emptyList()
    )

    @Serializable
    data class SessionBackupDto(
        val id: String,
        val topicId: String,
        val scenarioId: String?,
        val level: String,
        val mode: String,
        val startedAt: Long,
        val endedAt: Long,
        val activeDurationMs: Long,
        val fluencyScore: Int?,
        val grammarScore: Int?,
        val vocabularyScore: Int?,
        val encouragement: String? = null,
        val nextSuggestion: String? = null,
        val isCompleted: Boolean = true,
        val pronunciationScore: Int?,
        val wordsPerMinute: Int?,
        val fillerWordsCount: Int?,
        val fillerWordsRatio: Float?,
        val meanLengthOfUtterance: Float?,
        val vietnameseWordsRatio: Float?,
        val comprehensionScore: Int?,
        val ieltsBandScore: Float?
    )

    @Serializable
    data class WordBackupDto(
        val word: String,
        val normalizedWord: String,
        val meaning: String,
        val exampleSentence: String,
        val learnedAt: Long,
        val reviewCount: Int,
        val nextReviewAt: Long,
        val isCollocation: Boolean = false
    )

    @Serializable
    data class MistakeBackupDto(
        val original: String,
        val normalizedOriginal: String,
        val corrected: String,
        val explanation: String,
        val createdAt: Long,
        val reviewCount: Int,
        val nextReviewAt: Long,
        val timesMade: Int
    )

    @Serializable
    data class FactBackupDto(
        val fact: String,
        val normalizedFact: String,
        val createdAt: Long
    )

    @Serializable
    data class ScenarioBackupDto(
        val id: String,
        val titleVi: String,
        val titleEn: String,
        val aiRole: String,
        val learnerRole: String,
        val customContext: String,
        val missionObjective: String?,
        val createdAt: Long
    )

    suspend fun exportToJson(outputStream: OutputStream): Result<Unit> = runCatching {
        val sessions = sessionDao.getAllSessions().map {
            SessionBackupDto(
                id = it.id,
                topicId = it.topicId,
                scenarioId = it.scenarioId,
                level = it.level,
                mode = it.mode,
                startedAt = it.startedAt,
                endedAt = it.endedAt,
                activeDurationMs = it.activeDurationMs,
                fluencyScore = it.fluencyScore,
                grammarScore = it.grammarScore,
                vocabularyScore = it.vocabularyScore,
                encouragement = it.encouragement,
                nextSuggestion = it.nextSuggestion,
                isCompleted = it.isCompleted,
                pronunciationScore = it.pronunciationScore,
                wordsPerMinute = it.wordsPerMinute,
                fillerWordsCount = it.fillerWordsCount,
                fillerWordsRatio = it.fillerWordsRatio,
                meanLengthOfUtterance = it.meanLengthOfUtterance,
                vietnameseWordsRatio = it.vietnameseWordsRatio,
                comprehensionScore = it.comprehensionScore,
                ieltsBandScore = it.ieltsBandScore
            )
        }
        val words = wordDao.getAllWords().map {
            WordBackupDto(
                word = it.word,
                normalizedWord = it.normalizedWord,
                meaning = it.meaning,
                exampleSentence = it.exampleSentence,
                learnedAt = it.learnedAt,
                reviewCount = it.reviewCount,
                nextReviewAt = it.nextReviewAt,
                isCollocation = it.isCollocation
            )
        }
        val mistakes = memoryDao.getAllMistakes().map {
            MistakeBackupDto(
                original = it.original,
                normalizedOriginal = it.normalizedOriginal,
                corrected = it.corrected,
                explanation = it.explanation,
                createdAt = it.createdAt,
                reviewCount = it.reviewCount,
                nextReviewAt = it.nextReviewAt,
                timesMade = it.timesMade
            )
        }
        val facts = memoryDao.getAllFacts().map {
            FactBackupDto(fact = it.fact, normalizedFact = it.normalizedFact, createdAt = it.createdAt)
        }
        val scenarios = scenarioDao.getAll().map {
            ScenarioBackupDto(
                id = it.id,
                titleVi = it.titleVi,
                titleEn = it.titleEn,
                aiRole = it.aiRole,
                learnerRole = it.learnerRole,
                customContext = it.customContext,
                missionObjective = it.missionObjective,
                createdAt = it.createdAt
            )
        }

        val backupData = BackupData(
            sessions = sessions,
            words = words,
            mistakes = mistakes,
            facts = facts,
            scenarios = scenarios
        )

        val jsonString = json.encodeToString(backupData)
        outputStream.writer(Charsets.UTF_8).use { it.write(jsonString) }
    }

    suspend fun importFromJson(inputStream: InputStream): Result<Int> = runCatching {
        val jsonString = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val backupData = json.decodeFromString<BackupData>(jsonString)

        var restoredCount = 0

        if (backupData.sessions.isNotEmpty()) {
            for (s in backupData.sessions) {
                sessionDao.upsertSession(
                    SessionEntity(
                        id = s.id,
                        topicId = s.topicId,
                        scenarioId = s.scenarioId,
                        level = s.level,
                        mode = s.mode,
                        startedAt = s.startedAt,
                        endedAt = s.endedAt,
                        activeDurationMs = s.activeDurationMs,
                        fluencyScore = s.fluencyScore,
                        grammarScore = s.grammarScore,
                        vocabularyScore = s.vocabularyScore,
                        encouragement = s.encouragement,
                        nextSuggestion = s.nextSuggestion,
                        isCompleted = s.isCompleted,
                        pronunciationScore = s.pronunciationScore,
                        wordsPerMinute = s.wordsPerMinute,
                        fillerWordsCount = s.fillerWordsCount,
                        fillerWordsRatio = s.fillerWordsRatio,
                        meanLengthOfUtterance = s.meanLengthOfUtterance,
                        vietnameseWordsRatio = s.vietnameseWordsRatio,
                        comprehensionScore = s.comprehensionScore,
                        ieltsBandScore = s.ieltsBandScore
                    )
                )
                restoredCount++
            }
        }

        if (backupData.words.isNotEmpty()) {
            val entities = backupData.words.map {
                LearnedWordEntity(
                    word = it.word,
                    normalizedWord = it.normalizedWord,
                    meaning = it.meaning,
                    exampleSentence = it.exampleSentence,
                    sessionId = null,
                    learnedAt = it.learnedAt,
                    reviewCount = it.reviewCount,
                    nextReviewAt = it.nextReviewAt,
                    isCollocation = it.isCollocation
                )
            }
            wordDao.insertAll(entities)
            restoredCount += entities.size
        }

        if (backupData.mistakes.isNotEmpty()) {
            val entities = backupData.mistakes.map {
                MistakeEntity(
                    original = it.original,
                    normalizedOriginal = it.normalizedOriginal,
                    corrected = it.corrected,
                    explanation = it.explanation,
                    sessionId = null,
                    createdAt = it.createdAt,
                    reviewCount = it.reviewCount,
                    nextReviewAt = it.nextReviewAt,
                    timesMade = it.timesMade
                )
            }
            memoryDao.insertAllMistakes(entities)
            restoredCount += entities.size
        }

        if (backupData.facts.isNotEmpty()) {
            val entities = backupData.facts.map {
                LearnerFactEntity(
                    fact = it.fact,
                    normalizedFact = it.normalizedFact,
                    sessionId = null,
                    createdAt = it.createdAt
                )
            }
            memoryDao.insertFacts(entities)
            restoredCount += entities.size
        }

        if (backupData.scenarios.isNotEmpty()) {
            for (s in backupData.scenarios) {
                scenarioDao.insert(
                    CustomScenarioEntity(
                        id = s.id,
                        titleVi = s.titleVi,
                        titleEn = s.titleEn,
                        aiRole = s.aiRole,
                        learnerRole = s.learnerRole,
                        customContext = s.customContext,
                        missionObjective = s.missionObjective,
                        createdAt = s.createdAt
                    )
                )
                restoredCount++
            }
        }

        restoredCount
    }

    /**
     * Exports all learned words and collocations to Anki tab-delimited format (TSV).
     * Format: Front (Word/Collocation) \t Back (Meaning & Type) \t Example
     */
    suspend fun exportToAnki(outputStream: OutputStream): Int {
        val words = wordDao.getAllWords()
        outputStream.writer(Charsets.UTF_8).use { writer ->
            writer.write("#separator:tab\n#html:true\n#tags:SpeakDrive\n")
            for (w in words) {
                val tag = if (w.isCollocation) "[Collocation] " else ""
                val front = "$tag${w.word}".replace("\t", " ")
                val back = w.meaning.replace("\t", " ")
                val example = w.exampleSentence.replace("\t", " ")
                writer.write("$front\t$back\t$example\n")
            }
        }
        return words.size
    }
}
