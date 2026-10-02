package com.speakdrive.data

import android.app.Application
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.speakdrive.data.local.AppDatabase
import com.speakdrive.data.local.MIGRATION_2_3
import com.speakdrive.data.local.MIGRATION_3_4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Upgrades keep the learner's history (schemas come from app/schemas). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun `2 to 3 keeps sessions and adds the pronunciation tables`() {
        helper.createDatabase(dbPath, 2).apply {
            execSQL(
                "INSERT INTO sessions (id, topicId, scenarioId, level, mode, startedAt, endedAt, activeDurationMs, " +
                    "fluencyScore, grammarScore, vocabularyScore, encouragement, nextSuggestion, isCompleted) " +
                    "VALUES ('s1', 'travel', NULL, 'BEGINNER', 'FREE_TALK', 1, 2, 60000, 7, 6, 5, 'ok', 'next', 1)"
            )
            execSQL(
                "INSERT INTO messages (sessionId, speaker, text, timestamp, position) VALUES ('s1', 'USER', 'Hello', 1, 0)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbPath, 3, true, MIGRATION_2_3)

        db.query("SELECT topicId, fluencyScore, pronunciationScore FROM sessions WHERE id = 's1'").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("travel")
            assertThat(cursor.getInt(1)).isEqualTo(7)
            assertThat(cursor.isNull(2)).isTrue()
        }
        db.query("SELECT COUNT(*) FROM messages").use { cursor ->
            cursor.moveToFirst()
            assertThat(cursor.getInt(0)).isEqualTo(1)
        }
        db.execSQL(
            "INSERT INTO pronunciation_attempts (sessionId, target, heard, accuracyPercent, passed, attemptNumber, " +
                "modelSaidCorrect, problemWords, notes, timestamp) VALUES ('s1', 'Hi there', 'hi there', 100, 1, 1, 1, '', '', 3)"
        )
    }

    @Test
    fun `3 to 4 adds azure scores to existing attempts`() {
        helper.createDatabase(dbPath, 3).apply {
            execSQL(
                "INSERT INTO sessions (id, topicId, scenarioId, level, mode, startedAt, endedAt, activeDurationMs, " +
                    "fluencyScore, grammarScore, vocabularyScore, encouragement, nextSuggestion, isCompleted, pronunciationScore) " +
                    "VALUES ('s1', 'travel', NULL, 'BEGINNER', 'REPEAT_AFTER_ME', 1, 2, 60000, NULL, NULL, NULL, NULL, NULL, 1, 50)"
            )
            execSQL(
                "INSERT INTO pronunciation_attempts (sessionId, target, heard, accuracyPercent, passed, attemptNumber, " +
                    "modelSaidCorrect, problemWords, notes, timestamp) VALUES ('s1', 'Hi there', 'hi there', 100, 1, 1, 1, '', '', 3)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbPath, 4, true, MIGRATION_3_4)

        db.query("SELECT target, azurePronScore, azureWeakSounds FROM pronunciation_attempts").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("Hi there")
            assertThat(cursor.isNull(1)).isTrue()
            assertThat(cursor.isNull(2)).isTrue()
        }
    }

    /** Room's driver compares full paths, which differ from the bare name under Robolectric. */
    private val dbPath: String = InstrumentationRegistry.getInstrumentation().targetContext
        .getDatabasePath("migration-test").absolutePath
}
