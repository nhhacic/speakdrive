package com.speakdrive.ai.translation

import com.google.common.truth.Truth.assertThat
import com.speakdrive.ai.drill.DrillSentenceManager
import com.speakdrive.ai.model.AppLanguage
import org.junit.Before
import org.junit.Test

class SentenceTranslatorTest {

    private lateinit var drillSentenceManager: DrillSentenceManager
    private lateinit var sentenceTranslator: SentenceTranslator

    @Before
    fun setup() {
        drillSentenceManager = DrillSentenceManager()
        sentenceTranslator = SentenceTranslator(drillSentenceManager)
    }

    @Test
    fun `getInstantTranslation returns curated offline translation with zero latency`() {
        // Curated sentence in DrillSentenceManager
        val vi = sentenceTranslator.getInstantTranslation("The climber survived against all odds.", AppLanguage.VIETNAMESE)
        assertThat(vi).isEqualTo("Người leo núi đã sống sót bất chấp mọi khó khăn.")

        val carVi = sentenceTranslator.getInstantTranslation("Stop the car here.", AppLanguage.VIETNAMESE)
        assertThat(carVi).isEqualTo("Dừng xe ở đây.")
    }

    @Test
    fun `getInstantTranslation returns null when English target language is selected`() {
        val en = sentenceTranslator.getInstantTranslation("Stop the car here.", AppLanguage.ENGLISH)
        assertThat(en).isNull()
    }

    @Test
    fun `generateInstantFallbackTranslation resolves survival and compound phrases offline`() {
        // Compound patterns
        val climber = sentenceTranslator.generateInstantFallbackTranslation(
            "Repeat after me: The climber survived against all odds.",
            AppLanguage.VIETNAMESE
        )
        assertThat(climber).isNotEmpty()
        assertThat(climber?.lowercase()).contains("người leo núi")

        val blindSpot = sentenceTranslator.generateInstantFallbackTranslation(
            "Check your blind spot carefully",
            AppLanguage.VIETNAMESE
        )
        assertThat(blindSpot).isNotEmpty()
        assertThat(blindSpot?.lowercase()).contains("điểm mù")

        val traffic = sentenceTranslator.generateInstantFallbackTranslation(
            "There is heavy traffic ahead",
            AppLanguage.VIETNAMESE
        )
        assertThat(traffic).isNotEmpty()
        assertThat(traffic?.lowercase()).contains("giao thông")
    }

    @Test
    fun `generateInstantFallbackTranslation resolves formulaic sentence starters`() {
        val result = sentenceTranslator.generateInstantFallbackTranslation(
            "Make sure to stop the car",
            AppLanguage.VIETNAMESE
        )
        assertThat(result).isNotEmpty()
        assertThat(result).contains("dừng xe lại")
    }
}
