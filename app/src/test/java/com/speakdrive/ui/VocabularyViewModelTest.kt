package com.speakdrive.ui

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.speakdrive.ai.evaluation.SentenceEvaluator
import com.speakdrive.ai.evaluation.SentenceStatus
import com.speakdrive.audio.VoiceAnnouncer
import com.speakdrive.data.local.AppDatabase
import com.speakdrive.data.repository.SessionRepository
import com.speakdrive.ui.screens.VocabFilter
import com.speakdrive.ui.screens.VocabViewMode
import com.speakdrive.ui.screens.VocabularyViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class FakeAnnouncer : VoiceAnnouncer {
    val announcements = mutableListOf<String>()
    override fun announce(text: String) {
        announcements.add(text)
    }
    override fun shutdown() {}
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class VocabularyViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var repository: SessionRepository
    private val announcer = FakeAnnouncer()
    private val evaluator = SentenceEvaluator()
    private lateinit var viewModel: VocabularyViewModel
    private var now = 100_000_000L

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = SessionRepository(db.sessionDao(), db.wordDao(), db.memoryDao()).also { it.clock = { now } }
        viewModel = VocabularyViewModel(repository, announcer, evaluator).also { it.clock = { now } }
    }

    @After
    fun teardown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `addCustomWord inserts word and appears in words list`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.words.collect()
        }

        viewModel.addCustomWord("destination", "điểm đến", "Our destination is Da Nang.")
        val words = repository.observeAllWords().first { it.isNotEmpty() }
        advanceUntilIdle()

        assertThat(words).hasSize(1)
        assertThat(words[0].word).isEqualTo("destination")
        assertThat(words[0].meaning).isEqualTo("điểm đến")
    }

    @Test
    fun `searchQuery filters words by word or meaning`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.words.collect()
        }

        repository.addCustomWord("itinerary", "lịch trình", "Check the itinerary.")
        repository.addCustomWord("accommodation", "chỗ ở", "We need accommodation.")
        repository.observeAllWords().first { it.size == 2 }
        advanceUntilIdle()

        viewModel.setSearchQuery("lịch trình")
        advanceUntilIdle()

        val filtered = viewModel.words.value
        assertThat(filtered).isNotNull()
        assertThat(filtered).hasSize(1)
        assertThat(filtered!![0].word.word).isEqualTo("itinerary")

        viewModel.setSearchQuery("accom")
        advanceUntilIdle()
        val filtered2 = viewModel.words.value
        assertThat(filtered2).hasSize(1)
        assertThat(filtered2!![0].word.word).isEqualTo("accommodation")
    }

    @Test
    fun `selectedFilter filters by due status`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.words.collect()
        }

        val wordId = repository.addCustomWord("word1", "nghĩa 1", "")
        repository.observeAllWords().first { it.isNotEmpty() }
        advanceUntilIdle()

        // Room delivers table changes on its own executor, so the view model may see a write a
        // moment after the repository does: wait for the filtered list instead of reading it once.
        // Brand new word is in learning stage
        viewModel.setFilter(VocabFilter.LEARNING)
        assertThat(viewModel.words.first { it?.size == 1 }).hasSize(1)

        // Reset review schedule to make it due
        viewModel.resetReview(wordId)
        repository.observeAllWords().first { it.first().nextReviewAt <= now }
        advanceUntilIdle()

        viewModel.setFilter(VocabFilter.DUE)
        assertThat(viewModel.words.first { it?.size == 1 }).hasSize(1)

        // Mark it mastered (reviewCount = 5, scheduled far future)
        viewModel.markMastered(wordId)
        repository.observeAllWords().first { it.first().reviewCount >= 5 }
        advanceUntilIdle()

        viewModel.setFilter(VocabFilter.MASTERED)
        assertThat(viewModel.words.first { it?.size == 1 }).hasSize(1)

        viewModel.setFilter(VocabFilter.DUE)
        assertThat(viewModel.words.first { it != null && it.isEmpty() }).isEmpty()
    }

    @Test
    fun `playAudio invokes announcer`() {
        viewModel.playAudio("appreciate")
        assertThat(announcer.announcements).contains("appreciate")
    }

    @Test
    fun `sentence evaluation and save as example`() = runTest(testDispatcher) {
        val wordId = repository.addCustomWord("appreciate", "trân trọng", "")
        repository.observeAllWords().first { it.isNotEmpty() }
        advanceUntilIdle()

        viewModel.toggleSentencePractice(wordId)
        viewModel.updateSentenceInput("I really appreciate your kind help today.")
        viewModel.evaluateSentence("appreciate")

        val eval = viewModel.sentenceEvaluation.value
        assertThat(eval).isNotNull()
        assertThat(eval!!.status).isEqualTo(SentenceStatus.EXCELLENT)
        assertThat(eval.containsTargetWord).isTrue()

        viewModel.saveSentenceAsExample(wordId)
        val updated = repository.observeAllWords().first { it.first().exampleSentence.isNotBlank() }.first()

        assertThat(updated.exampleSentence).isEqualTo("I really appreciate your kind help today.")
    }

    @Test
    fun `flashcard navigation works properly`() {
        viewModel.setViewMode(VocabViewMode.FLASHCARD)
        assertThat(viewModel.viewMode.value).isEqualTo(VocabViewMode.FLASHCARD)
        assertThat(viewModel.isFlashcardFlipped.value).isFalse()

        viewModel.flipFlashcard()
        assertThat(viewModel.isFlashcardFlipped.value).isTrue()

        viewModel.nextFlashcard(size = 3)
        assertThat(viewModel.flashcardIndex.value).isEqualTo(1)
        assertThat(viewModel.isFlashcardFlipped.value).isFalse()

        viewModel.prevFlashcard(size = 3)
        assertThat(viewModel.flashcardIndex.value).isEqualTo(0)
    }

    @Test
    fun `deleteWord removes word from database`() = runTest(testDispatcher) {
        val wordId = repository.addCustomWord("temporary", "tạm thời", "")
        repository.observeAllWords().first { it.isNotEmpty() }
        advanceUntilIdle()

        viewModel.deleteWord(wordId)
        val remaining = repository.observeAllWords().first { it.isEmpty() }
        assertThat(remaining).isEmpty()
    }
}
