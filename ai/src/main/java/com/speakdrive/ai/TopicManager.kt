package com.speakdrive.ai

import com.speakdrive.ai.model.Scenario
import com.speakdrive.ai.model.Topic
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for conversation topics, used by the phone UI,
 * the Android Auto media tree and voice search.
 */
@Singleton
class TopicManager @Inject constructor() {

    private val topics = listOf(
        Topic(
            id = "travel",
            titleVi = "Du lịch & Đi lại",
            titleEn = "Travel & Getting Around",
            description = "Travel plans, trips, airports, hotels and asking for directions.",
            emoji = "🏖️",
            keywords = listOf("travel", "trip", "holiday", "vacation", "airport", "hotel", "du lich", "di lai"),
            scenarios = listOf(
                Scenario("travel_hotel", "Nhận phòng khách sạn", "Checking into a hotel", "a friendly hotel receptionist", "a guest checking in"),
                Scenario("travel_airport", "Làm thủ tục ở sân bay", "At the airport check-in", "an airline check-in agent", "a passenger flying abroad"),
                Scenario("travel_directions", "Hỏi đường", "Asking for directions", "a helpful local on the street", "a tourist who is lost"),
                Scenario("travel_taxi", "Đi taxi", "Taking a taxi", "a chatty taxi driver", "a passenger going downtown")
            )
        ),
        Topic(
            id = "work",
            titleVi = "Công việc & Kinh doanh",
            titleEn = "Work & Business",
            description = "Jobs, meetings, colleagues, projects and business small talk.",
            emoji = "💼",
            keywords = listOf("work", "business", "job", "office", "meeting", "cong viec", "kinh doanh"),
            scenarios = listOf(
                Scenario("work_meeting", "Họp nhóm", "Team meeting", "a team manager running a weekly meeting", "a team member giving an update"),
                Scenario("work_client", "Gặp khách hàng", "Meeting a client", "a potential client", "a salesperson presenting a product"),
                Scenario("work_boss", "Xin nghỉ phép", "Asking your boss for leave", "a busy but fair boss", "an employee asking for time off"),
                Scenario("work_smalltalk", "Trò chuyện với đồng nghiệp", "Small talk with a colleague", "a friendly coworker at the coffee machine", "a colleague")
            )
        ),
        Topic(
            id = "food",
            titleVi = "Ăn uống & Nhà hàng",
            titleEn = "Food & Dining",
            description = "Food, cooking, restaurants and ordering a meal.",
            emoji = "🍽️",
            keywords = listOf("food", "restaurant", "dining", "eat", "cooking", "an uong", "nha hang"),
            scenarios = listOf(
                Scenario("food_order", "Gọi món ở nhà hàng", "Ordering at a restaurant", "a waiter at a busy restaurant", "a customer ordering dinner"),
                Scenario("food_coffee", "Mua cà phê", "Ordering coffee", "a barista at a coffee shop", "a customer"),
                Scenario("food_complaint", "Phàn nàn về món ăn", "Complaining about a dish", "a restaurant manager", "a customer whose dish is wrong"),
                Scenario("food_recipe", "Chia sẻ công thức", "Sharing a recipe", "a curious friend who loves cooking", "someone explaining a Vietnamese dish")
            )
        ),
        Topic(
            id = "shopping",
            titleVi = "Mua sắm",
            titleEn = "Shopping",
            description = "Buying things, prices, sizes, returns and online shopping.",
            emoji = "🛒",
            keywords = listOf("shopping", "shop", "buy", "store", "mua sam"),
            scenarios = listOf(
                Scenario("shopping_clothes", "Mua quần áo", "Buying clothes", "a shop assistant in a clothing store", "a customer looking for a jacket"),
                Scenario("shopping_return", "Đổi trả hàng", "Returning an item", "a customer service clerk", "a customer returning a broken item"),
                Scenario("shopping_market", "Đi chợ", "At the market", "a market vendor", "a customer bargaining for fruit"),
                Scenario("shopping_phone", "Mua điện thoại", "Buying a phone", "an electronics store salesperson", "a customer comparing phones")
            )
        ),
        Topic(
            id = "health",
            titleVi = "Y tế & Sức khỏe",
            titleEn = "Health & Medical",
            description = "Health, fitness, feeling unwell and visiting a doctor.",
            emoji = "🏥",
            keywords = listOf("health", "medical", "doctor", "hospital", "fitness", "y te", "suc khoe"),
            scenarios = listOf(
                Scenario("health_doctor", "Khám bệnh", "At the doctor's", "a kind family doctor", "a patient with a sore throat"),
                Scenario("health_pharmacy", "Mua thuốc", "At the pharmacy", "a pharmacist", "a customer asking for medicine"),
                Scenario("health_gym", "Đăng ký phòng gym", "Joining a gym", "a gym receptionist", "a new member"),
                Scenario("health_appointment", "Đặt lịch khám", "Booking an appointment", "a clinic receptionist on the phone", "a patient booking a visit")
            )
        ),
        Topic(
            id = "entertainment",
            titleVi = "Giải trí & Phim ảnh",
            titleEn = "Entertainment & Movies",
            description = "Movies, music, hobbies, games, books and weekend fun.",
            emoji = "🎭",
            keywords = listOf("entertainment", "movie", "movies", "film", "music", "hobby", "giai tri", "phim"),
            scenarios = listOf(
                Scenario("ent_movie", "Bàn về một bộ phim", "Talking about a movie", "a friend who just saw the same movie", "a movie fan"),
                Scenario("ent_tickets", "Mua vé xem phim", "Buying cinema tickets", "a cinema ticket seller", "a customer"),
                Scenario("ent_concert", "Rủ bạn đi xem hòa nhạc", "Inviting a friend to a concert", "a friend with a busy schedule", "someone inviting them"),
                Scenario("ent_hobby", "Giới thiệu sở thích", "Sharing your hobbies", "a new acquaintance at a party", "someone talking about hobbies")
            )
        ),
        Topic(
            id = "daily",
            titleVi = "Giao tiếp hàng ngày",
            titleEn = "Daily Conversation",
            description = "Everyday life: routines, weather, family, neighbours and small talk.",
            emoji = "💬",
            keywords = listOf("daily", "everyday", "small talk", "life", "giao tiep", "hang ngay"),
            scenarios = listOf(
                Scenario("daily_neighbor", "Chào hỏi hàng xóm", "Chatting with a neighbour", "a friendly neighbour", "a neighbour"),
                Scenario("daily_weekend", "Kế hoạch cuối tuần", "Weekend plans", "a close friend", "a friend making plans"),
                Scenario("daily_phone", "Gọi điện cho bạn", "Calling a friend", "an old friend on the phone", "the caller"),
                Scenario("daily_new_friend", "Làm quen bạn mới", "Making a new friend", "a friendly stranger at a cafe", "someone starting a conversation")
            )
        ),
        Topic(
            id = "interview",
            titleVi = "Phỏng vấn xin việc",
            titleEn = "Job Interview",
            description = "Job interviews: introductions, strengths, experience and salary.",
            emoji = "🎯",
            keywords = listOf("interview", "job interview", "career", "phong van", "xin viec"),
            scenarios = listOf(
                Scenario("interview_intro", "Giới thiệu bản thân", "Tell me about yourself", "an HR interviewer", "a job candidate"),
                Scenario("interview_behavioral", "Câu hỏi tình huống", "Behavioural questions", "a hiring manager", "a candidate"),
                Scenario("interview_strengths", "Điểm mạnh & điểm yếu", "Strengths and weaknesses", "a senior interviewer", "a candidate"),
                Scenario("interview_salary", "Đàm phán lương", "Salary negotiation", "an HR manager making an offer", "a candidate negotiating")
            )
        )
    )

