package com.speakdrive.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.speakdrive.domain.MemoryText

/** v3: pronunciation drill results ("repeat after me"). Schemas are in app/schemas. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `sessions` ADD COLUMN `pronunciationScore` INTEGER")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pronunciation_attempts` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` TEXT NOT NULL, `target` TEXT NOT NULL, " +
                "`heard` TEXT NOT NULL, `accuracyPercent` INTEGER NOT NULL, `passed` INTEGER NOT NULL, " +
                "`attemptNumber` INTEGER NOT NULL, `modelSaidCorrect` INTEGER NOT NULL, `problemWords` TEXT NOT NULL, " +
                "`notes` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, " +
                "FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_pronunciation_attempts_sessionId` ON `pronunciation_attempts` (`sessionId`)"
        )
    }
}

/** v4: Azure Pronunciation Assessment scores per attempt. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf("azurePronScore", "azureAccuracy", "azureFluency", "azureCompleteness").forEach { column ->
            db.execSQL("ALTER TABLE `pronunciation_attempts` ADD COLUMN `$column` INTEGER")
        }
        db.execSQL("ALTER TABLE `pronunciation_attempts` ADD COLUMN `azureWeakSounds` TEXT")
    }
}

/** v5: Adaptive level recommendations (direction, recommended level, reason). */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `sessions` ADD COLUMN `recommendedLevel` TEXT")
        db.execSQL("ALTER TABLE `sessions` ADD COLUMN `levelRecommendationDirection` TEXT")
        db.execSQL("ALTER TABLE `sessions` ADD COLUMN `levelRecommendationReason` TEXT")
    }
}

/**
 * v6: what the AI remembers between lessons. Mistakes get their own review schedule (mistake review
 * mode) and are filled from the corrections already stored, so existing learners can start reviewing
 * right away. Pronunciation drills are left out: their "mistakes" are speech recognition output.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `mistakes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `original` TEXT NOT NULL, " +
                "`normalizedOriginal` TEXT NOT NULL, `corrected` TEXT NOT NULL, `explanation` TEXT NOT NULL, `sessionId` TEXT, " +
                "`createdAt` INTEGER NOT NULL, `reviewCount` INTEGER NOT NULL, `nextReviewAt` INTEGER NOT NULL, " +
                "`lastReviewedAt` INTEGER, `timesMade` INTEGER NOT NULL, " +
                "FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_mistakes_normalizedOriginal` ON `mistakes` (`normalizedOriginal`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_mistakes_sessionId` ON `mistakes` (`sessionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_mistakes_nextReviewAt` ON `mistakes` (`nextReviewAt`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `learner_facts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fact` TEXT NOT NULL, " +
                "`normalizedFact` TEXT NOT NULL, `sessionId` TEXT, `createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_learner_facts_normalizedFact` ON `learner_facts` (`normalizedFact`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_learner_facts_sessionId` ON `learner_facts` (`sessionId`)")

        val key = "lower(trim(%s, '${MemoryText.TRIM_CHARS}'))"
        // Newest first, so the latest correction wins when the same mistake was made in several lessons.
        db.execSQL(
            "INSERT OR IGNORE INTO `mistakes` (original, normalizedOriginal, corrected, explanation, sessionId, createdAt, " +
                "reviewCount, nextReviewAt, lastReviewedAt, timesMade) " +
                "SELECT trim(c.original), ${key.format("c.original")}, trim(c.corrected), c.explanation, c.sessionId, s.endedAt, " +
                "0, s.endedAt + $ONE_DAY_MS, NULL, " +
                "(SELECT COUNT(DISTINCT c2.sessionId) FROM corrections c2 WHERE ${key.format("c2.original")} = ${key.format("c.original")}) " +
                "FROM corrections c JOIN sessions s ON s.id = c.sessionId " +
                "WHERE ${key.format("c.original")} != '' AND trim(c.corrected) != '' AND s.mode != 'REPEAT_AFTER_ME' " +
                "ORDER BY s.startedAt DESC, c.id DESC"
        )
    }
}

private const val ONE_DAY_MS = 86_400_000L

/**
 * v7: objective fluency metrics, IELTS speaking scores, story comprehension quiz,
 * collocation marking for learned words, and personalized custom scenarios.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf(
            "wordsPerMinute INTEGER",
            "fillerWordsCount INTEGER",
            "fillerWordsRatio REAL",
            "meanLengthOfUtterance REAL",
            "vietnameseWordsRatio REAL",
            "comprehensionScore INTEGER",
            "ieltsBandScore REAL"
        ).forEach { columnDef ->
            db.execSQL("ALTER TABLE `sessions` ADD COLUMN $columnDef")
        }

        db.execSQL("ALTER TABLE `learned_words` ADD COLUMN `isCollocation` INTEGER NOT NULL DEFAULT 0")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `custom_scenarios` (" +
                "`id` TEXT PRIMARY KEY NOT NULL, " +
                "`titleVi` TEXT NOT NULL, " +
                "`titleEn` TEXT NOT NULL, " +
                "`aiRole` TEXT NOT NULL, " +
                "`learnerRole` TEXT NOT NULL, " +
                "`customContext` TEXT NOT NULL, " +
                "`missionObjective` TEXT, " +
                "`createdAt` INTEGER NOT NULL" +
            ")"
        )
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)

