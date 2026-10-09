package com.speakdrive.auto

import com.speakdrive.ai.TopicManager
import com.speakdrive.ai.model.ActiveLesson

/** Painted backdrop behind a story illustration; colours stay dark so Android Auto's scrim keeps it calm. */
internal enum class Backdrop(val top: Int, val bottom: Int, val glow: Int) {
    NIGHT(0xFF1B1446.toInt(), 0xFF070912.toInt(), 0xFF8B5CF6.toInt()),
    SPACE(0xFF0B0820.toInt(), 0xFF02030A.toInt(), 0xFF6366F1.toInt()),
    OCEAN(0xFF0C2A4A.toInt(), 0xFF04101F.toInt(), 0xFF0EA5E9.toInt()),
    SNOW(0xFF1E3A5F.toInt(), 0xFF0B1422.toInt(), 0xFF93C5FD.toInt()),
    JUNGLE(0xFF0F3B2E.toInt(), 0xFF04140F.toInt(), 0xFF34D399.toInt()),
    DESERT(0xFF4A2A12.toInt(), 0xFF140A05.toInt(), 0xFFF59E0B.toInt()),
    CITY(0xFF3B1F4A.toInt(), 0xFF0E0A18.toInt(), 0xFFF472B6.toInt()),
    LAB(0xFF0F2F3A.toInt(), 0xFF050F14.toInt(), 0xFF2DD4BF.toInt())
}

/**
 * A wordless picture for a story: one big emoji, a few smaller ones around it and a backdrop.
 * [heroes] is in order of preference; older phones fall back to the next emoji their font has.
 */
internal data class StoryScene(val heroes: List<String>, val props: List<String>, val backdrop: Backdrop)

/** Picks the illustration for a story lesson, so the car screen shows a picture instead of text. */
internal object StoryScenes {

    fun sceneFor(lesson: ActiveLesson): StoryScene {
        lesson.scenario?.id?.let { id -> BY_SCENARIO[id]?.let { return it } }

        // AI-made and web stories have no fixed id; their title still says what they are about.
        val about = listOfNotNull(
            lesson.scenario?.titleEn,
            lesson.scenario?.titleVi,
            lesson.resumeStoryTitle,
            lesson.scenario?.customContext?.take(300)
        ).joinToString(" ")
        val words = " ${TopicManager.normalize(about)} "
        KEYWORD_SCENES.firstOrNull { (keywords, _) -> keywords.any { words.contains(" $it ") } }?.let { return it.second }

        return BY_TOPIC[lesson.topic.id]
            ?: StoryScene(listOf(lesson.topic.emoji, "📖"), listOf("✨", "🎧", "🌙"), Backdrop.NIGHT)
    }