    fun getAllTopics(): List<Topic> = topics

    fun getTopicById(id: String?): Topic? = topics.find { it.id == id }

    fun getScenario(scenarioId: String?): Pair<Topic, Scenario>? {
        if (scenarioId == null) return null
        for (topic in topics) {
            topic.scenarios.find { it.id == scenarioId }?.let { return topic to it }
        }
        return null
    }

    /**
     * Finds the topic a spoken query refers to, e.g. "travel English", "phỏng vấn".
     * Matching ignores case and Vietnamese diacritics.
     */
    fun findTopicByQuery(query: String): Topic? {
        val q = normalize(query)
        if (q.isBlank()) return null
        return topics.firstOrNull { topic ->
            val candidates = listOf(topic.id, topic.titleEn, topic.titleVi) + topic.keywords
            candidates.any { candidate ->
                val c = normalize(candidate)
                // Whole-word match either way so "work" does not match "network".
                containsWord(q, c) || containsWord(c, q)
            }
        }
    }

    /** Prefers topics the learner has not practised recently. */
    fun suggestTopic(recentTopicIds: List<String>): Topic {
        val unvisited = topics.filterNot { it.id in recentTopicIds }
        return (unvisited.ifEmpty { topics }).random()
    }

    companion object {
        /** Lower-cases and strips Vietnamese diacritics: "Phỏng vấn" -> "phong van". */
        fun normalize(text: String): String {
            val decomposed = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            return decomposed
                .replace(Regex("\\p{Mn}+"), "")
                .replace('đ', 'd')
                .replace(Regex("[^a-z0-9 ]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        private fun containsWord(haystack: String, needle: String): Boolean {
            if (needle.isBlank()) return false
            return Regex("(^| )${Regex.escape(needle)}( |$)").containsMatchIn(haystack)
        }
    }
}
