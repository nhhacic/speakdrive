package com.speakdrive.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.speakdrive.data.local.entity.ConversationMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ConversationMessageEntity)

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesBySession(sessionId: String): Flow<List<ConversationMessageEntity>>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId AND correction IS NOT NULL ORDER BY timestamp ASC")
    fun getMessagesWithCorrections(sessionId: String): Flow<List<ConversationMessageEntity>>
}
