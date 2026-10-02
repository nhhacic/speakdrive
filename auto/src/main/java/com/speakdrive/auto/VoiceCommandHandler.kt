package com.speakdrive.auto

import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.Futures

class VoiceCommandHandler {

    fun handlePlayFromSearch(
        query: String,
        extras: android.os.Bundle?
    ): ListenableFuture<SessionResult> {
        val lowerQuery = query.lowercase()

        val mediaId = when {
            lowerQuery.isEmpty() || lowerQuery.contains("resume") || lowerQuery.contains("tiếp tục") -> {
                MediaContentProvider.RESUME_ID
            }
            lowerQuery.contains("random") || lowerQuery.contains("ngẫu nhiên") -> {
                MediaContentProvider.RANDOM_ID
            }
            else -> {
                val matchedTopic = MediaContentProvider.topics.find { 
                    lowerQuery.contains(it.lowercase()) 
                }
                
                if (matchedTopic != null) {
                    "topic_${matchedTopic.lowercase()}"
                } else {
                    when {
                        lowerQuery.contains("easy") || lowerQuery.contains("beginner") || lowerQuery.contains("dễ") -> "level_beginner"
                        lowerQuery.contains("hard") || lowerQuery.contains("advanced") || lowerQuery.contains("khó") -> "level_advanced"
                        else -> MediaContentProvider.RESUME_ID // Default fallback
                    }
                }
            }
        }

        // Return successful result as we processed the voice command
        return Futures.immediateFuture(
            SessionResult(SessionResult.RESULT_SUCCESS)
        )
    }
}
