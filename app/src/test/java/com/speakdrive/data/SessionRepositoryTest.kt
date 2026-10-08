package com.speakdrive.data

import com.speakdrive.ai.model.MistakeReviewResult
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
import com.speakdrive.data.repository.UserPreferencesRepository
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
    private val repository = SessionRepository(db.sessionDao(), db.wordDao(), db.memoryDao()).also { it.clock = { now } }
    private val progress = ProgressRepository(
        db.sessionDao(),
        db.wordDao(),
        UserPreferencesRepository(ApplicationProvider.getApplicationContext()),
        db.memoryDao()
    )

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

    private fun withMistakes(
        id: String,
        corrections: List<Correction>,
        facts: List<String> = emptyList(),
        mode: SessionMode = SessionMode.FREE_TALK
    ) = session(id = id).let { s ->
        s.copy(mode = mode, summary = s.summary!!.copy(corrections = corrections, learnerFacts = facts))
    }

    @Test
    fun `mistakes from a lesson come back for review the next day`() = runTest {
        repository.saveSession(withMistakes("a", listOf(Correction("I goed to Hue.", "I went to Hue.", "Quá khứ của go là went"))))
        assertThat(repository.mistakesDueForReview(10)).isEmpty()

        now += TimeUnit.DAYS.toMillis(1)
        val due = repository.mistakesDueForReview(10).single()
        assertThat(due.original).isEqualTo("I goed to Hue.")
        assertThat(due.corrected).isEqualTo("I went to Hue.")
        assertThat(repository.observeDueMistakeCount().first()).isEqualTo(1)
    }

    @Test
    fun `the same mistake made again counts twice and starts its schedule over`() = runTest {
        repository.saveSession(withMistakes("a", listOf(Correction("I goed to Hue", "I went to Hue", ""))))
        now += TimeUnit.DAYS.toMillis(1)
        val id = repository.mistakesDueForReview(10).single().id
        repository.recordMistakeReviews(listOf(MistakeReviewResult(id, fixed = true)))
        assertThat(repository.mistakesDueForReview(10)).isEmpty()

        // Same sentence, different case and punctuation, in a later lesson.
        repository.saveSession(withMistakes("b", listOf(Correction("i goed to hue!", "I went to Hue", "Dùng went"))))
        // Saving the same lesson again must not count it a third time.
        repository.saveSession(withMistakes("b", listOf(Correction("i goed to hue!", "I went to Hue", "Dùng went"))))

        val mistake = repository.observeMistakes().first().single()
        assertThat(mistake.timesMade).isEqualTo(2)
        assertThat(mistake.reviewCount).isEqualTo(0)
        assertThat(mistake.explanation).isEqualTo("Dùng went")
        assertThat(mistake.nextReviewAt).isEqualTo(now + TimeUnit.DAYS.toMillis(1))
    }

    @Test
    fun `fixed mistakes move to longer intervals and missed ones come back tomorrow`() = runTest {
        repository.saveSession(
            withMistakes("a", listOf(Correction("She don't like it", "She doesn't like it", ""), Correction("I goed", "I went", "")))
        )
        now += TimeUnit.DAYS.toMillis(1)
        val (first, second) = repository.mistakesDueForReview(10)

        repository.recordMistakeReviews(listOf(MistakeReviewResult(first.id, true), MistakeReviewResult(second.id, false)))

        val byId = repository.observeMistakes().first().associateBy { it.id }
        assertThat(byId.getValue(first.id).reviewCount).isEqualTo(1)
        assertThat(byId.getValue(first.id).nextReviewAt).isEqualTo(now + TimeUnit.DAYS.toMillis(3))
        assertThat(byId.getValue(second.id).reviewCount).isEqualTo(0)
        assertThat(byId.getValue(second.id).nextReviewAt).isEqualTo(now + TimeUnit.DAYS.toMillis(1))
        assertThat(byId.getValue(second.id).lastReviewedAt).isEqualTo(now)
    }

    @Test
    fun `pronunciation drill corrections are not kept as mistakes`() = runTest {
        repository.saveSession(withMistakes("d", listOf(Correction("I need tree tickets", "I need three tickets", "")), mode = SessionMode.REPEAT_AFTER_ME))

        assertThat(repository.observeMistakes().first()).isEmpty()
    }

    @Test
    fun `learner memory combines repeated mistakes, weak drill words and recent facts`() = runTest {
        repository.saveSession(withMistakes("a", listOf(Correction("I goed", "I went", "")), facts = listOf("Works as a nurse")))
        now += 1000
        repository.saveSession(withMistakes("b", listOf(Correction("I goed", "I went", ""), Correction("He go", "He goes", "")), facts = listOf("works as a nurse", "Has two kids")))
        repository.saveSession(
            session(id = "drill").copy(
                mode = SessionMode.REPEAT_AFTER_ME,
                pronunciationAttempts = listOf(
                    PronunciationGrader.grade("I need three tickets", "I need tree tickets", false, emptyList(), "", attemptNumber = 1, timestamp = now),
                    PronunciationGrader.grade("Three times", "tree times", false, emptyList(), "", attemptNumber = 1, timestamp = now + 1),
                    PronunciationGrader.grade("Where is it", "where is", false, emptyList(), "", attemptNumber = 1, timestamp = now + 2)
                )
            )
        )

        val memory = repository.learnerMemory()

        assertThat(memory.recurringMistakes.first().original).isEqualTo("I goed")
        assertThat(memory.recurringMistakes.map { it.original }).containsExactly("I goed", "He go")
        assertThat(memory.facts).containsExactly("Has two kids", "Works as a nurse")
        assertThat(memory.weakWords).contains("three")
    }

    @Test
    fun `deleting all data also forgets mistakes and facts`() = runTest {
        repository.saveSession(withMistakes("a", listOf(Correction("I goed", "I went", "")), facts = listOf("Works as a nurse")))

        repository.deleteAllData()

        assertThat(repository.observeMistakes().first()).isEmpty()
        assertThat(repository.observeLearnerFacts().first()).isEmpty()
        assertThat(repository.learnerMemory().isEmpty).isTrue()
    }

    @Test
    fun `forgetting facts keeps the mistakes to review`() = runTest {
        repository.saveSession(withMistakes("a", listOf(Correction("I goed", "I went", "")), facts = listOf("Works as a nurse")))

        repository.forgetLearnerFacts()

        assertThat(repository.observeLearnerFacts().first()).isEmpty()
        assertThat(repository.observeMistakes().first()).hasSize(1)
    }

    @Test
    fun `streak freezes and due mistakes show in the progress stats`() = runTest {
        // Saved two days ago, so the mistake is due now.
        now -= TimeUnit.DAYS.toMillis(2)
        repository.saveSession(withMistakes("a", listOf(Correction("I goed", "I went", ""))))

        val stats = progress.observeStats().first()

        assertThat(stats.dueMistakeCount).isEqualTo(1)
        assertThat(stats.streakFreezes).isEqualTo(0)
    }
}
