package com.speakdrive.domain

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.speakdrive.data.local.AppDatabase
import com.speakdrive.data.local.entity.LearnedWordEntity
import com.speakdrive.data.local.entity.SessionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WeeklyDigestGeneratorTest {

    private lateinit var db: AppDatabase
    private lateinit var generator: WeeklyDigestGenerator
    private val now = 1_700_000_000_000L // arbitrary fixed timestamp

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        generator = WeeklyDigestGenerator(db.sessionDao(), db.wordDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `generateDigest with no sessions returns empty encouragement`() = runTest {
        val digest = generator.generateDigest(now)
        assertThat(digest.totalMinutes).isEqualTo(0)
        assertThat(digest.sessionCount).isEqualTo(0)
        assertThat(digest.averageWpm).isEqualTo(0)
        assertThat(digest.newWordsCount).isEqualTo(0)
        assertThat(digest.spokenSpokenSummaryVi).contains("Một tuần mới lại bắt đầu!")
        assertThat(digest.spokenSummaryEn).contains("A fresh week begins!")
    }

    @Test
    fun `generateDigest aggregates sessions and words within past 7 days`() = runTest {
        val threeDaysAgo = now - TimeUnit.DAYS.toMillis(3)
        val tenDaysAgo = now - TimeUnit.DAYS.toMillis(10)

        // Session 1: 3 days ago, 15 minutes, 110 WPM
        db.sessionDao().upsertSession(
            SessionEntity(
                id = "s1",
                topicId = "free_talk",
                scenarioId = null,
                level = "INTERMEDIATE",
                mode = "CONVERSATION",
                startedAt = threeDaysAgo,
                endedAt = threeDaysAgo + TimeUnit.MINUTES.toMillis(15),
                activeDurationMs = TimeUnit.MINUTES.toMillis(15),
                fluencyScore = 8,
                grammarScore = 8,
                vocabularyScore = 8,
                encouragement = "Good effort",
                nextSuggestion = "Keep going",
                isCompleted = true,
                pronunciationScore = 8,
                wordsPerMinute = 110,
                fillerWordsCount = 2,
                meanLengthOfUtterance = 8.5f,
                vietnameseWordsRatio = 0.05f
            )
        )

        // Session 2: 2 days ago, 25 minutes, 130 WPM
        val twoDaysAgo = now - TimeUnit.DAYS.toMillis(2)
        db.sessionDao().upsertSession(
            SessionEntity(
                id = "s2",
                topicId = "ielts",
                scenarioId = null,
                level = "ADVANCED",
                mode = "IELTS_SPEAKING",
                startedAt = twoDaysAgo,
                endedAt = twoDaysAgo + TimeUnit.MINUTES.toMillis(25),
                activeDurationMs = TimeUnit.MINUTES.toMillis(25),
                fluencyScore = 9,
                grammarScore = 9,
                vocabularyScore = 9,
                encouragement = "Excellent",
                nextSuggestion = "Focus on vocabulary",
                isCompleted = true,
                pronunciationScore = 9,
                wordsPerMinute = 130,
                fillerWordsCount = 1,
                meanLengthOfUtterance = 11.2f,
                vietnameseWordsRatio = 0.0f
            )
        )

        // Session 3: 10 days ago (older than 1 week) -> should be excluded
        db.sessionDao().upsertSession(
            SessionEntity(
                id = "s3",
                topicId = "old",
                scenarioId = null,
                level = "INTERMEDIATE",
                mode = "CONVERSATION",
                startedAt = tenDaysAgo,
                endedAt = tenDaysAgo + TimeUnit.MINUTES.toMillis(30),
                activeDurationMs = TimeUnit.MINUTES.toMillis(30),
                fluencyScore = 7,
                grammarScore = 7,
                vocabularyScore = 7,
                encouragement = "Good",
                nextSuggestion = "Work on grammar",
                isCompleted = true,
                pronunciationScore = 7,
                wordsPerMinute = 90
            )
        )

        // Words
        db.wordDao().upsertWord(
            LearnedWordEntity(
                word = "articulate",
                normalizedWord = "articulate",
                meaning = "nói rõ ràng",
                exampleSentence = "She is very articulate.",
                sessionId = null,
                learnedAt = threeDaysAgo,
                nextReviewAt = 0L
            )
        )
        db.wordDao().upsertWord(
            LearnedWordEntity(
                word = "come across",
                normalizedWord = "come across",
                meaning = "tình cờ gặp",
                exampleSentence = "I came across this book.",
                sessionId = null,
                learnedAt = twoDaysAgo,
                nextReviewAt = 0L,
                isCollocation = true
            )
        )
        db.wordDao().upsertWord(
            LearnedWordEntity(
                word = "ancient",
                normalizedWord = "ancient",
                meaning = "cổ xưa",
                exampleSentence = "Ancient Rome.",
                sessionId = null,
                learnedAt = tenDaysAgo,
                nextReviewAt = 0L
            )
        )

        val digest = generator.generateDigest(now)
        assertThat(digest.totalMinutes).isEqualTo(40) // 15 + 25
        assertThat(digest.sessionCount).isEqualTo(2)
        assertThat(digest.averageWpm).isEqualTo(120) // (110 + 130) / 2
        assertThat(digest.newWordsCount).isEqualTo(2) // 2 words within 7 days
        assertThat(digest.spokenSpokenSummaryVi).contains("40 phút")
        assertThat(digest.spokenSpokenSummaryVi).contains("120 từ trên phút")
        assertThat(digest.spokenSpokenSummaryVi).contains("2 từ vựng và cụm từ mới")
        assertThat(digest.spokenSummaryEn).contains("40 minutes")
        assertThat(digest.spokenSummaryEn).contains("120 words per minute")
        assertThat(digest.spokenSummaryEn).contains("2 new words and collocations")
    }
}
