package com.speakdrive.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.speakdrive.data.local.dao.MemoryDao
import com.speakdrive.data.local.dao.SessionDao
import com.speakdrive.data.local.dao.WordDao
import com.speakdrive.data.local.entity.CorrectionEntity
import com.speakdrive.data.local.entity.LearnedWordEntity
import com.speakdrive.data.local.entity.LearnerFactEntity
import com.speakdrive.data.local.entity.MessageEntity
import com.speakdrive.data.local.entity.MistakeEntity
import com.speakdrive.data.local.entity.PronunciationAttemptEntity
import com.speakdrive.data.local.entity.SessionEntity

@Database(
    entities = [
        SessionEntity::class,
        MessageEntity::class,
        CorrectionEntity::class,
        LearnedWordEntity::class,
        PronunciationAttemptEntity::class,
        MistakeEntity::class,
        LearnerFactEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun wordDao(): WordDao
    abstract fun memoryDao(): MemoryDao
}
