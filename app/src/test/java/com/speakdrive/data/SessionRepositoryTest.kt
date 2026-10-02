package com.speakdrive.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.speakdrive.ai.model.CompletedSession
import com.speakdrive.ai.model.Correction
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.NewWord
import com.speakdrive.ai.model.SessionMode
import com.speakdrive.ai.model.SessionSummary
import com.speakdrive.ai.model.Speaker
import com.speakdrive.ai.model.TranscriptTurn
import com.speakdrive.ai.pronunciation.PronunciationGrader
import com.speakdrive.data.local.AppDatabase
import com.speakdrive.data.repository.ProgressRepository
import com.speakdrive.data.repository.SessionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SessionRepositoryTest {

    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private var now = System.currentTimeMillis()
    private val repository = SessionRepository(db.sessionDao(), db.wordDao()).also { it.clock = { now } }
    private val progress = ProgressRepository(db.sessionDao(), db.wordDao())

    @After
    fun close() = db.close()

    private fun session(
        id: String = "s1",
        topicId: String = "travel",
        completed: Boolean = true,
        words: List<String> = listOf("itinerary"),
        startedAt: Long = now,
        durationMs: Long = TimeUnit.MINUTES.toMillis(12)
    ) = CompletedSession(
        id = id,
        topicId = topicId,
        scenarioId = null,
        level = DifficultyLevel.BEGINNER,
        mode = SessionMode.FREE_TALK,
        startedAt = startedAt,
        endedAt = startedAt + durationMs,
        activeDurationMs = durationMs,
        transcript = listOf(
            TranscriptTurn(1, Speaker.AI, "Where did you go?", startedAt),
            TranscriptTurn(2, Speaker.USER, "I goed to Hue", startedAt + 1000)
        ),
        summary = if (completed) {
            SessionSummary(
                fluencyScore = 7,
                grammarScore = 5,
                vocabularyScore = 6,
                newWords = words.map { NewWord(it, "nghĩa", "Example with $it.") },
                corrections = listOf(Correction("I goed", "I went", "Quá khứ của go là went")),
                encouragement = "Giỏi lắm",
                nextSuggestion = "Ôn thì quá khứ"
            )
        } else {
            null
        },
        reviewedWords = emptyList(),
        isCompleted = completed
    )

    @Test
    fun `a completed save replaces the draft of the same lesson`() = runTest {
        repository.saveSession(session(completed = false))
        repository.saveSession(session(completed = true))

        val detail = repository.observeSessionDetail("s1").first()!!
        assertThat(detail.session.isCompleted).isTrue()
        assertThat(detail.session.fluencyScore).isEqualTo(7)
        assertThat(detail.messages.map { it.text }).containsExactly("Where did you go?", "I goed to Hue").inOrder()
        assertThat(detail.corrections.single().corrected).isEqualTo("I went")
        assertThat(detail.words.single().word).isEqualTo("itinerary")
    }

    @Test
    fun `a word learned twice is stored once`() = runTest {
        repository.saveSession(session(id = "a", words = listOf("Commute")))
        repository.saveSession(session(id = "b", words = listOf("commute", "traffic jam")))

        assertThat(repository.observeAllWords().first().map { it.word }).containsExactly("Commute", "traffic jam")
    }

    @Test
    fun `words come back for review and move to longer intervals`() = runTest {
        repository.saveSession(session(words = listOf("itinerary")))
        assertThat(repository.wordsDueForReview(10)).isEmpty()

        now += TimeUnit.DAYS.toMillis(1)
        assertThat(repository.wordsDueForReview(10).map { it.word }).containsExactly("itinerary")

        repository.markWordsReviewed(listOf("Itinerary"))
        assertThat(repository.wordsDueForReview(10)).isEmpty()
        val word = repository.observeAllWords().first().single()
        assertThat(word.reviewCount).isEqualTo(1)
        assertThat(word.nextReviewAt).isEqualTo(now + TimeUnit.DAYS.toMillis(3))
    }

    @Test
    fun `recent topics are newest first`() = runTest {
        repository.saveSession(session(id = "old", topicId = "food", startedAt = now - 10_000))
        repository.saveSession(session(id = "new", topicId = "work", startedAt = now))

        assertThat(repository.recentTopicIds(5)).containsExactly("work", "food").inOrder()
    }

    @Test
    fun `progress stats add up today's practice`() = runTest {
        repository.saveSession(session(id = "a", durationMs = TimeUnit.MINUTES.toMillis(10)))
        repository.saveSession(session(id = "b", topicId = "food", durationMs = TimeUnit.MINUTES.toMillis(5), words = listOf("menu")))
        repository.saveSession(session(id = "c", startedAt = now - TimeUnit.DAYS.toMillis(1)))

        val stats = progress.observeStats().first()
        assertThat(stats.minutesToday).isEqualTo(15)
        assertThat(stats.streakDays).isEqualTo(2)
        assertThat(stats.completedSessions).isEqualTo(3)
        assertThat(stats.topicCounts).containsExactly("travel", 2, "food", 1)
        assertThat(stats.lastSevenDays).hasSize(7)
        assertThat(stats.lastSevenDays.last().minutes).isEqualTo(15)
    }

    @Test
    fun `pronunciation attempts and score are stored with the lesson`() = runTest {
        val attempts = listOf(
            PronunciationGrader.grade("I need three tickets", "I need tree tickets", true, emptyList(), "th sound", 1, now),
            PronunciationGrader.grade("I need three tickets", "I need three tickets", true, emptyList(), "", 2, now + 1)
        )
        val drill = session(id = "d").copy(
            mode = SessionMode.REPEAT_AFTER_ME,
            pronunciationAttempts = attempts,
            summary = session().summary!!.copy(pronunciationScore = 100)
        )

        repository.saveSession(drill)

        val detail = repository.observeSessionDetail("d").first()!!
        assertThat(detail.session.pronunciationScore).isEqualTo(100)
        assertThat(detail.attempts.map { it.attemptNumber to it.passed }).containsExactly(1 to false, 2 to true).inOrder()
        assertThat(detail.attempts.first().problemWords).isEqualTo("three")
        assertThat(detail.attempts.first().notes).isEqualTo("th sound")
    }

    @Test
    fun `delete all wipes lessons and words`() = runTest {
        repository.saveSession(session())

        repository.deleteAllData()

        assertThat(repository.observeHistory().first()).isEmpty()
        assertThat(repository.observeAllWords().first()).isEmpty()
        assertThat(repository.observeSessionDetail("s1").first()).isNull()
    }
}
