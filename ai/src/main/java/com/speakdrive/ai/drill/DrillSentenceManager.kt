package com.speakdrive.ai.drill

import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.DrillCategory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A curated practice sentence for pronunciation and repetition drills.
 */
data class DrillSentence(
    val id: String,
    val text: String,
    val translationVi: String,
    val level: DifficultyLevel,
    val category: DrillCategory,
    val topicId: String? = null,
    val phoneticFocus: String? = null,
    val isShortForDriving: Boolean = true
)

/**
 * Curated repository and intelligent selector of high-yield pronunciation drill sentences.
 * Addresses Vietnamese phonetic pitfalls (ending consonants, difficult pairs, linking),
 * conversational reflexes, and hands-free driving safety constraints.
 */
@Singleton
class DrillSentenceManager @Inject constructor() {

    private val sentences = buildList {
        // =========================================================================
        // CATEGORY 1: VIETNAMESE PHONETIC PITFALLS (Ending sounds, /θ/, /ʃ/, /v/, linking)
        // =========================================================================

        // --- Beginner (A1) ---
        add(DrillSentence("vp_a1_01", "Stop the car here.", "Dừng xe ở đây.", DifficultyLevel.BEGINNER, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Final /p/", isShortForDriving = true))
        add(DrillSentence("vp_a1_02", "She likes hot tea.", "Cô ấy thích trà nóng.", DifficultyLevel.BEGINNER, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Ending -s /s/", isShortForDriving = true))
        add(DrillSentence("vp_a1_03", "This is his bag.", "Đây là túi của anh ấy.", DifficultyLevel.BEGINNER, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Ending /z/", isShortForDriving = true))
        add(DrillSentence("vp_a1_04", "Thank you very much.", "Cảm ơn bạn rất nhiều.", DifficultyLevel.BEGINNER, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Voiceless /θ/ & /v/", isShortForDriving = true))
        add(DrillSentence("vp_a1_05", "Look at that cat.", "Nhìn con mèo kìa.", DifficultyLevel.BEGINNER, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Final /t/", isShortForDriving = true))
        add(DrillSentence("vp_a1_06", "Open the big box.", "Mở chiếc hộp lớn ra.", DifficultyLevel.BEGINNER, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Final /ks/", isShortForDriving = true))
        add(DrillSentence("vp_a1_07", "He lives in Texas.", "Anh ấy sống ở Texas.", DifficultyLevel.BEGINNER, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Ending -s /vz/", isShortForDriving = true))
        add(DrillSentence("vp_a1_08", "Wash your face first.", "Rửa mặt trước đi.", DifficultyLevel.BEGINNER, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Ending /ʃ/ vs /st/", isShortForDriving = true))
        add(DrillSentence("vp_a1_09", "Turn off the lamp.", "Tắt chiếc đèn đi.", DifficultyLevel.BEGINNER, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Final /mp/", isShortForDriving = true))
        add(DrillSentence("vp_a1_10", "Pick it up now.", "Nhặt nó lên ngay.", DifficultyLevel.BEGINNER, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Linking pick-it-up", isShortForDriving = true))
        add(DrillSentence("vp_a1_11", "I need three eggs.", "Tôi cần ba quả trứng.", DifficultyLevel.BEGINNER, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "/θ/ & ending /gz/", isShortForDriving = true))
        add(DrillSentence("vp_a1_12", "She showed me this.", "Cô ấy cho tôi xem cái này.", DifficultyLevel.BEGINNER, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "/ʃ/ vs /ð/", isShortForDriving = true))

        // --- Elementary (A2) ---
        add(DrillSentence("vp_a2_01", "She noticed the lost keys.", "Cô ấy nhận ra chùm chìa khóa bị mất.", DifficultyLevel.ELEMENTARY, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Past -ed /st/ and plural -s", isShortForDriving = true))
        add(DrillSentence("vp_a2_02", "Check out the best prices.", "Tham khảo mức giá tốt nhất.", DifficultyLevel.ELEMENTARY, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Linking & final /st/, /sɪz/", isShortForDriving = true))
        add(DrillSentence("vp_a2_03", "He works at six clinics.", "Anh ấy làm việc ở sáu phòng khám.", DifficultyLevel.ELEMENTARY, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Clusters /rks/, /ks/, /ks/", isShortForDriving = true))
        add(DrillSentence("vp_a2_04", "We talked about both paths.", "Chúng tôi đã bàn về cả hai con đường.", DifficultyLevel.ELEMENTARY, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Past /kt/ & voiceless /θs/", isShortForDriving = true))
        add(DrillSentence("vp_a2_05", "Please pass the fresh fish.", "Làm ơn chuyển đĩa cá tươi.", DifficultyLevel.ELEMENTARY, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Ending /s/ vs /ʃ/", isShortForDriving = true))
        add(DrillSentence("vp_a2_06", "We visited very vast valleys.", "Chúng tôi đã thăm những thung lũng rất rộng lớn.", DifficultyLevel.ELEMENTARY, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Consonant /v/ vs /w/", isShortForDriving = true))
        add(DrillSentence("vp_a2_07", "They missed the last bus.", "Họ đã lỡ chuyến xe buýt cuối cùng.", DifficultyLevel.ELEMENTARY, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Past -ed /st/ & final /st/", isShortForDriving = true))
        add(DrillSentence("vp_a2_08", "Put it on my desk.", "Đặt nó lên bàn làm việc của tôi.", DifficultyLevel.ELEMENTARY, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Linking & final /sk/", isShortForDriving = true))
        add(DrillSentence("vp_a2_09", "Both brothers think alike.", "Cả hai anh em đều suy nghĩ giống nhau.", DifficultyLevel.ELEMENTARY, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Voiced /ð/ vs voiceless /θ/", isShortForDriving = true))
        add(DrillSentence("vp_a2_10", "Could you take it out?", "Bạn có thể mang nó ra ngoài không?", DifficultyLevel.ELEMENTARY, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Linking take-it-out", isShortForDriving = true))
        add(DrillSentence("vp_a2_11", "He loves fast sports cars.", "Anh ấy yêu thích những chiếc xe thể thao tốc độ.", DifficultyLevel.ELEMENTARY, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Ending /vz/, /st/, /ts/, /rz/", isShortForDriving = true))
        add(DrillSentence("vp_a2_12", "Clean the glasses with soap.", "Rửa sạch những chiếc ly bằng xà phòng.", DifficultyLevel.ELEMENTARY, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Plural /sɪz/ & final /p/", isShortForDriving = true))

        // --- Pre-Intermediate (A2–B1) ---
        add(DrillSentence("vp_b1_01", "She expects us to fix it.", "Cô ấy mong đợi chúng tôi sửa nó.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Cluster /kspɛkts/ & final /ks/", isShortForDriving = true))
        add(DrillSentence("vp_b1_02", "The guests loved the fresh seafood.", "Các vị khách rất thích hải sản tươi sống.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Final /sts/, /vd/, /ʃ/", isShortForDriving = true))
        add(DrillSentence("vp_b1_03", "Think through both things thoroughly.", "Hãy suy nghĩ kỹ lưỡng về cả hai điều đó.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Multiple /θ/ words", isShortForDriving = true))
        add(DrillSentence("vp_b1_04", "He stopped and asked for directions.", "Anh ấy đã dừng lại và hỏi đường.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Past -ed /pt/ & /skt/", isShortForDriving = true))
        add(DrillSentence("vp_b1_05", "We arrived safe and sound.", "Chúng tôi đã đến nơi an toàn bình yên.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Past /vd/ & linking safe-and-sound", isShortForDriving = true))
        add(DrillSentence("vp_b1_06", "Choose the freshest vegetables available.", "Hãy chọn những loại rau củ tươi nhất có sẵn.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Affricate /tʃ/ vs /dʒ/ & /ʃɪst/", isShortForDriving = true))
        add(DrillSentence("vp_b1_07", "First, test the entire guest list.", "Đầu tiên, hãy kiểm tra toàn bộ danh sách khách mời.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Triple final /st/: first, test, list", isShortForDriving = true))
        add(DrillSentence("vp_b1_08", "She sells special shoes on sale.", "Cô ấy bán những đôi giày đặc biệt đang giảm giá.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Contrast /s/ and /ʃ/", isShortForDriving = true))
        add(DrillSentence("vp_b1_09", "They risked everything to save friends.", "Họ mạo hiểm mọi thứ để cứu bạn bè.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Past /skt/ & plural /ndz/", isShortForDriving = true))
        add(DrillSentence("vp_b1_10", "Hold on a second, please.", "Làm ơn giữ máy một chút.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Connected speech hold-on-a", isShortForDriving = true))

        // --- Intermediate (B1) ---
        add(DrillSentence("vp_int_01", "The company launched six new products.", "Công ty đã ra mắt sáu sản phẩm mới.", DifficultyLevel.INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Past /ntʃt/, /ks/, /kts/", isShortForDriving = true))
        add(DrillSentence("vp_int_02", "He finished his speech without hesitation.", "Anh ấy đã hoàn thành bài phát biểu mà không hề do dự.", DifficultyLevel.INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Past /ʃt/, /tʃ/, /ð/", isShortForDriving = true))
        add(DrillSentence("vp_int_03", "These statistics surprise all the specialists.", "Những số liệu thống kê này làm ngạc nhiên tất cả các chuyên gia.", DifficultyLevel.INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Complex /st/ and plural /sts/", isShortForDriving = true))
        add(DrillSentence("vp_int_04", "We must discuss these specific requests.", "Chúng ta phải thảo luận về các yêu cầu cụ thể này.", DifficultyLevel.INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Final /sts/, /fɪk/, /sts/", isShortForDriving = true))
        add(DrillSentence("vp_int_05", "As far as I can tell.", "Theo như tôi được biết.", DifficultyLevel.INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Connected speech reduction", isShortForDriving = true))
        add(DrillSentence("vp_int_06", "She thoroughly checked all the facts.", "Cô ấy đã kiểm tra kỹ lưỡng mọi sự thật.", DifficultyLevel.INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Voiceless /θ/, past /kt/, /kts/", isShortForDriving = true))
        add(DrillSentence("vp_int_07", "Turn off the engine and relax.", "Tắt động cơ xe và thư giãn đi.", DifficultyLevel.INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Linking turn-off-the", isShortForDriving = true))
        add(DrillSentence("vp_int_08", "The flight was delayed for hours.", "Chuyến bay đã bị hoãn nhiều giờ.", DifficultyLevel.INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Past /eɪd/, /rz/", isShortForDriving = true))

        // --- Upper-Intermediate & Advanced (B2–C2) ---
        add(DrillSentence("vp_b2_01", "The scientists established strict testing standards.", "Các nhà khoa học đã thiết lập những tiêu chuẩn thử nghiệm nghiêm ngặt.", DifficultyLevel.UPPER_INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Plural /sts/, past /ʃt/, /kts/", isShortForDriving = true))
        add(DrillSentence("vp_b2_02", "He successfully managed multiple stressful risks.", "Anh ấy đã quản lý thành công nhiều rủi ro căng thẳng.", DifficultyLevel.UPPER_INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Adverb /li/, past /dʒd/, /sks/", isShortForDriving = true))
        add(DrillSentence("vp_b2_03", "Let's synthesize all three theoretical viewpoints.", "Hãy tổng hợp cả ba quan điểm lý thuyết.", DifficultyLevel.ADVANCED, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Repeated /θ/ in academic vocabulary", isShortForDriving = true))
        add(DrillSentence("vp_b2_04", "The legislation protects consumers against fraudulent schemes.", "Luật pháp bảo vệ người tiêu dùng chống lại các chiêu trò lừa đảo.", DifficultyLevel.ADVANCED, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Final /kts/, /mz/, /dʒənt/", isShortForDriving = true))

        // =========================================================================
        // CATEGORY 2: CONVERSATIONAL REFLEX & CHUNKS (Spoken Idioms & Native Collocations)
        // =========================================================================

        // --- Beginner (A1) ---
        add(DrillSentence("cr_a1_01", "Nice to meet you.", "Rất vui được gặp bạn.", DifficultyLevel.BEGINNER, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_a1_02", "Have a great day.", "Chúc một ngày tuyệt vời.", DifficultyLevel.BEGINNER, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_a1_03", "See you later today.", "Hẹn gặp lại bạn hôm nay.", DifficultyLevel.BEGINNER, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_a1_04", "Can you help me?", "Bạn có thể giúp tôi không?", DifficultyLevel.BEGINNER, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_a1_05", "Where is the restroom?", "Nhà vệ sinh ở đâu vậy?", DifficultyLevel.BEGINNER, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_a1_06", "I don't understand that.", "Tôi không hiểu điều đó.", DifficultyLevel.BEGINNER, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_a1_07", "How much is this?", "Cái này giá bao nhiêu?", DifficultyLevel.BEGINNER, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_a1_08", "I would like water.", "Tôi muốn một cốc nước.", DifficultyLevel.BEGINNER, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))

        // --- Elementary (A2) ---
        add(DrillSentence("cr_a2_01", "Take your time, no rush.", "Cứ thong thả, không phải vội.", DifficultyLevel.ELEMENTARY, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_a2_02", "It is totally up to you.", "Hoàn toàn tùy thuộc vào bạn.", DifficultyLevel.ELEMENTARY, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_a2_03", "I am running a bit late.", "Tôi đang bị muộn một chút.", DifficultyLevel.ELEMENTARY, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_a2_04", "That sounds like a plan.", "Nghe có vẻ là kế hoạch hay.", DifficultyLevel.ELEMENTARY, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_a2_05", "Let's call it a day.", "Hôm nay kết thúc ở đây thôi.", DifficultyLevel.ELEMENTARY, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_a2_06", "What do you think?", "Bạn nghĩ thế nào về điều này?", DifficultyLevel.ELEMENTARY, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_a2_07", "I didn't catch your name.", "Tôi chưa kịp nghe rõ tên bạn.", DifficultyLevel.ELEMENTARY, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_a2_08", "Could you say that again?", "Bạn có thể nói lại lần nữa không?", DifficultyLevel.ELEMENTARY, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_a2_09", "Long time no see!", "Lâu quá rồi không gặp bạn!", DifficultyLevel.ELEMENTARY, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_a2_10", "I'm heading out right now.", "Tôi chuẩn bị ra ngoài ngay bây giờ.", DifficultyLevel.ELEMENTARY, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))

        // --- Pre-Intermediate (A2–B1) ---
        add(DrillSentence("cr_b1_01", "I was wondering if you could help.", "Tôi tự hỏi liệu bạn có thể giúp tôi được không.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_b1_02", "To be honest, that makes sense.", "Nói thật lòng thì điều đó rất hợp lý.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_b1_03", "Don't get me wrong, but listen.", "Đừng hiểu lầm ý tôi, nhưng hãy lắng nghe nhé.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_b1_04", "Keep me in the loop.", "Có gì nhớ cập nhật cho tôi biết nhé.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_b1_05", "That rings a bell to me.", "Nghe quen quen đối với tôi đấy.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_b1_06", "Let's play it by ear.", "Cứ để tùy cơ ứng biến xem sao.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_b1_07", "It completely slipped my mind.", "Tôi quên béng mất điều đó.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_b1_08", "Let's touch base tomorrow morning.", "Sáng mai chúng ta trao đổi nhanh nhé.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_b1_09", "I couldn't agree with you more.", "Tôi hoàn toàn đồng ý với bạn.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_b1_10", "Long story short, everything worked out.", "Nói ngắn gọn là mọi chuyện đều êm đẹp.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))

        // --- Intermediate (B1) ---
        add(DrillSentence("cr_int_01", "As far as I'm concerned, it's great.", "Theo góc nhìn của tôi thì điều đó rất tuyệt.", DifficultyLevel.INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_int_02", "I have mixed feelings about this.", "Tôi có cảm xúc lẫn lộn về việc này.", DifficultyLevel.INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_int_03", "Let's get straight to the point.", "Hãy đi thẳng vào trọng tâm vấn đề.", DifficultyLevel.INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_int_04", "It goes without saying, of course.", "Điều đó là hiển nhiên rồi, dĩ nhiên.", DifficultyLevel.INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_int_05", "I'm completely on board with that.", "Tôi hoàn toàn ủng hộ ý kiến đó.", DifficultyLevel.INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_int_06", "Could you elaborate a bit more?", "Bạn có thể nói rõ hơn một chút được không?", DifficultyLevel.INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_int_07", "Fair enough, let's move forward together.", "Hợp lý đấy, chúng ta cùng tiến hành thôi.", DifficultyLevel.INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_int_08", "It is definitely worth a shot.", "Điều đó rất đáng để thử một lần.", DifficultyLevel.INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))

        // --- Upper-Intermediate & Advanced (B2–C2) ---
        add(DrillSentence("cr_b2_01", "At the end of the day, results matter.", "Sau tất cả, kết quả cuối cùng mới là điều quan trọng.", DifficultyLevel.UPPER_INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_b2_02", "We should weigh the pros and cons.", "Chúng ta nên cân nhắc kỹ mặt lợi và hại.", DifficultyLevel.UPPER_INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_b2_03", "Let's not jump to premature conclusions.", "Chúng ta đừng vội đưa ra kết luận hấp tấp.", DifficultyLevel.ADVANCED, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_b2_04", "That speaks volumes about their integrity.", "Điều đó nói lên rất nhiều về sự chính trực của họ.", DifficultyLevel.ADVANCED, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))

        // =========================================================================
        // CATEGORY 3: DRIVING & COMMUTE (Short, Punchy, Safety-Optimized for Car)
        // =========================================================================

        // --- Beginner (A1) ---
        add(DrillSentence("dr_a1_01", "Turn left here.", "Rẽ trái ở đây.", DifficultyLevel.BEGINNER, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_a1_02", "Turn right now.", "Rẽ phải ngay bây giờ.", DifficultyLevel.BEGINNER, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_a1_03", "Go straight ahead.", "Đi thẳng về phía trước.", DifficultyLevel.BEGINNER, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_a1_04", "Stop at red lights.", "Dừng lại khi đèn đỏ.", DifficultyLevel.BEGINNER, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_a1_05", "Park the car there.", "Đỗ xe ở đằng kia.", DifficultyLevel.BEGINNER, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_a1_06", "Fasten your seatbelt.", "Thắt dây an toàn vào.", DifficultyLevel.BEGINNER, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_a1_07", "Turn on the radio.", "Bật đài phát thanh lên.", DifficultyLevel.BEGINNER, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_a1_08", "Where is the gas?", "Trạm xăng ở đâu vậy?", DifficultyLevel.BEGINNER, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))

        // --- Elementary (A2) ---
        add(DrillSentence("dr_a2_01", "Take the second exit.", "Đi theo lối ra thứ hai.", DifficultyLevel.ELEMENTARY, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_a2_02", "Traffic is jammed ahead.", "Giao thông phía trước đang bị tắc nghẽn.", DifficultyLevel.ELEMENTARY, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_a2_03", "Watch out for pedestrians.", "Chú ý quan sát người đi bộ.", DifficultyLevel.ELEMENTARY, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_a2_04", "The gas tank is low.", "Bình xăng sắp cạn rồi.", DifficultyLevel.ELEMENTARY, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_a2_05", "Turn on your wipers.", "Bật cần gạt nước mưa lên.", DifficultyLevel.ELEMENTARY, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_a2_06", "Check your rear mirror.", "Kiểm tra gương chiếu hậu của bạn.", DifficultyLevel.ELEMENTARY, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_a2_07", "Keep both hands ready.", "Luôn giữ cả hai tay sẵn sàng.", DifficultyLevel.ELEMENTARY, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_a2_08", "Slow down on turns.", "Giảm tốc độ khi vào khúc cua.", DifficultyLevel.ELEMENTARY, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))

        // --- Pre-Intermediate & Intermediate (B1) ---
        add(DrillSentence("dr_b1_01", "Take the next highway exit.", "Rẽ vào lối ra cao tốc tiếp theo.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_b1_02", "Could you navigate to downtown?", "Bạn có thể dẫn đường tới trung tâm không?", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_b1_03", "Merge safely into express lanes.", "Nhập làn an toàn vào làn đường cao tốc.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_b1_04", "Signal right before pulling over.", "Bật xi nhan phải trước khi tấp vào lề.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_b1_05", "The tire pressure light blinked.", "Đèn cảnh báo áp suất lốp vừa nhấp nháy.", DifficultyLevel.INTERMEDIATE, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_b1_06", "Heavy rain reduces road visibility.", "Mưa lớn làm giảm tầm nhìn trên đường.", DifficultyLevel.INTERMEDIATE, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_b1_07", "Avoid toll roads during rush hour.", "Tránh các tuyến đường có thu phí vào giờ cao điểm.", DifficultyLevel.INTERMEDIATE, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_b1_08", "Road construction caused sudden delays.", "Công trình đường bộ gây ra ùn tắc bất ngờ.", DifficultyLevel.INTERMEDIATE, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))

        // =========================================================================
        // CATEGORY 4: BUSINESS & WORKPLACE (Meetings, Negotiations, Tech & Career)
        // =========================================================================

        // --- Elementary & Pre-Intermediate ---
        add(DrillSentence("bw_a2_01", "Let's schedule a meeting.", "Hãy lên lịch cho một cuộc họp.", DifficultyLevel.ELEMENTARY, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("bw_a2_02", "Could you send the report?", "Bạn có thể gửi bản báo cáo được không?", DifficultyLevel.ELEMENTARY, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("bw_a2_03", "We need to finish today.", "Chúng ta cần hoàn thành trong hôm nay.", DifficultyLevel.ELEMENTARY, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("bw_b1_01", "Let's align on our targets.", "Hãy thống nhất về các mục tiêu của chúng ta.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("bw_b1_02", "Who is heading this project?", "Ai đang phụ trách dự án này vậy?", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("bw_b1_03", "We hit our quarterly targets.", "Chúng ta đã đạt các mục tiêu của quý này.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("bw_b1_04", "Can we reschedule our call?", "Chúng ta có thể dời lại cuộc gọi được không?", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))

        // --- Intermediate (B1) & Upper-Intermediate (B2) ---
        add(DrillSentence("bw_int_01", "Let's negotiate a win-win deal.", "Hãy đàm phán một thỏa thuận đôi bên cùng có lợi.", DifficultyLevel.INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("bw_int_02", "We must mitigate potential risks.", "Chúng ta phải giảm thiểu các rủi ro tiềm ẩn.", DifficultyLevel.INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("bw_int_03", "Our customer retention rate jumped.", "Tỷ lệ giữ chân khách hàng của chúng ta đã tăng vọt.", DifficultyLevel.INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("bw_int_04", "Let's circle back tomorrow morning.", "Hãy thảo luận lại điều này vào sáng mai.", DifficultyLevel.INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("bw_b2_01", "What is our competitive advantage?", "Lợi thế cạnh tranh của chúng ta ở đây là gì?", DifficultyLevel.UPPER_INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("bw_b2_02", "We are scaling the engineering team.", "Chúng tôi đang mở rộng quy mô đội ngũ kỹ sư.", DifficultyLevel.UPPER_INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("bw_b2_03", "Let's automate the deployment pipeline.", "Hãy tự động hóa quy trình triển khai phần mềm.", DifficultyLevel.UPPER_INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "tech_it", isShortForDriving = true))
        add(DrillSentence("bw_b2_04", "Security compliance remains our top priority.", "Tuân thủ an ninh bảo mật vẫn là ưu tiên hàng đầu của chúng tôi.", DifficultyLevel.ADVANCED, DrillCategory.BUSINESS_WORK, topicId = "tech_it", isShortForDriving = true))

        // =========================================================================
        // CATEGORY 5: TRAVEL & DAILY LIFE (Hotels, Airports, Dining, Emergencies)
        // =========================================================================

        // --- Beginner (A1) & Elementary (A2) ---
        add(DrillSentence("td_a1_01", "A table for two, please.", "Làm ơn cho một bàn hai người.", DifficultyLevel.BEGINNER, DrillCategory.TRAVEL_DAILY, topicId = "food", isShortForDriving = true))
        add(DrillSentence("td_a1_02", "Could we have the bill?", "Làm ơn cho chúng tôi xin hóa đơn.", DifficultyLevel.BEGINNER, DrillCategory.TRAVEL_DAILY, topicId = "food", isShortForDriving = true))
        add(DrillSentence("td_a1_03", "Where is the boarding gate?", "Cổng lên máy bay ở đâu vậy?", DifficultyLevel.BEGINNER, DrillCategory.TRAVEL_DAILY, topicId = "travel", isShortForDriving = true))
        add(DrillSentence("td_a1_04", "I have a hotel booking.", "Tôi có đặt phòng khách sạn trước.", DifficultyLevel.BEGINNER, DrillCategory.TRAVEL_DAILY, topicId = "travel", isShortForDriving = true))
        add(DrillSentence("td_a2_01", "Can I pay by credit card?", "Tôi có thể thanh toán bằng thẻ tín dụng không?", DifficultyLevel.ELEMENTARY, DrillCategory.TRAVEL_DAILY, topicId = "shopping", isShortForDriving = true))
        add(DrillSentence("td_a2_02", "I would like a window seat.", "Tôi muốn một chỗ ngồi gần cửa sổ.", DifficultyLevel.ELEMENTARY, DrillCategory.TRAVEL_DAILY, topicId = "travel", isShortForDriving = true))
        add(DrillSentence("td_a2_03", "Do you have vegetarian food?", "Quán có món ăn chay không?", DifficultyLevel.ELEMENTARY, DrillCategory.TRAVEL_DAILY, topicId = "food", isShortForDriving = true))
        add(DrillSentence("td_a2_04", "I'd like to return this shirt.", "Tôi muốn đổi trả chiếc áo sơ mi này.", DifficultyLevel.ELEMENTARY, DrillCategory.TRAVEL_DAILY, topicId = "shopping", isShortForDriving = true))

        // --- Pre-Intermediate (A2–B1) & Intermediate (B1) ---
        add(DrillSentence("td_b1_01", "Could I request a late checkout?", "Tôi có thể xin trả phòng trễ được không?", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.TRAVEL_DAILY, topicId = "travel", isShortForDriving = true))
        add(DrillSentence("td_b1_02", "Where is the baggage claim area?", "Khu vực lấy hành lý ở đâu vậy?", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.TRAVEL_DAILY, topicId = "travel", isShortForDriving = true))
        add(DrillSentence("td_b1_03", "My connecting flight was severely delayed.", "Chuyến bay nối chuyến của tôi đã bị trễ nặng.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.TRAVEL_DAILY, topicId = "travel", isShortForDriving = true))
        add(DrillSentence("td_b1_04", "Is there an all-night pharmacy nearby?", "Gần đây có hiệu thuốc nào mở thâu đêm không?", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.TRAVEL_DAILY, topicId = "health", isShortForDriving = true))
        add(DrillSentence("td_int_01", "The air conditioner in my room broke.", "Máy điều hòa trong phòng tôi bị hỏng rồi.", DifficultyLevel.INTERMEDIATE, DrillCategory.TRAVEL_DAILY, topicId = "travel", isShortForDriving = true))
        add(DrillSentence("td_int_02", "I'd like to apply for tax refund.", "Tôi muốn làm thủ tục hoàn thuế mua sắm.", DifficultyLevel.INTERMEDIATE, DrillCategory.TRAVEL_DAILY, topicId = "shopping", isShortForDriving = true))
        add(DrillSentence("td_int_03", "Could you recommend authentic local delicacies?", "Bạn có thể gợi ý những món ngon địa phương chính gốc không?", DifficultyLevel.INTERMEDIATE, DrillCategory.TRAVEL_DAILY, topicId = "food", isShortForDriving = true))
        add(DrillSentence("td_b2_01", "I'm reporting a lost passport to embassy officials.", "Tôi đang trình báo việc mất hộ chiếu cho nhân viên đại sứ quán.", DifficultyLevel.UPPER_INTERMEDIATE, DrillCategory.TRAVEL_DAILY, topicId = "travel", isShortForDriving = true))
    }

    /** Returns all available curated drill sentences. */
    fun getAllSentences(): List<DrillSentence> = sentences

    /**
     * Filters curated sentences by difficulty level, category filter, topic, and driving safety constraint.
     */
    fun getSentences(
        category: DrillCategory = DrillCategory.ALL,
        level: DifficultyLevel? = null,
        topicId: String? = null,
        isShortOnly: Boolean = false
    ): List<DrillSentence> {
        return sentences.filter { item ->
            // Level compatibility: exact match or adjacent level within 1 tier (or all if level is null)
            val levelMatch = level == null || item.level == level || isCompatibleLevel(item.level, level)
            // Category match
            val categoryMatch = category == DrillCategory.ALL || item.category == category
            // Topic match if specified
            val topicMatch = topicId == null || item.topicId == null || item.topicId == topicId
            // Driving safety constraints
            val shortMatch = !isShortOnly || item.isShortForDriving
            levelMatch && categoryMatch && topicMatch && shortMatch
        }.ifEmpty {
            // Fallback: broaden level constraint
            sentences.filter { item ->
                (category == DrillCategory.ALL || item.category == category) &&
                    (!isShortOnly || item.isShortForDriving)
            }
        }.ifEmpty { sentences }
    }

    /**
     * Picks a curated sample of sentences formatted for Gemini Live system instruction.
     */
    fun getSampleSentencesForPrompt(
        category: DrillCategory = DrillCategory.ALL,
        level: DifficultyLevel = DifficultyLevel.INTERMEDIATE,
        topicId: String? = null,
        isCarConnected: Boolean = false,
        limit: Int = 8
    ): List<DrillSentence> {
        val candidates = getSentences(
            category = category,
            level = level,
            topicId = topicId,
            isShortOnly = isCarConnected
        )
        return candidates.shuffled().take(limit)
    }

    /**
     * Provides concise phonetic guidance based on category and level.
     */
    fun getCategoryGuidance(category: DrillCategory, level: DifficultyLevel = DifficultyLevel.INTERMEDIATE): String = when (category) {
        DrillCategory.VIETNAMESE_PITFALLS -> """
            PHONETIC FOCUS: VIETNAMESE LEARNER PITFALLS
            - Strictly target critical English sounds often dropped or mispronounced by Vietnamese speakers:
              1. Final consonant endings: /s/, /z/, /t/, /d/, /k/, and past tense -ed (/t/, /d/, /ɪd/).
              2. Difficult consonant pairs: /θ/ vs /t/ (think/tink), /ʃ/ vs /s/ (she/see), /v/ vs /w/ (very/wary).
              3. Connected speech & linking: natural transitions without chopping words (e.g. pick it up, check it out).
        """.trimIndent()
        DrillCategory.CONVERSATIONAL_REFLEX -> """
            PHONETIC & REFLEX FOCUS: CONVERSATIONAL NATIVE SPOKEN CHUNKS & IDIOMS
            - Practice high-frequency spoken formulas, natural sentence starters, and colloquial collocations.
            - Focus on natural intonation, rhythm, cadence, and reduction (e.g. "I was wondering if...", "To be completely honest...").
        """.trimIndent()
        DrillCategory.DRIVING_PHRASES -> """
            PHONETIC & COGNITIVE FOCUS: HANDS-FREE DRIVING SAFETY
            - Short, punchy driving phrases under 5 to 8 words maximum!
            - Decisive rhythm, clear road and directional context, effortless working-memory recall while driving.
        """.trimIndent()
        DrillCategory.BUSINESS_WORK -> """
            PHONETIC & PROFESSIONAL FOCUS: WORKPLACE & MEETINGS
            - High-impact business terminology, diplomatic disagreement, presentation phrasing, and tech collaboration.
        """.trimIndent()
        DrillCategory.TRAVEL_DAILY -> """
            PHONETIC & PRACTICAL FOCUS: TRAVEL, AIRPORTS & DAILY LIFE
            - Real-world situational phrases for dining, check-ins, transit, and resolving unexpected travel hassles.
        """.trimIndent()
        DrillCategory.ALL -> """
            PHONETIC & REFLEX FOCUS: COMPREHENSIVE DRILL
            - Balanced blend of phonetic accuracy (ending sounds, consonant pairs) and natural spoken conversational rhythm.
        """.trimIndent()
    }

    private fun isCompatibleLevel(itemLevel: DifficultyLevel, targetLevel: DifficultyLevel): Boolean {
        val diff = Math.abs(itemLevel.ordinal - targetLevel.ordinal)
        return diff <= 1
    }
}
