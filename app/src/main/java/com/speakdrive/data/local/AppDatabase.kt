package com.speakdrive.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.speakdrive.data.local.dao.MessageDao
import com.speakdrive.data.local.dao.SessionDao
import com.speakdrive.data.local.dao.WordDao
import com.speakdrive.data.local.entity.ConversationMessageEntity
import com.speakdrive.data.local.entity.ConversationSessionEntity
import com.speakdrive.data.local.entity.LearnedWordEntity

@Database(
    entities = [
        ConversationSessionEntity::class,
        ConversationMessageEntity::class,
        LearnedWordEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun wordDao(): WordDao
}
