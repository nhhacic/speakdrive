package com.speakdrive.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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

val ALL_MIGRATIONS = arrayOf(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)

