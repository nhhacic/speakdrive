package com.speakdrive.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.speakdrive.data.backup.DataBackupManager
import com.speakdrive.data.local.AppDatabase
import com.speakdrive.data.local.entity.CustomScenarioEntity
import com.speakdrive.data.local.entity.LearnedWordEntity
import com.speakdrive.data.local.entity.LearnerFactEntity
import com.speakdrive.data.local.entity.MistakeEntity
import com.speakdrive.data.local.entity.SessionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DataBackupManagerTest {

    private lateinit var db: AppDatabase
    private lateinit var backupManager: DataBackupManager

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        backupManager = DataBackupManager(
            sessionDao = db.sessionDao(),
            wordDao = db.wordDao(),
            memoryDao = db.memoryDao(),
            scenarioDao = db.scenarioDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `exportToJson and importFromJson round trip works properly`() = runTest {
        // Prepare database with sample data
        db.sessionDao().upsertSession(
            SessionEntity(
                id = "sess_101",
                topicId = "daily",
                scenarioId = null,
                level = "INTERMEDIATE",
                mode = "CONVERSATION",
                startedAt = 1000L,
                endedAt = 2000L,
                activeDurationMs = 1000L,
                fluencyScore = 8,
                grammarScore = 8,
                vocabularyScore = 8,
                encouragement = "Keep going",
                nextSuggestion = "Try more complex phrases",
                isCompleted = true,
                pronunciationScore = 8,
                wordsPerMinute = 125,
                fillerWordsCount = 2,
                meanLengthOfUtterance = 9.4f,
                vietnameseWordsRatio = 0.02f
            )
        )

        db.wordDao().upsertWord(
            LearnedWordEntity(
                word = "fast track",
                normalizedWord = "fast track",
                meaning = "đẩy nhanh tiến độ",
                exampleSentence = "We need to fast track this project.",
                sessionId = null,
                learnedAt = 1500L,
                nextReviewAt = 0L,
                isCollocation = true
            )
        )

        db.memoryDao().insertFacts(
            listOf(
                LearnerFactEntity(
                    fact = "Learner is a software engineer.",
                    normalizedFact = "learner is a software engineer",
                    sessionId = null,
                    createdAt = 1200L
                )
            )
        )

        db.memoryDao().insertAllMistakes(
            listOf(
                MistakeEntity(
                    original = "I go yesterday",
                    normalizedOriginal = "i go yesterday",
                    corrected = "I went yesterday",
                    explanation = "Past simple tense required",
                    sessionId = null,
                    createdAt = 1300L,
                    nextReviewAt = 0L,
                    timesMade = 2
                )
            )
        )

        db.scenarioDao().insert(
            CustomScenarioEntity(
                id = "sc_01",
                titleVi = "Phỏng vấn Logistics",
                titleEn = "Logistics Interview",
                aiRole = "Interviewer",
                learnerRole = "Candidate",
                customContext = "Interview for a logistics supervisor role.",
                missionObjective = "Explain supply chain experience.",
                createdAt = 1000L
            )
        )

        // Export to JSON
        val outputStream = ByteArrayOutputStream()
        backupManager.exportToJson(outputStream)

        val jsonString = outputStream.toString(Charsets.UTF_8.name())
        assertThat(jsonString).contains("sess_101")
        assertThat(jsonString).contains("fast track")
        assertThat(jsonString).contains("Logistics Interview")
        assertThat(jsonString).contains("I went yesterday")

        // Clear database
        db.clearAllTables()
        assertThat(db.sessionDao().getAllSessions()).isEmpty()
        assertThat(db.wordDao().getAllWords()).isEmpty()
        assertThat(db.scenarioDao().getAll()).isEmpty()

        // Import back from JSON
        val inputStream = ByteArrayInputStream(jsonString.toByteArray(Charsets.UTF_8))
        val importResult = backupManager.importFromJson(inputStream)
        val importOk = importResult.isSuccess
        assertThat(importOk).isTrue()
        assertThat(importResult.getOrNull()).isAtLeast(4)

        // Verify restoration
        val restoredSessions = db.sessionDao().getAllSessions()
        assertThat(restoredSessions).hasSize(1)
        assertThat(restoredSessions[0].id).isEqualTo("sess_101")
        assertThat(restoredSessions[0].wordsPerMinute).isEqualTo(125)

        val restoredWords = db.wordDao().getAllWords()
        assertThat(restoredWords).hasSize(1)
        assertThat(restoredWords[0].word).isEqualTo("fast track")
        assertThat(restoredWords[0].isCollocation).isTrue()

        val restoredMistakes = db.memoryDao().getAllMistakes()
        assertThat(restoredMistakes).hasSize(1)
        assertThat(restoredMistakes[0].corrected).isEqualTo("I went yesterday")

        val restoredScenarios = db.scenarioDao().getAll()
        assertThat(restoredScenarios).hasSize(1)
        assertThat(restoredScenarios[0].titleVi).isEqualTo("Phỏng vấn Logistics")
    }

    @Test
    fun `exportToAnki generates valid TSV cards`() = runTest {
        db.wordDao().upsertWord(
            LearnedWordEntity(
                word = "serendipity",
                normalizedWord = "serendipity",
                meaning = "sự may mắn bất ngờ",
                exampleSentence = "Finding this place was pure serendipity.",
                sessionId = null,
                learnedAt = 2000L,
                nextReviewAt = 0L,
                isCollocation = false
            )
        )
        db.wordDao().upsertWord(
            LearnedWordEntity(
                word = "bear in mind",
                normalizedWord = "bear in mind",
                meaning = "ghi nhớ điều gì",
                exampleSentence = "Bear in mind that roads are slippery.",
                sessionId = null,
                learnedAt = 2100L,
                nextReviewAt = 0L,
                isCollocation = true
            )
        )

        val outputStream = ByteArrayOutputStream()
        val count = backupManager.exportToAnki(outputStream)
        assertThat(count).isEqualTo(2)

        val content = outputStream.toString(Charsets.UTF_8.name())
        assertThat(content).startsWith("#separator:tab\n#html:true\n#tags:SpeakDrive\n")
        assertThat(content).contains("serendipity\tsự may mắn bất ngờ\tFinding this place was pure serendipity.")
        assertThat(content).contains("[Collocation] bear in mind\tghi nhớ điều gì\tBear in mind that roads are slippery.")
    }
}
