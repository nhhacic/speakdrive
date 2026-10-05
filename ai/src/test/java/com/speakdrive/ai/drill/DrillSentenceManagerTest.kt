package com.speakdrive.ai.drill

import com.google.common.truth.Truth.assertThat
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.DrillCategory
import org.junit.Test

class DrillSentenceManagerTest {

    private val manager = DrillSentenceManager()

    @Test
    fun `manager has rich collection of curated sentences across all categories`() {
        val allSentences = manager.getSentences(DrillCategory.ALL)
        // Verify bank has over 100 curated sentences
        assertThat(allSentences.size).isAtLeast(100)

        // Verify each specific category has substantial sentence coverage
        DrillCategory.entries.filterNot { it == DrillCategory.ALL }.forEach { category ->
            val catSentences = manager.getSentences(category)
            assertThat(catSentences.size).isAtLeast(15)
            catSentences.forEach { sentence ->
                assertThat(sentence.category).isEqualTo(category)
                assertThat(sentence.text).isNotEmpty()
                assertThat(sentence.translationVi).isNotEmpty()
            }
        }
    }

    @Test
    fun `getSampleSentencesForPrompt returns balanced sentences for level and category`() {
        val beginnerPitfalls = manager.getSampleSentencesForPrompt(
            category = DrillCategory.VIETNAMESE_PITFALLS,
            level = DifficultyLevel.BEGINNER,
            limit = 6
        )
        assertThat(beginnerPitfalls).isNotEmpty()
        assertThat(beginnerPitfalls.size).isAtMost(6)
        beginnerPitfalls.forEach {
            assertThat(it.category).isEqualTo(DrillCategory.VIETNAMESE_PITFALLS)
        }

        val drivingPhrases = manager.getSampleSentencesForPrompt(
            category = DrillCategory.DRIVING_PHRASES,
            level = DifficultyLevel.INTERMEDIATE,
            limit = 6
        )
        assertThat(drivingPhrases).isNotEmpty()
        drivingPhrases.forEach {
            assertThat(it.category).isEqualTo(DrillCategory.DRIVING_PHRASES)
            // Driving phrases should be concise (mostly <= 10 words)
            assertThat(it.text.split(" ").size).isAtMost(10)
        }

        val allCategorySamples = manager.getSampleSentencesForPrompt(
            category = DrillCategory.ALL,
            level = DifficultyLevel.ADVANCED,
            limit = 8
        )
        assertThat(allCategorySamples.size).isAtLeast(4)
    }

    @Test
    fun `getCategoryGuidance provides actionable phonetic guidance for all categories`() {
        DrillCategory.entries.forEach { category ->
            val guidance = manager.getCategoryGuidance(category)
            assertThat(guidance).isNotEmpty()
            when (category) {
                DrillCategory.VIETNAMESE_PITFALLS -> assertThat(guidance.lowercase()).contains("consonant")
                DrillCategory.DRIVING_PHRASES -> assertThat(guidance.lowercase()).contains("driving")
                DrillCategory.CONVERSATIONAL_REFLEX -> assertThat(guidance.lowercase()).contains("spoken")
                DrillCategory.BUSINESS_WORK -> assertThat(guidance.lowercase()).contains("workplace")
                DrillCategory.TRAVEL_DAILY -> assertThat(guidance.lowercase()).contains("travel")
                DrillCategory.ALL -> assertThat(guidance.lowercase()).contains("balanced")
            }
        }
    }
}
