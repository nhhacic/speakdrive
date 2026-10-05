package com.speakdrive.ai.pronunciation

import com.google.common.truth.Truth.assertThat
import com.speakdrive.ai.model.PronunciationStrictness
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AzurePronunciationTest {

    /** Shape of a real "detailed" response with Granularity=Phoneme (flat score layout). */
    private val flatResponse = """
        {"RecognitionStatus":"Success","Offset":700000,"Duration":8400000,"DisplayText":"I need tree tickets.",
         "NBest":[{"Confidence":0.97,"Lexical":"i need tree tickets","Display":"I need tree tickets.",
           "AccuracyScore":71.4,"FluencyScore":92.0,"CompletenessScore":100.0,"PronScore":79.6,
           "Words":[
             {"Word":"i","AccuracyScore":100.0,"ErrorType":"None","Phonemes":[{"Phoneme":"ay","AccuracyScore":100.0}]},
             {"Word":"need","AccuracyScore":96.0,"ErrorType":"None","Phonemes":[{"Phoneme":"n","AccuracyScore":98.0}]},
             {"Word":"three","AccuracyScore":35.0,"ErrorType":"Mispronunciation",
              "Phonemes":[{"Phoneme":"th","AccuracyScore":12.0},{"Phoneme":"r","AccuracyScore":55.0},{"Phoneme":"iy","AccuracyScore":90.0}]},
             {"Word":"tickets","AccuracyScore":82.0,"ErrorType":"None","Phonemes":[{"Phoneme":"s","AccuracyScore":70.0}]}
           ]}]}
    """.trimIndent()

    /** Older/SDK layout with scores nested in "PronunciationAssessment". */
    private val nestedResponse = """
        {"RecognitionStatus":"Success","NBest":[{"Display":"Good morning.",
          "PronunciationAssessment":{"AccuracyScore":100,"FluencyScore":100,"CompletenessScore":100,"PronScore":100},
          "Words":[{"Word":"good","PronunciationAssessment":{"AccuracyScore":100,"ErrorType":"None"}},
                   {"Word":"morning","PronunciationAssessment":{"AccuracyScore":98,"ErrorType":"None"}}]}]}
    """.trimIndent()

    @Test
    fun `parses scores, words and weakest sounds`() {
        val result = AzureResponseParser.parse(flatResponse)

        assertThat(result.pronunciationScore).isEqualTo(80)
        assertThat(result.accuracyScore).isEqualTo(71)
        assertThat(result.recognizedText).isEqualTo("I need tree tickets.")
        assertThat(result.problemWords.map { it.word }).containsExactly("three")
        assertThat(result.describeProblems(PronunciationStrictness.ADVANCED)).containsExactly("three (th 12, r 55)")
        // Overall 80 but a mispronounced word: strict grading still fails it.
        assertThat(result.passed).isFalse()
    }

    @Test
    fun `parses the nested layout`() {
        val result = AzureResponseParser.parse(nestedResponse)

        assertThat(result.pronunciationScore).isEqualTo(100)
        assertThat(result.words.map { it.word to it.accuracy }).containsExactly("good" to 100, "morning" to 98).inOrder()
        assertThat(result.passed).isTrue()
    }

    @Test
    fun `silence is a zero score`() {
        val result = AzureResponseParser.parse("""{"RecognitionStatus":"InitialSilenceTimeout"}""")
        assertThat(result.pronunciationScore).isEqualTo(0)
        assertThat(result.passed).isFalse()
    }

    @Test
    fun `omitted words fail even with a high overall score`() {
        val result = AzureAssessment(
            pronunciationScore = 90, accuracyScore = 95, fluencyScore = 95, completenessScore = 90, recognizedText = "",
            words = listOf(AzureWord("please", 0, AzureWord.ERROR_OMISSION, emptyList()))
        )
        assertThat(result.passed).isFalse()
        assertThat(result.describeProblems()).containsExactly("please (missing)")
    }

    @Test
    fun `wav header describes 16 kHz mono PCM16`() {
        val pcm = ByteArray(3200)
        val wav = Wav.pcm16Mono(pcm, 16_000)
        val header = ByteBuffer.wrap(wav, 0, 44).order(ByteOrder.LITTLE_ENDIAN)

        assertThat(String(wav, 0, 4)).isEqualTo("RIFF")
        assertThat(header.getInt(4)).isEqualTo(36 + pcm.size)
        assertThat(String(wav, 8, 4)).isEqualTo("WAVE")
        assertThat(header.getShort(22).toInt()).isEqualTo(1)
        assertThat(header.getInt(24)).isEqualTo(16_000)
        assertThat(header.getShort(34).toInt()).isEqualTo(16)
        assertThat(header.getInt(40)).isEqualTo(pcm.size)
        assertThat(wav.size).isEqualTo(44 + pcm.size)
    }

    @Test
    fun `azure is a third judge in the grader`() {
        val azure = AzureResponseParser.parse(flatResponse)

        // Transcript and AI both happy, Azure hears a bad "th": not passed, and the word is flagged.
        val attempt = PronunciationGrader.grade("I need three tickets", "I need three tickets", true, emptyList(), "", 1, 0, azure, strictness = PronunciationStrictness.ADVANCED)
        assertThat(attempt.passed).isFalse()
        assertThat(attempt.words.first { it.word == "three" }.azureScore).isEqualTo(35)
        assertThat(attempt.words.first { it.word == "three" }.isProblem).isTrue()
        assertThat(attempt.problemWords).contains("three")

        val response = PronunciationDrill.toolResponse(attempt)
        assertThat(response["final_verdict"]).isEqualTo("needs_work")
        assertThat(response["azure_weak_sounds"] as List<*>).containsExactly("three (th 12, r 55)")
        assertThat((response["azure_scores"] as Map<*, *>)["pronunciation"]).isEqualTo(80)
    }

    @Test
    fun `an azure pass still needs the other judges`() {
        val azure = AzureResponseParser.parse(nestedResponse)
        val attempt = PronunciationGrader.grade("Good morning", "Good morning", false, listOf("morning"), "", 1, 0, azure)
        assertThat(attempt.passed).isFalse()
    }

    @Test
    fun `strictness thresholds change pass requirements`() {
        val word = AzureWord("hello", 55, "None", listOf(AzurePhoneme("hh", 48), AzurePhoneme("l", 55)))
        val assessment = AzureAssessment(
            pronunciationScore = 75,
            accuracyScore = 75,
            fluencyScore = 80,
            completenessScore = 100,
            recognizedText = "hello",
            words = listOf(word)
        )

        // Strict / Advanced requires pronScore >= 80, word >= 60, phoneme >= 60 -> fails
        assertThat(assessment.isPassed(PronunciationStrictness.STRICT)).isFalse()
        assertThat(assessment.isPassed(PronunciationStrictness.ADVANCED)).isFalse()

        // Standard / Intermediate requires pronScore >= 70, word >= 50, phoneme >= 45 -> passes
        assertThat(assessment.isPassed(PronunciationStrictness.STANDARD)).isTrue()
        assertThat(assessment.isPassed(PronunciationStrictness.INTERMEDIATE)).isTrue()

        // Relaxed / Elementary requires pronScore >= 60, word >= 40, phoneme ignored -> passes
        assertThat(assessment.isPassed(PronunciationStrictness.RELAXED)).isTrue()
        assertThat(assessment.isPassed(PronunciationStrictness.ELEMENTARY)).isTrue()

        // Beginner requires pronScore >= 50, word >= 35, phoneme ignored -> passes
        assertThat(assessment.isPassed(PronunciationStrictness.BEGINNER)).isTrue()
    }
}
