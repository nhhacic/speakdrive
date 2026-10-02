package com.speakdrive.ai

import com.speakdrive.ai.model.Topic
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TopicManager @Inject constructor() {

    private val topics = listOf(
        Topic(
            id = "travel",
            titleVi = "Du lịch",
            titleEn = "Travel",
            description = "Discussing travel plans, experiences, and destinations.",
            iconRes = 0,
            subScenarios = listOf("Booking a hotel", "At the airport", "Asking for directions", "Describing a trip")
        ),
        Topic(
            id = "work",
            titleVi = "Công việc",
            titleEn = "Work",
            description = "Talking about jobs, careers, and the workplace.",
            iconRes = 0,
            subScenarios = listOf("Job interview", "Meeting with colleagues", "Discussing a project", "Working remotely")
        ),
        Topic(
            id = "food",
            titleVi = "Ẩm thực",
            titleEn = "Food",
            description = "Conversations about food, cooking, and eating out.",
            iconRes = 0,
            subScenarios = listOf("Ordering at a restaurant", "Sharing recipes", "Dietary preferences", "Street food")
        ),
        Topic(
            id = "shopping",
            titleVi = "Mua sắm",
            titleEn = "Shopping",
            description = "Buying things, bargaining, and consumer culture.",
            iconRes = 0,
            subScenarios = listOf("At a clothing store", "Supermarket run", "Online shopping", "Returning an item")
        ),
        Topic(
            id = "health",
            titleVi = "Sức khỏe",
            titleEn = "Health",
            description = "Discussing well-being, fitness, and medical issues.",
            iconRes = 0,
            subScenarios = listOf("At the doctor's clinic", "Gym routine", "Mental health", "Healthy eating")
        ),
        Topic(
            id = "entertainment",
            titleVi = "Giải trí",
            titleEn = "Entertainment",
            description = "Movies, music, hobbies, and pop culture.",
            iconRes = 0,
            subScenarios = listOf("Reviewing a movie", "Concert experience", "Video games", "Reading books")
        ),
        Topic(
            id = "daily",
            titleVi = "Hàng ngày",
            titleEn = "Daily Life",
            description = "Everyday routines, chores, and small talk.",
            iconRes = 0,
            subScenarios = listOf("Morning routine", "Weekend plans", "Weather", "Pet care")
        ),
        Topic(
            id = "interview",
            titleVi = "Phỏng vấn",
            titleEn = "Interview",
            description = "Practicing for various types of interviews.",
            iconRes = 0,
            subScenarios = listOf("Self-introduction", "Strengths and weaknesses", "Behavioral questions", "Salary negotiation")
        )
    )

    fun getAllTopics(): List<Topic> = topics

    fun getTopicById(id: String): Topic? = topics.find { it.id == id }

    fun findTopicByQuery(query: String): Topic? {
        val lowerQuery = query.lowercase()
        return topics.find { 
            it.titleEn.lowercase().contains(lowerQuery) || 
            it.titleVi.lowercase().contains(lowerQuery) ||
            it.id.lowercase().contains(lowerQuery)
        }
    }

    fun suggestTopic(historyIds: List<String>): Topic {
        val unvisitedTopics = topics.filterNot { historyIds.contains(it.id) }
        return if (unvisitedTopics.isNotEmpty()) {
            unvisitedTopics.random()
        } else {
            topics.random()
        }
    }
}