    /** One scene per curated story in TopicManager. */
    val BY_SCENARIO: Map<String, StoryScene> = mapOf(
        // Detectives & heists
        "story_antwerp_diamond" to StoryScene(listOf("💎"), listOf("🔦", "🔐", "🚨"), Backdrop.NIGHT),
        "story_db_cooper" to StoryScene(listOf("✈️"), listOf("🪂", "💰", "🌧️"), Backdrop.NIGHT),
        "story_mona_lisa_heist" to StoryScene(listOf("🖼️"), listOf("🎨", "🔑", "👣"), Backdrop.NIGHT),
        "story_speckled_band" to StoryScene(listOf("🐍"), listOf("🔍", "🕯️", "🎻"), Backdrop.NIGHT),
        // Survival
        "story_aron_ralston" to StoryScene(listOf("🏜️"), listOf("🪨", "🧗", "☀️"), Backdrop.DESERT),
        "story_tham_luang" to StoryScene(listOf("🤿", "⛑️"), listOf("🔦", "🌊", "⚽"), Backdrop.OCEAN),
        "story_miracle_hudson" to StoryScene(listOf("✈️"), listOf("🐦", "🌊", "🚤"), Backdrop.OCEAN),
        "story_jan_baalsrud" to StoryScene(listOf("🏔️"), listOf("❄️", "⛷️", "🧭"), Backdrop.SNOW),
        // Comedy
        "story_paris_dinner" to StoryScene(listOf("🍷"), listOf("🥖", "🧀", "🗼"), Backdrop.CITY),
        "story_wrong_side_driving" to StoryScene(listOf("🚗"), listOf("🔄", "🚌", "☂️"), Backdrop.CITY),
        "story_backpacking_duke" to StoryScene(listOf("🎒"), listOf("👑", "☕", "🌹"), Backdrop.CITY),
        "story_ikea_labyrinth" to StoryScene(listOf("🛋️"), listOf("📦", "🔧", "🧭"), Backdrop.CITY),
        // Adventure
        "story_shackleton" to StoryScene(listOf("🚢"), listOf("🧊", "🐧", "❄️"), Backdrop.SNOW),
        "story_andes_flight" to StoryScene(listOf("🏔️"), listOf("✈️", "❄️", "🔥"), Backdrop.SNOW),
        "story_juliane_amazon" to StoryScene(listOf("🌴"), listOf("🦜", "🐊", "🌧️"), Backdrop.JUNGLE),
        "story_alcatraz_escape" to StoryScene(listOf("🏝️"), listOf("🛶", "🔒", "🌊"), Backdrop.OCEAN),
        // Classic mysteries
        "story_sherlock_red_headed" to StoryScene(listOf("🔍"), listOf("🎩", "💰", "🏦"), Backdrop.NIGHT),
        "story_poe_tell_tale" to StoryScene(listOf("🫀", "❤️"), listOf("🕯️", "👁️", "⏰"), Backdrop.NIGHT),
        "story_monkeys_paw" to StoryScene(listOf("🐒"), listOf("🐾", "🕯️", "✨"), Backdrop.NIGHT),
        "story_mary_celeste" to StoryScene(listOf("⛵"), listOf("🌫️", "👻", "🧭"), Backdrop.OCEAN),
        // Sci-fi
        "story_apollo_13" to StoryScene(listOf("🚀"), listOf("🌕", "👨‍🚀", "🛰️"), Backdrop.SPACE),
        "story_time_machine" to StoryScene(listOf("⏳"), listOf("⚙️", "🕰️", "🌀"), Backdrop.SPACE),
        "story_asimov_robot" to StoryScene(listOf("🤖"), listOf("⚙️", "🧠", "💡"), Backdrop.LAB),
        "story_voyager_record" to StoryScene(listOf("🛰️"), listOf("💿", "🪐", "✨"), Backdrop.SPACE),
        // Famous people
        "story_steve_jobs" to StoryScene(listOf("📱"), listOf("🍎", "💡", "🎤"), Backdrop.LAB),
        "story_marie_curie" to StoryScene(listOf("⚗️"), listOf("☢️", "🔬", "✨"), Backdrop.LAB),
        "story_alan_turing" to StoryScene(listOf("🔐"), listOf("⚙️", "📻", "💡"), Backdrop.LAB),
        "story_einstein" to StoryScene(listOf("⚛️", "💡"), listOf("🕰️", "✨", "🧠"), Backdrop.LAB),
        // Science
        "story_penicillin" to StoryScene(listOf("🧫", "🔬"), listOf("💊", "🍄", "✨"), Backdrop.LAB),
        "story_black_holes" to StoryScene(listOf("🌌"), listOf("🕳️", "✨", "🌠"), Backdrop.SPACE),
        "story_deep_sea" to StoryScene(listOf("🐙"), listOf("🐠", "🦑", "🌊"), Backdrop.OCEAN),
        "story_ai_evolution" to StoryScene(listOf("🤖"), listOf("🧠", "💻", "✨"), Backdrop.LAB),
        // History
        "story_titanic" to StoryScene(listOf("🚢"), listOf("🧊", "🌊", "🌙"), Backdrop.OCEAN),
        "story_pyramids" to StoryScene(listOf("🐫"), listOf("☀️", "🏺", "📐"), Backdrop.DESERT),
        "story_bermuda" to StoryScene(listOf("🌀"), listOf("✈️", "🚢", "🌊"), Backdrop.OCEAN),
        "story_silk_road" to StoryScene(listOf("🐫"), listOf("🧵", "🏮", "🗺️"), Backdrop.DESERT)
    )

    /** Fallback per story topic, for stories in a topic without a scene of their own. */
    private val BY_TOPIC: Map<String, StoryScene> = mapOf(
        "story_detective_heists" to StoryScene(listOf("🕵️"), listOf("🔍", "💎", "🔦"), Backdrop.NIGHT),
        "story_extreme_survival" to StoryScene(listOf("🧗"), listOf("⛰️", "🔥", "🧭"), Backdrop.SNOW),
        "story_comedy_misadventures" to StoryScene(listOf("🎭"), listOf("😂", "🎉", "🍿"), Backdrop.CITY),
        "story_adventure" to StoryScene(listOf("🧭"), listOf("🗺️", "⛵", "🏔️"), Backdrop.OCEAN),
        "story_classic_mystery" to StoryScene(listOf("🔍"), listOf("🕯️", "🗝️", "👻"), Backdrop.NIGHT),
        "story_sci_fi_tales" to StoryScene(listOf("🚀"), listOf("🪐", "🛸", "🤖"), Backdrop.SPACE),
        "story_famous" to StoryScene(listOf("🌟"), listOf("💡", "📜", "🏆"), Backdrop.LAB),
        "story_science" to StoryScene(listOf("🔬"), listOf("⚗️", "🧬", "💡"), Backdrop.LAB),
        "story_history" to StoryScene(listOf("🏛️"), listOf("📜", "⚔️", "🏺"), Backdrop.DESERT)
    )

    /** Accent-free keywords (as [TopicManager.normalize] leaves them), checked in order. */
    private val KEYWORD_SCENES: List<Pair<List<String>, StoryScene>> = listOf(
        listOf("space", "rocket", "astronaut", "nasa", "planet", "galaxy", "mars", "moon", "alien", "vu tru", "ten lua", "hanh tinh", "phi hanh gia") to
            StoryScene(listOf("🚀"), listOf("🪐", "🌕", "✨"), Backdrop.SPACE),
        listOf("ghost", "haunted", "horror", "vampire", "con ma", "kinh di", "ma am") to
            StoryScene(listOf("👻"), listOf("🕯️", "🌙", "🦇"), Backdrop.NIGHT),
        listOf("detective", "mystery", "crime", "heist", "sherlock", "murder", "thief", "trinh tham", "bi an", "vu an", "vu cuop") to
            StoryScene(listOf("🕵️"), listOf("🔍", "🗝️", "🕯️"), Backdrop.NIGHT),
        listOf("dragon", "magic", "wizard", "fairy", "princess", "castle", "co tich", "phep thuat", "con rong", "cong chua") to
            StoryScene(listOf("🐉"), listOf("🏰", "✨", "🪄"), Backdrop.NIGHT),
        listOf("ocean", "sea", "ship", "titanic", "pirate", "island", "sailor", "whale", "shark", "bien", "dai duong", "cuop bien", "hon dao") to
            StoryScene(listOf("⛵"), listOf("🌊", "🐳", "🧭"), Backdrop.OCEAN),
        listOf("snow", "arctic", "antarctic", "everest", "mountain", "glacier", "winter", "tuyet", "nui", "bang gia") to
            StoryScene(listOf("🏔️"), listOf("❄️", "⛷️", "🧭"), Backdrop.SNOW),
        listOf("jungle", "amazon", "forest", "rainforest", "safari", "rung", "rung ram") to
            StoryScene(listOf("🌴"), listOf("🦜", "🐒", "🌿"), Backdrop.JUNGLE),
        listOf("desert", "egypt", "pyramid", "pyramids", "sahara", "pharaoh", "sa mac", "kim tu thap", "ai cap") to
            StoryScene(listOf("🐫"), listOf("☀️", "🏺", "📜"), Backdrop.DESERT),
        listOf("robot", "robots", "computer", "invention", "inventor", "science", "scientist", "laboratory", "khoa hoc", "phat minh", "nha khoa hoc") to
            StoryScene(listOf("🔬"), listOf("💡", "⚙️", "🧬"), Backdrop.LAB),
        listOf("history", "ancient", "empire", "war", "king", "emperor", "lich su", "co dai", "de che", "chien tranh", "nha vua") to
            StoryScene(listOf("🏛️"), listOf("⚔️", "📜", "👑"), Backdrop.DESERT),
        listOf("funny", "comedy", "hilarious", "hai huoc", "vui nhon") to
            StoryScene(listOf("🎭"), listOf("😂", "🎉", "🍿"), Backdrop.CITY),
        listOf("food", "cook", "cooking", "chef", "restaurant", "am thuc", "nau an", "dau bep") to
            StoryScene(listOf("🍜"), listOf("👨‍🍳", "🍰", "☕"), Backdrop.CITY)
    )
}
