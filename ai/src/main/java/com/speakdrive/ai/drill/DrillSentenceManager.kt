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
        add(DrillSentence("vp_a1_13", "He drank cold milk.", "Anh ấy uống sữa lạnh.", DifficultyLevel.BEGINNER, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Final /lk/ & /ld/", isShortForDriving = true))
        add(DrillSentence("vp_a1_14", "Take a deep breath.", "Hít một hơi thật sâu.", DifficultyLevel.BEGINNER, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Final /θ/ & /p/", isShortForDriving = true))
        add(DrillSentence("vp_a1_15", "Put on your warm coat.", "Mặc áo khoác ấm vào.", DifficultyLevel.BEGINNER, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Final /t/ & linking put-on", isShortForDriving = true))
        add(DrillSentence("vp_a1_16", "She walked in the park.", "Cô ấy đi dạo trong công viên.", DifficultyLevel.BEGINNER, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Past -ed /kt/ & /rk/", isShortForDriving = true))
        add(DrillSentence("vp_a1_17", "Give me five minutes.", "Cho tôi năm phút.", DifficultyLevel.BEGINNER, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Ending /v/ & /ts/", isShortForDriving = true))
        add(DrillSentence("vp_a1_18", "Close both of your eyes.", "Nhắm cả hai mắt lại.", DifficultyLevel.BEGINNER, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Ending /z/ & /ð/", isShortForDriving = true))

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
        add(DrillSentence("vp_a2_13", "He helped us with bags.", "Anh ấy giúp chúng tôi mang túi.", DifficultyLevel.ELEMENTARY, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Past /pt/ & plural /gz/", isShortForDriving = true))
        add(DrillSentence("vp_a2_14", "Check the exact birth date.", "Kiểm tra chính xác ngày sinh.", DifficultyLevel.ELEMENTARY, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Final /kt/, /θ/, /t/", isShortForDriving = true))
        add(DrillSentence("vp_a2_15", "Wash both white shirts today.", "Hôm nay hãy giặt cả hai chiếc áo sơ mi trắng.", DifficultyLevel.ELEMENTARY, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Final /ʃ/, /θ/, /ts/", isShortForDriving = true))
        add(DrillSentence("vp_a2_16", "They reached the next stop.", "Họ đã đến trạm dừng tiếp theo.", DifficultyLevel.ELEMENTARY, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Past /tʃt/ & /kst/", isShortForDriving = true))
        add(DrillSentence("vp_a2_17", "He fixed three broken watches.", "Anh ấy đã sửa ba chiếc đồng hồ bị hỏng.", DifficultyLevel.ELEMENTARY, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Past /kst/, /θ/, /ɪz/", isShortForDriving = true))
        add(DrillSentence("vp_a2_18", "The dog jumped very high.", "Chú chó nhảy lên rất cao.", DifficultyLevel.ELEMENTARY, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Past /mpt/ & /v/", isShortForDriving = true))

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
        add(DrillSentence("vp_b1_11", "We solved twelve distinct problems.", "Chúng tôi giải quyết mười hai vấn đề khác nhau.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Past /lvd/, /lv/, /kts/", isShortForDriving = true))
        add(DrillSentence("vp_b1_12", "She baked six fresh loaves of bread.", "Cô ấy nướng sáu ổ bánh mì tươi ngon.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Past /kt/, /ks/, /vz/", isShortForDriving = true))
        add(DrillSentence("vp_b1_13", "He gasped when he touched the cold glass.", "Anh ấy thở dốc khi chạm vào cốc thủy tinh lạnh.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Past /spt/, /tʃt/, /st/", isShortForDriving = true))
        add(DrillSentence("vp_b1_14", "Don't push yourself so hard today.", "Hôm nay đừng ép bản thân quá sức.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Contrast /ʃ/ vs /s/", isShortForDriving = true))
        add(DrillSentence("vp_b1_15", "She wants to live in peace.", "Cô ấy muốn sống trong yên bình.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Ending /nts/, /v/, /s/", isShortForDriving = true))

        // --- Intermediate (B1) ---
        add(DrillSentence("vp_int_01", "The company launched six new products.", "Công ty đã ra mắt sáu sản phẩm mới.", DifficultyLevel.INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Past /ntʃt/, /ks/, /kts/", isShortForDriving = true))
        add(DrillSentence("vp_int_02", "He finished his speech without hesitation.", "Anh ấy đã hoàn thành bài phát biểu mà không hề do dự.", DifficultyLevel.INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Past /ʃt/, /tʃ/, /ð/", isShortForDriving = true))
        add(DrillSentence("vp_int_03", "These statistics surprise all the specialists.", "Những số liệu thống kê này làm ngạc nhiên tất cả các chuyên gia.", DifficultyLevel.INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Complex /st/ and plural /sts/", isShortForDriving = true))
        add(DrillSentence("vp_int_04", "We must discuss these specific requests.", "Chúng ta phải thảo luận về các yêu cầu cụ thể này.", DifficultyLevel.INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Final /sts/, /fɪk/, /sts/", isShortForDriving = true))
        add(DrillSentence("vp_int_05", "As far as I can tell.", "Theo như tôi được biết.", DifficultyLevel.INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Connected speech reduction", isShortForDriving = true))
        add(DrillSentence("vp_int_06", "She thoroughly checked all the facts.", "Cô ấy đã kiểm tra kỹ lưỡng mọi sự thật.", DifficultyLevel.INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Voiceless /θ/, past /kt/, /kts/", isShortForDriving = true))
        add(DrillSentence("vp_int_07", "Turn off the engine and relax.", "Tắt động cơ xe và thư giãn đi.", DifficultyLevel.INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Linking turn-off-the", isShortForDriving = true))
        add(DrillSentence("vp_int_08", "The flight was delayed for hours.", "Chuyến bay đã bị hoãn nhiều giờ.", DifficultyLevel.INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Past /eɪd/, /rz/", isShortForDriving = true))
        add(DrillSentence("vp_int_09", "The software detected several critical risks.", "Phần mềm đã phát hiện một vài rủi ro nghiêm trọng.", DifficultyLevel.INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Past /ktɪd/, /sks/", isShortForDriving = true))
        add(DrillSentence("vp_int_10", "She described the project's broad scope.", "Cô ấy đã mô tả phạm vi rộng lớn của dự án.", DifficultyLevel.INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Past /bd/, /kts/, /sk/", isShortForDriving = true))
        add(DrillSentence("vp_int_11", "They grasped the basic concepts quickly.", "Họ đã nắm bắt các khái niệm cơ bản rất nhanh.", DifficultyLevel.INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Past /spt/, /pts/", isShortForDriving = true))
        add(DrillSentence("vp_int_12", "All candidates passed the difficult tests.", "Tất cả ứng viên đều đã vượt qua các bài kiểm tra khó.", DifficultyLevel.INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Past /st/, /sts/", isShortForDriving = true))
        add(DrillSentence("vp_int_13", "He stretched his arms and laughed.", "Anh ấy vươn vai và bật cười thoải mái.", DifficultyLevel.INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Past /tʃt/, /ft/", isShortForDriving = true))

        // --- Upper-Intermediate & Advanced (B2–C2) ---
        add(DrillSentence("vp_b2_01", "The scientists established strict testing standards.", "Các nhà khoa học đã thiết lập những tiêu chuẩn thử nghiệm nghiêm ngặt.", DifficultyLevel.UPPER_INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Plural /sts/, past /ʃt/, /kts/", isShortForDriving = true))
        add(DrillSentence("vp_b2_02", "He successfully managed multiple stressful risks.", "Anh ấy đã quản lý thành công nhiều rủi ro căng thẳng.", DifficultyLevel.UPPER_INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Adverb /li/, past /dʒd/, /sks/", isShortForDriving = true))
        add(DrillSentence("vp_b2_03", "Let's synthesize all three theoretical viewpoints.", "Hãy tổng hợp cả ba quan điểm lý thuyết.", DifficultyLevel.ADVANCED, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Repeated /θ/ in academic vocabulary", isShortForDriving = true))
        add(DrillSentence("vp_b2_04", "The legislation protects consumers against fraudulent schemes.", "Luật pháp bảo vệ người tiêu dùng chống lại các chiêu trò lừa đảo.", DifficultyLevel.ADVANCED, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Final /kts/, /mz/, /dʒənt/", isShortForDriving = true))
        add(DrillSentence("vp_b2_05", "The analyst glimpsed the hidden market trends.", "Nhà phân tích đã thoáng thấy các xu hướng thị trường tiềm ẩn.", DifficultyLevel.UPPER_INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Cluster /mpst/, /kts/", isShortForDriving = true))
        add(DrillSentence("vp_b2_06", "They accomplished significant scientific breakthroughs.", "Họ đã đạt được những đột phá khoa học đáng kể.", DifficultyLevel.ADVANCED, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Past /ʃt/, /kts/, /θ/", isShortForDriving = true))
        add(DrillSentence("vp_b2_07", "The committee approved six strategic investments.", "Ủy ban đã phê duyệt sáu khoản đầu tư chiến lược.", DifficultyLevel.UPPER_INTERMEDIATE, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Past /vd/, /ks/, /ts/", isShortForDriving = true))
        add(DrillSentence("vp_b2_08", "Technological disruptions affected global markets.", "Sự xáo trộn công nghệ đã tác động mạnh đến thị trường toàn cầu.", DifficultyLevel.ADVANCED, DrillCategory.VIETNAMESE_PITFALLS, phoneticFocus = "Past /tɪd/, /kts/", isShortForDriving = true))

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
        add(DrillSentence("cr_a1_09", "I am so happy today.", "Hôm nay tôi rất vui.", DifficultyLevel.BEGINNER, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_a1_10", "How is it going?", "Mọi chuyện thế nào rồi?", DifficultyLevel.BEGINNER, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_a1_11", "That sounds very good.", "Nghe có vẻ rất tốt.", DifficultyLevel.BEGINNER, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_a1_12", "I really appreciate it.", "Tôi thực sự rất trân trọng.", DifficultyLevel.BEGINNER, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_a1_13", "Never mind about that.", "Đừng bận tâm về điều đó.", DifficultyLevel.BEGINNER, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_a1_14", "Are you ready now?", "Bây giờ bạn đã sẵn sàng chưa?", DifficultyLevel.BEGINNER, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))

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
        add(DrillSentence("cr_a2_11", "Better safe than sorry.", "Cẩn tắc vô áy náy.", DifficultyLevel.ELEMENTARY, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_a2_12", "So far, so good.", "Đến nay mọi chuyện vẫn ổn.", DifficultyLevel.ELEMENTARY, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_a2_13", "I am on my way.", "Tôi đang trên đường tới.", DifficultyLevel.ELEMENTARY, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_a2_14", "Make yourself at home.", "Cứ tự nhiên như ở nhà nhé.", DifficultyLevel.ELEMENTARY, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_a2_15", "It is not a big deal.", "Không có gì to tát đâu.", DifficultyLevel.ELEMENTARY, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_a2_16", "I didn't mean to do that.", "Tôi không cố ý làm vậy đâu.", DifficultyLevel.ELEMENTARY, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))

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
        add(DrillSentence("cr_b1_11", "Off the top of my head.", "Theo những gì tôi nhớ ra ngay lúc này.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_b1_12", "Let's call it even.", "Thế là chúng ta hòa nhau nhé.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_b1_13", "I'm having second thoughts.", "Tôi đang đắn đo suy nghĩ lại.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_b1_14", "Don't jump the gun.", "Đừng vội vàng hấp tấp.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_b1_15", "Cut straight to the chase.", "Hãy đi thẳng vào trọng tâm vấn đề.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_b1_16", "I have no clue about that.", "Tôi hoàn toàn không biết gì về việc đó.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_b1_17", "It is easier said than done.", "Nói thì dễ hơn làm.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))

        // --- Intermediate (B1) ---
        add(DrillSentence("cr_int_01", "As far as I'm concerned, it's great.", "Theo góc nhìn của tôi thì điều đó rất tuyệt.", DifficultyLevel.INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_int_02", "I have mixed feelings about this.", "Tôi có cảm xúc lẫn lộn về việc này.", DifficultyLevel.INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_int_03", "Let's get straight to the point.", "Hãy đi thẳng vào trọng tâm vấn đề.", DifficultyLevel.INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_int_04", "It goes without saying, of course.", "Điều đó là hiển nhiên rồi, dĩ nhiên.", DifficultyLevel.INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_int_05", "I'm completely on board with that.", "Tôi hoàn toàn ủng hộ ý kiến đó.", DifficultyLevel.INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_int_06", "Could you elaborate a bit more?", "Bạn có thể nói rõ hơn một chút được không?", DifficultyLevel.INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_int_07", "Fair enough, let's move forward together.", "Hợp lý đấy, chúng ta cùng tiến hành thôi.", DifficultyLevel.INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_int_08", "It is definitely worth a shot.", "Điều đó rất đáng để thử một lần.", DifficultyLevel.INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_int_09", "Take it with a grain of salt.", "Đừng vội tin hoàn toàn vào điều đó.", DifficultyLevel.INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_int_10", "We are on the exact same page.", "Chúng ta hoàn toàn cùng chung quan điểm.", DifficultyLevel.INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_int_11", "That is water under the bridge.", "Chuyện cũ đã qua rồi, bỏ qua đi.", DifficultyLevel.INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_int_12", "Let's give them the benefit of doubt.", "Hãy cho họ cơ hội giải thích trước.", DifficultyLevel.INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_int_13", "You took the words right out of my mouth.", "Bạn nói đúng ngay ý nghĩ của tôi.", DifficultyLevel.INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))

        // --- Upper-Intermediate & Advanced (B2–C2) ---
        add(DrillSentence("cr_b2_01", "At the end of the day, results matter.", "Sau tất cả, kết quả cuối cùng mới là điều quan trọng.", DifficultyLevel.UPPER_INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_b2_02", "We should weigh the pros and cons.", "Chúng ta nên cân nhắc kỹ mặt lợi và hại.", DifficultyLevel.UPPER_INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_b2_03", "Let's not jump to premature conclusions.", "Chúng ta đừng vội đưa ra kết luận hấp tấp.", DifficultyLevel.ADVANCED, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_b2_04", "That speaks volumes about their integrity.", "Điều đó nói lên rất nhiều về sự chính trực của họ.", DifficultyLevel.ADVANCED, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_b2_05", "He hit the nail right on the head.", "Anh ấy đã nói trúng tim đen trọng tâm.", DifficultyLevel.UPPER_INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_b2_06", "Let's play devil's advocate for a moment.", "Hãy thử đứng ở góc nhìn phản biện một chút.", DifficultyLevel.ADVANCED, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_b2_07", "Actions always speak louder than words.", "Hành động luôn có sức nặng hơn lời nói.", DifficultyLevel.UPPER_INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))
        add(DrillSentence("cr_b2_08", "That proved to be a blessing in disguise.", "Hóa ra trong cái rủi lại có cái may.", DifficultyLevel.ADVANCED, DrillCategory.CONVERSATIONAL_REFLEX, isShortForDriving = true))

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
        add(DrillSentence("dr_a1_09", "Watch the speed limit.", "Chú ý giới hạn tốc độ.", DifficultyLevel.BEGINNER, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_a1_10", "Turn left at the corner.", "Rẽ trái ở góc đường.", DifficultyLevel.BEGINNER, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_a1_11", "Stop before the crosswalk.", "Dừng lại trước vạch sang đường.", DifficultyLevel.BEGINNER, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_a1_12", "Turn off your headlights.", "Tắt đèn pha xe của bạn đi.", DifficultyLevel.BEGINNER, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_a1_13", "The traffic light is green.", "Đèn giao thông đang xanh rồi.", DifficultyLevel.BEGINNER, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))

        // --- Elementary (A2) ---
        add(DrillSentence("dr_a2_01", "Take the second exit.", "Đi theo lối ra thứ hai.", DifficultyLevel.ELEMENTARY, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_a2_02", "Traffic is jammed ahead.", "Giao thông phía trước đang bị tắc nghẽn.", DifficultyLevel.ELEMENTARY, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_a2_03", "Watch out for pedestrians.", "Chú ý quan sát người đi bộ.", DifficultyLevel.ELEMENTARY, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_a2_04", "The gas tank is low.", "Bình xăng sắp cạn rồi.", DifficultyLevel.ELEMENTARY, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_a2_05", "Turn on your wipers.", "Bật cần gạt nước mưa lên.", DifficultyLevel.ELEMENTARY, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_a2_06", "Check your rear mirror.", "Kiểm tra gương chiếu hậu của bạn.", DifficultyLevel.ELEMENTARY, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_a2_07", "Keep both hands ready.", "Luôn giữ cả hai tay sẵn sàng.", DifficultyLevel.ELEMENTARY, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_a2_08", "Slow down on turns.", "Giảm tốc độ khi vào khúc cua.", DifficultyLevel.ELEMENTARY, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_a2_09", "Check the blind spot carefully.", "Kiểm tra điểm mù thật cẩn thận.", DifficultyLevel.ELEMENTARY, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_a2_10", "Switch on hazard warning lights.", "Bật đèn cảnh báo nguy hiểm lên.", DifficultyLevel.ELEMENTARY, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_a2_11", "Yield to pedestrians ahead.", "Nhường đường cho người đi bộ phía trước.", DifficultyLevel.ELEMENTARY, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_a2_12", "Keep a safe following distance.", "Giữ khoảng cách an toàn với xe trước.", DifficultyLevel.ELEMENTARY, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_a2_13", "Turn right at the roundabout.", "Rẽ phải ở vòng xuyến giao thông.", DifficultyLevel.ELEMENTARY, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))

        // --- Pre-Intermediate & Intermediate (B1) ---
        add(DrillSentence("dr_b1_01", "Take the next highway exit.", "Rẽ vào lối ra cao tốc tiếp theo.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_b1_02", "Could you navigate to downtown?", "Bạn có thể dẫn đường tới trung tâm không?", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_b1_03", "Merge safely into express lanes.", "Nhập làn an toàn vào làn đường cao tốc.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_b1_04", "Signal right before pulling over.", "Bật xi nhan phải trước khi tấp vào lề.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_b1_05", "The tire pressure light blinked.", "Đèn cảnh báo áp suất lốp vừa nhấp nháy.", DifficultyLevel.INTERMEDIATE, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_b1_06", "Heavy rain reduces road visibility.", "Mưa lớn làm giảm tầm nhìn trên đường.", DifficultyLevel.INTERMEDIATE, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_b1_07", "Avoid toll roads during rush hour.", "Tránh các tuyến đường có thu phí vào giờ cao điểm.", DifficultyLevel.INTERMEDIATE, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_b1_08", "Road construction caused sudden delays.", "Công trình đường bộ gây ra ùn tắc bất ngờ.", DifficultyLevel.INTERMEDIATE, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_b1_09", "Merge smoothly onto the freeway.", "Nhập làn êm ái vào đường cao tốc.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_b1_10", "Find the nearest EV charging station.", "Tìm trạm sạc xe điện gần nhất.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_b1_11", "Avoid hard braking on wet roads.", "Tránh phanh gấp trên đường trơn ướt.", DifficultyLevel.INTERMEDIATE, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_b1_12", "The GPS route has been recalculated.", "Tuyến đường định vị vừa được tính toán lại.", DifficultyLevel.INTERMEDIATE, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))
        add(DrillSentence("dr_b1_13", "Watch out for sudden construction detours.", "Chú ý các đoạn chuyển hướng thi công.", DifficultyLevel.INTERMEDIATE, DrillCategory.DRIVING_PHRASES, topicId = "driving_emergency", isShortForDriving = true))

        // =========================================================================
        // CATEGORY 4: BUSINESS & WORKPLACE (Meetings, Negotiations, Tech & Career)
        // =========================================================================

        // --- Elementary & Pre-Intermediate ---
        add(DrillSentence("bw_a2_01", "Let's schedule a meeting.", "Hãy lên lịch cho một cuộc họp.", DifficultyLevel.ELEMENTARY, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("bw_a2_02", "Could you send the report?", "Bạn có thể gửi bản báo cáo được không?", DifficultyLevel.ELEMENTARY, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("bw_a2_03", "We need to finish today.", "Chúng ta cần hoàn thành trong hôm nay.", DifficultyLevel.ELEMENTARY, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("bw_a2_04", "Let's wrap up this meeting.", "Chúng ta hãy kết thúc cuộc họp này thôi.", DifficultyLevel.ELEMENTARY, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("bw_a2_05", "Could you review my draft?", "Bạn có thể xem qua bản thảo của tôi không?", DifficultyLevel.ELEMENTARY, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("bw_a2_06", "We have a tight deadline.", "Chúng ta có thời hạn rất gấp.", DifficultyLevel.ELEMENTARY, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("bw_b1_01", "Let's align on our targets.", "Hãy thống nhất về các mục tiêu của chúng ta.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("bw_b1_02", "Who is heading this project?", "Ai đang phụ trách dự án này vậy?", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("bw_b1_03", "We hit our quarterly targets.", "Chúng ta đã đạt các mục tiêu của quý này.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("bw_b1_04", "Can we reschedule our call?", "Chúng ta có thể dời lại cuộc gọi được không?", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("bw_b1_05", "Can you send the agenda?", "Bạn có thể gửi lịch trình cuộc họp không?", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("bw_b1_06", "Let's brainstorm some ideas together.", "Hãy cùng nhau động não tìm ý tưởng nhé.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("bw_b1_07", "We need to prioritize tasks.", "Chúng ta cần sắp xếp mức độ ưu tiên công việc.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("bw_b1_08", "I will follow up by email.", "Tôi sẽ gửi email trao đổi tiếp.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))

        // --- Intermediate (B1) & Upper-Intermediate (B2) ---
        add(DrillSentence("bw_int_01", "Let's negotiate a win-win deal.", "Hãy đàm phán một thỏa thuận đôi bên cùng có lợi.", DifficultyLevel.INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("bw_int_02", "We must mitigate potential risks.", "Chúng ta phải giảm thiểu các rủi ro tiềm ẩn.", DifficultyLevel.INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("bw_int_03", "Our customer retention rate jumped.", "Tỷ lệ giữ chân khách hàng của chúng ta đã tăng vọt.", DifficultyLevel.INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("bw_int_04", "Let's circle back tomorrow morning.", "Hãy thảo luận lại điều này vào sáng mai.", DifficultyLevel.INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("bw_int_05", "Let's streamline our daily workflow.", "Hãy tinh gọn quy trình làm việc hàng ngày của chúng ta.", DifficultyLevel.INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("bw_int_06", "We need a feasible contingency plan.", "Chúng ta cần một kế hoạch dự phòng khả thi.", DifficultyLevel.INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("bw_int_07", "Can you walk us through the numbers?", "Bạn có thể giải thích chi tiết các con số được không?", DifficultyLevel.INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("bw_int_08", "Let's pivot our current marketing strategy.", "Hãy chuyển hướng chiến lược tiếp thị hiện tại của chúng ta.", DifficultyLevel.INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("bw_b2_01", "What is our competitive advantage?", "Lợi thế cạnh tranh của chúng ta ở đây là gì?", DifficultyLevel.UPPER_INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("bw_b2_02", "We are scaling the engineering team.", "Chúng tôi đang mở rộng quy mô đội ngũ kỹ sư.", DifficultyLevel.UPPER_INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("bw_b2_03", "Let's automate the deployment pipeline.", "Hãy tự động hóa quy trình triển khai phần mềm.", DifficultyLevel.UPPER_INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "tech_it", isShortForDriving = true))
        add(DrillSentence("bw_b2_04", "Security compliance remains our top priority.", "Tuân thủ an ninh bảo mật vẫn là ưu tiên hàng đầu của chúng tôi.", DifficultyLevel.ADVANCED, DrillCategory.BUSINESS_WORK, topicId = "tech_it", isShortForDriving = true))
        add(DrillSentence("bw_b2_05", "I'd like to touch on key deliverables.", "Tôi muốn điểm qua các sản phẩm bàn giao chính.", DifficultyLevel.UPPER_INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("bw_b2_06", "What is the projected return on investment?", "Tỷ suất sinh lời dự kiến từ khoản đầu tư là bao nhiêu?", DifficultyLevel.UPPER_INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("bw_b2_07", "We managed to stay ahead of schedule.", "Chúng tôi đã hoàn thành vượt tiến độ đề ra.", DifficultyLevel.UPPER_INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("bw_b2_08", "Cross-functional alignment is essential for success.", "Sự đồng thuận liên phòng ban là yếu tố quyết định thành công.", DifficultyLevel.ADVANCED, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))

        // =========================================================================
        // CATEGORY 5: TRAVEL & DAILY LIFE (Hotels, Airports, Dining, Emergencies)
        // =========================================================================

        // --- Beginner (A1) & Elementary (A2) ---
        add(DrillSentence("td_a1_01", "A table for two, please.", "Làm ơn cho một bàn hai người.", DifficultyLevel.BEGINNER, DrillCategory.TRAVEL_DAILY, topicId = "food", isShortForDriving = true))
        add(DrillSentence("td_a1_02", "Could we have the bill?", "Làm ơn cho chúng tôi xin hóa đơn.", DifficultyLevel.BEGINNER, DrillCategory.TRAVEL_DAILY, topicId = "food", isShortForDriving = true))
        add(DrillSentence("td_a1_03", "Where is the boarding gate?", "Cổng lên máy bay ở đâu vậy?", DifficultyLevel.BEGINNER, DrillCategory.TRAVEL_DAILY, topicId = "travel", isShortForDriving = true))
        add(DrillSentence("td_a1_04", "I have a hotel booking.", "Tôi có đặt phòng khách sạn trước.", DifficultyLevel.BEGINNER, DrillCategory.TRAVEL_DAILY, topicId = "travel", isShortForDriving = true))
        add(DrillSentence("td_a1_05", "Can I see the dessert menu?", "Cho tôi xem thực đơn món tráng miệng được không?", DifficultyLevel.BEGINNER, DrillCategory.TRAVEL_DAILY, topicId = "food", isShortForDriving = true))
        add(DrillSentence("td_a1_06", "Where is the subway entrance?", "Lối vào ga tàu điện ngầm ở đâu?", DifficultyLevel.BEGINNER, DrillCategory.TRAVEL_DAILY, topicId = "travel", isShortForDriving = true))
        add(DrillSentence("td_a2_01", "Can I pay by credit card?", "Tôi có thể thanh toán bằng thẻ tín dụng không?", DifficultyLevel.ELEMENTARY, DrillCategory.TRAVEL_DAILY, topicId = "shopping", isShortForDriving = true))
        add(DrillSentence("td_a2_02", "I would like a window seat.", "Tôi muốn một chỗ ngồi gần cửa sổ.", DifficultyLevel.ELEMENTARY, DrillCategory.TRAVEL_DAILY, topicId = "travel", isShortForDriving = true))
        add(DrillSentence("td_a2_03", "Do you have vegetarian food?", "Quán có món ăn chay không?", DifficultyLevel.ELEMENTARY, DrillCategory.TRAVEL_DAILY, topicId = "food", isShortForDriving = true))
        add(DrillSentence("td_a2_04", "I'd like to return this shirt.", "Tôi muốn đổi trả chiếc áo sơ mi này.", DifficultyLevel.ELEMENTARY, DrillCategory.TRAVEL_DAILY, topicId = "shopping", isShortForDriving = true))
        add(DrillSentence("td_a2_05", "Could you call a taxi, please?", "Làm ơn gọi giúp tôi một chiếc taxi.", DifficultyLevel.ELEMENTARY, DrillCategory.TRAVEL_DAILY, topicId = "travel", isShortForDriving = true))
        add(DrillSentence("td_a2_06", "Is there free Wi-Fi here?", "Ở đây có mạng Wi-Fi miễn phí không?", DifficultyLevel.ELEMENTARY, DrillCategory.TRAVEL_DAILY, topicId = "travel", isShortForDriving = true))
        add(DrillSentence("td_a2_07", "I need a local SIM card.", "Tôi cần mua một chiếc thẻ SIM địa phương.", DifficultyLevel.ELEMENTARY, DrillCategory.TRAVEL_DAILY, topicId = "shopping", isShortForDriving = true))

        // --- Pre-Intermediate (A2–B1) & Intermediate (B1) ---
        add(DrillSentence("td_b1_01", "Could I request a late checkout?", "Tôi có thể xin trả phòng trễ được không?", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.TRAVEL_DAILY, topicId = "travel", isShortForDriving = true))
        add(DrillSentence("td_b1_02", "Where is the baggage claim area?", "Khu vực lấy hành lý ở đâu vậy?", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.TRAVEL_DAILY, topicId = "travel", isShortForDriving = true))
        add(DrillSentence("td_b1_03", "My connecting flight was severely delayed.", "Chuyến bay nối chuyến của tôi đã bị trễ nặng.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.TRAVEL_DAILY, topicId = "travel", isShortForDriving = true))
        add(DrillSentence("td_b1_04", "Is there an all-night pharmacy nearby?", "Gần đây có hiệu thuốc nào mở thâu đêm không?", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.TRAVEL_DAILY, topicId = "health", isShortForDriving = true))
        add(DrillSentence("td_b1_05", "Is breakfast included in the room rate?", "Bữa sáng có bao gồm trong giá phòng không?", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.TRAVEL_DAILY, topicId = "travel", isShortForDriving = true))
        add(DrillSentence("td_b1_06", "Where can I catch the airport shuttle?", "Tôi có thể đón xe đưa đón sân bay ở đâu?", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.TRAVEL_DAILY, topicId = "travel", isShortForDriving = true))
        add(DrillSentence("td_b1_07", "Could we please split the bill?", "Chúng tôi có thể thanh toán chia tiền được không?", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.TRAVEL_DAILY, topicId = "food", isShortForDriving = true))
        add(DrillSentence("td_b1_08", "I have a severe peanut allergy.", "Tôi bị dị ứng đậu phộng rất nặng.", DifficultyLevel.PRE_INTERMEDIATE, DrillCategory.TRAVEL_DAILY, topicId = "food", isShortForDriving = true))
        add(DrillSentence("td_int_01", "The air conditioner in my room broke.", "Máy điều hòa trong phòng tôi bị hỏng rồi.", DifficultyLevel.INTERMEDIATE, DrillCategory.TRAVEL_DAILY, topicId = "travel", isShortForDriving = true))
        add(DrillSentence("td_int_02", "I'd like to apply for tax refund.", "Tôi muốn làm thủ tục hoàn thuế mua sắm.", DifficultyLevel.INTERMEDIATE, DrillCategory.TRAVEL_DAILY, topicId = "shopping", isShortForDriving = true))
        add(DrillSentence("td_int_03", "Could you recommend authentic local delicacies?", "Bạn có thể gợi ý những món ngon địa phương chính gốc không?", DifficultyLevel.INTERMEDIATE, DrillCategory.TRAVEL_DAILY, topicId = "food", isShortForDriving = true))
        add(DrillSentence("td_int_04", "Where is the lost and found office?", "Văn phòng tìm đồ thất lạc ở đâu vậy?", DifficultyLevel.INTERMEDIATE, DrillCategory.TRAVEL_DAILY, topicId = "travel", isShortForDriving = true))
        add(DrillSentence("td_int_05", "Can I store my luggage after checkout?", "Tôi có thể gửi hành lý lại sau khi trả phòng không?", DifficultyLevel.INTERMEDIATE, DrillCategory.TRAVEL_DAILY, topicId = "travel", isShortForDriving = true))
        add(DrillSentence("td_int_06", "Is this train bound for central station?", "Chuyến tàu này có đi về ga trung tâm không?", DifficultyLevel.INTERMEDIATE, DrillCategory.TRAVEL_DAILY, topicId = "travel", isShortForDriving = true))
        add(DrillSentence("td_b2_01", "I'm reporting a lost passport to embassy officials.", "Tôi đang trình báo việc mất hộ chiếu cho nhân viên đại sứ quán.", DifficultyLevel.UPPER_INTERMEDIATE, DrillCategory.TRAVEL_DAILY, topicId = "travel", isShortForDriving = true))
        add(DrillSentence("td_b2_02", "I need to file an insurance claim for luggage.", "Tôi cần nộp hồ sơ yêu cầu bảo hiểm cho hành lý.", DifficultyLevel.UPPER_INTERMEDIATE, DrillCategory.TRAVEL_DAILY, topicId = "travel", isShortForDriving = true))
        add(DrillSentence("td_b2_03", "Could you arrange a doctor visit to the hotel?", "Khách sạn có thể sắp xếp bác sĩ tới phòng khám được không?", DifficultyLevel.ADVANCED, DrillCategory.TRAVEL_DAILY, topicId = "health", isShortForDriving = true))

        // High-frequency situational and driving sentences
        add(DrillSentence("hf_01", "We need a better plan.", "Chúng ta cần một kế hoạch tốt hơn.", DifficultyLevel.INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("hf_02", "I'd like to check in, please.", "Làm ơn cho tôi làm thủ tục nhận phòng.", DifficultyLevel.BEGINNER, DrillCategory.TRAVEL_DAILY, topicId = "travel", isShortForDriving = true))
        add(DrillSentence("hf_03", "Can you show me the way?", "Bạn có thể chỉ đường giúp tôi được không?", DifficultyLevel.BEGINNER, DrillCategory.TRAVEL_DAILY, topicId = "directions", isShortForDriving = true))
        add(DrillSentence("hf_04", "Turn left at the next corner.", "Rẽ trái ở góc đường tiếp theo.", DifficultyLevel.ELEMENTARY, DrillCategory.DRIVING_PHRASES, topicId = "directions", isShortForDriving = true))
        add(DrillSentence("hf_05", "Turn right at the traffic lights.", "Rẽ phải ở chỗ đèn giao thông.", DifficultyLevel.ELEMENTARY, DrillCategory.DRIVING_PHRASES, topicId = "directions", isShortForDriving = true))
        add(DrillSentence("hf_06", "Where is the nearest gas station?", "Cây xăng gần nhất ở đâu vậy?", DifficultyLevel.ELEMENTARY, DrillCategory.DRIVING_PHRASES, topicId = "directions", isShortForDriving = true))
        add(DrillSentence("hf_07", "Where can I park my car?", "Tôi có thể đỗ xe ở đâu?", DifficultyLevel.BEGINNER, DrillCategory.DRIVING_PHRASES, topicId = "directions", isShortForDriving = true))
        add(DrillSentence("hf_08", "Is there any traffic ahead?", "Phía trước có bị kẹt xe không?", DifficultyLevel.ELEMENTARY, DrillCategory.DRIVING_PHRASES, topicId = "directions", isShortForDriving = true))
        add(DrillSentence("hf_09", "Please fasten your seatbelt.", "Xin vui lòng thắt dây an toàn.", DifficultyLevel.BEGINNER, DrillCategory.DRIVING_PHRASES, topicId = "directions", isShortForDriving = true))
        add(DrillSentence("hf_10", "Keep your eyes on the road.", "Hãy tập trung chú ý quan sát đường.", DifficultyLevel.ELEMENTARY, DrillCategory.DRIVING_PHRASES, topicId = "directions", isShortForDriving = true))
        add(DrillSentence("hf_11", "How much does this cost?", "Cái này giá bao nhiêu tiền?", DifficultyLevel.BEGINNER, DrillCategory.TRAVEL_DAILY, topicId = "shopping", isShortForDriving = true))
        add(DrillSentence("hf_12", "Could you speak a little slower?", "Bạn có thể nói chậm lại một chút được không?", DifficultyLevel.BEGINNER, DrillCategory.CONVERSATIONAL_REFLEX, topicId = "daily", isShortForDriving = true))
        add(DrillSentence("hf_13", "I have a meeting at nine.", "Tôi có một cuộc họp lúc chín giờ.", DifficultyLevel.ELEMENTARY, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("hf_14", "Let's take a short break.", "Chúng ta hãy nghỉ giải lao một lát.", DifficultyLevel.ELEMENTARY, DrillCategory.CONVERSATIONAL_REFLEX, topicId = "work", isShortForDriving = true))
        add(DrillSentence("hf_15", "Everything is under control.", "Mọi thứ đều đang trong tầm kiểm soát.", DifficultyLevel.INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, topicId = "work", isShortForDriving = true))
        add(DrillSentence("hf_16", "I need to make a phone call.", "Tôi cần gọi một cuộc điện thoại.", DifficultyLevel.BEGINNER, DrillCategory.CONVERSATIONAL_REFLEX, topicId = "daily", isShortForDriving = true))
        add(DrillSentence("hf_17", "Could I have a glass of water?", "Làm ơn cho tôi xin một ly nước.", DifficultyLevel.BEGINNER, DrillCategory.TRAVEL_DAILY, topicId = "food", isShortForDriving = true))
        add(DrillSentence("hf_18", "The weather is very nice today.", "Hôm nay thời tiết rất đẹp.", DifficultyLevel.BEGINNER, DrillCategory.CONVERSATIONAL_REFLEX, topicId = "daily", isShortForDriving = true))
        add(DrillSentence("hf_19", "See you again tomorrow.", "Hẹn gặp lại bạn vào ngày mai nhé.", DifficultyLevel.BEGINNER, DrillCategory.CONVERSATIONAL_REFLEX, topicId = "daily", isShortForDriving = true))
        add(DrillSentence("hf_20", "Have a safe trip.", "Chúc bạn có một chuyến đi an toàn.", DifficultyLevel.BEGINNER, DrillCategory.CONVERSATIONAL_REFLEX, topicId = "travel", isShortForDriving = true))
        add(DrillSentence("hf_21", "I completely agree with you.", "Tôi hoàn toàn đồng ý với ý kiến của bạn.", DifficultyLevel.INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, topicId = "work", isShortForDriving = true))
        add(DrillSentence("hf_22", "Let me check my schedule.", "Để tôi kiểm tra lại lịch làm việc của mình.", DifficultyLevel.ELEMENTARY, DrillCategory.BUSINESS_WORK, topicId = "work", isShortForDriving = true))
        add(DrillSentence("hf_23", "I'm running a little bit late.", "Tôi đang bị trễ một chút.", DifficultyLevel.ELEMENTARY, DrillCategory.CONVERSATIONAL_REFLEX, topicId = "daily", isShortForDriving = true))
        add(DrillSentence("hf_24", "Could you please give me a hand?", "Bạn có thể giúp tôi một tay được không?", DifficultyLevel.ELEMENTARY, DrillCategory.CONVERSATIONAL_REFLEX, topicId = "daily", isShortForDriving = true))
        add(DrillSentence("hf_25", "Nice talking to you today.", "Rất vui được trò chuyện cùng bạn hôm nay.", DifficultyLevel.BEGINNER, DrillCategory.CONVERSATIONAL_REFLEX, topicId = "daily", isShortForDriving = true))

        // --- Adventure & Survival Drills (Aron Ralston, Outdoor, Emergency, Resilience) ---
        add(DrillSentence("surv_01", "The climber survived against all odds.", "Người leo núi đã sống sót bất chấp mọi khó khăn.", DifficultyLevel.INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, topicId = "survival", isShortForDriving = true))
        add(DrillSentence("surv_02", "Against all odds, he found his way back.", "Vượt qua mọi nghịch cảnh, anh ấy đã tìm được đường trở về.", DifficultyLevel.INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, topicId = "survival", isShortForDriving = true))
        add(DrillSentence("surv_03", "He had to make a very tough decision.", "Anh ấy đã phải đưa ra một quyết định rất khó khăn.", DifficultyLevel.INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, topicId = "survival", isShortForDriving = true))
        add(DrillSentence("surv_04", "Never give up hope in extreme danger.", "Đừng bao giờ từ bỏ hy vọng trong hiểm nguy tột cùng.", DifficultyLevel.INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, topicId = "survival", isShortForDriving = true))
        add(DrillSentence("surv_05", "He was trapped in a remote canyon.", "Anh ấy bị mắc kẹt trong một hẻm núi hẻo lánh.", DifficultyLevel.INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, topicId = "survival", isShortForDriving = true))
        add(DrillSentence("surv_06", "Courage helped him overcome the ordeal.", "Lòng dũng cảm đã giúp anh ấy vượt qua nghịch cảnh.", DifficultyLevel.UPPER_INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, topicId = "survival", isShortForDriving = true))
        add(DrillSentence("surv_07", "He stayed calm and focused on survival.", "Anh ấy giữ bình tĩnh và tập trung vào việc sống sót.", DifficultyLevel.INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, topicId = "survival", isShortForDriving = true))
        add(DrillSentence("surv_08", "Human will power can perform miracles.", "Ý chí con người có thể tạo nên những điều kỳ diệu.", DifficultyLevel.UPPER_INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, topicId = "survival", isShortForDriving = true))
        add(DrillSentence("surv_09", "He managed to walk toward safety.", "Anh ấy đã gắng gượng bước đi về nơi an toàn.", DifficultyLevel.ELEMENTARY, DrillCategory.CONVERSATIONAL_REFLEX, topicId = "survival", isShortForDriving = true))
        add(DrillSentence("surv_10", "He drank water from melted snow.", "Anh ấy uống nước từ tuyết tan.", DifficultyLevel.ELEMENTARY, DrillCategory.CONVERSATIONAL_REFLEX, topicId = "survival", isShortForDriving = true))
        add(DrillSentence("surv_11", "Every second counts in an emergency.", "Từng giây phút đều quý giá trong tình huống khẩn cấp.", DifficultyLevel.INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, topicId = "survival", isShortForDriving = true))
        add(DrillSentence("surv_12", "He kept his spirits high despite the pain.", "Anh ấy vẫn giữ vững tinh thần bất chấp nỗi đau.", DifficultyLevel.UPPER_INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, topicId = "survival", isShortForDriving = true))
        add(DrillSentence("surv_13", "They signaled for rescue at sunrise.", "Họ phát tín hiệu cầu cứu vào lúc bình minh.", DifficultyLevel.ELEMENTARY, DrillCategory.CONVERSATIONAL_REFLEX, topicId = "survival", isShortForDriving = true))
        add(DrillSentence("surv_14", "Staying hydrated is crucial for survival.", "Uống đủ nước là điều tối quan trọng để sinh tồn.", DifficultyLevel.INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, topicId = "survival", isShortForDriving = true))
        add(DrillSentence("surv_15", "Trust your instincts when danger strikes.", "Hãy tin vào trực giác khi hiểm nguy ập tới.", DifficultyLevel.UPPER_INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, topicId = "survival", isShortForDriving = true))
        add(DrillSentence("surv_16", "He cut off his arm to save his life.", "Anh ấy đã tự cắt cánh tay để bảo toàn mạng sống.", DifficultyLevel.INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, topicId = "survival", isShortForDriving = true))
        add(DrillSentence("surv_17", "The rescue helicopter arrived just in time.", "Trực thăng cứu hộ đã đến kịp thời.", DifficultyLevel.ELEMENTARY, DrillCategory.CONVERSATIONAL_REFLEX, topicId = "survival", isShortForDriving = true))
        add(DrillSentence("surv_18", "He never lost his will to live.", "Anh ấy không bao giờ đánh mất ý chí sống.", DifficultyLevel.INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, topicId = "survival", isShortForDriving = true))
        add(DrillSentence("surv_19", "His story inspired millions of people.", "Câu chuyện của anh ấy đã truyền cảm hứng cho hàng triệu người.", DifficultyLevel.INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, topicId = "survival", isShortForDriving = true))
        add(DrillSentence("surv_20", "Survival is about mental toughness.", "Sinh tồn đòi hỏi sự kiên cường về tinh thần.", DifficultyLevel.UPPER_INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, topicId = "survival", isShortForDriving = true))

        // --- High-Demand New Topics Seed Drills (Short, punchy for driving) ---
        // Personal Finance & Wealth
        add(DrillSentence("fin_01", "Diversify your investment portfolio.", "Hãy đa dạng hóa danh mục đầu tư của bạn.", DifficultyLevel.INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "personal_finance", isShortForDriving = true))
        add(DrillSentence("fin_02", "Check current mortgage interest rates.", "Kiểm tra mức lãi suất vay mua nhà hiện tại.", DifficultyLevel.INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "personal_finance", isShortForDriving = true))
        add(DrillSentence("fin_03", "Protect your card against fraud.", "Bảo vệ thẻ của bạn trước gian lận.", DifficultyLevel.ELEMENTARY, DrillCategory.BUSINESS_WORK, topicId = "personal_finance", isShortForDriving = true))
        add(DrillSentence("fin_04", "Inflation erodes cash purchasing power.", "Lạm phát làm xói mòn sức mua của tiền mặt.", DifficultyLevel.UPPER_INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "personal_finance", isShortForDriving = true))

        // Parenting & International Schooling
        add(DrillSentence("par_01", "Children learn best through play.", "Trẻ em học tốt nhất thông qua vui chơi.", DifficultyLevel.BEGINNER, DrillCategory.TRAVEL_DAILY, topicId = "parenting_education", isShortForDriving = true))
        add(DrillSentence("par_02", "We set clear bedtime routines.", "Chúng tôi thiết lập giờ đi ngủ rõ ràng.", DifficultyLevel.ELEMENTARY, DrillCategory.TRAVEL_DAILY, topicId = "parenting_education", isShortForDriving = true))
        add(DrillSentence("par_03", "Encourage your child's natural curiosity.", "Hãy khuyến khích sự tò mò tự nhiên của trẻ.", DifficultyLevel.INTERMEDIATE, DrillCategory.TRAVEL_DAILY, topicId = "parenting_education", isShortForDriving = true))
        add(DrillSentence("par_04", "Limit daily screen time sensibly.", "Giới hạn thời gian xem màn hình một cách hợp lý.", DifficultyLevel.INTERMEDIATE, DrillCategory.TRAVEL_DAILY, topicId = "parenting_education", isShortForDriving = true))

        // Real Estate & Relocation
        add(DrillSentence("re_01", "Is the security deposit refundable?", "Tiền đặt cọc có được hoàn lại không?", DifficultyLevel.ELEMENTARY, DrillCategory.TRAVEL_DAILY, topicId = "real_estate_relocation", isShortForDriving = true))
        add(DrillSentence("re_02", "We signed a one-year lease.", "Chúng tôi đã ký hợp đồng thuê một năm.", DifficultyLevel.ELEMENTARY, DrillCategory.TRAVEL_DAILY, topicId = "real_estate_relocation", isShortForDriving = true))
        add(DrillSentence("re_03", "The kitchen sink is leaking.", "Bồn rửa nhà bếp đang bị rò rỉ nước.", DifficultyLevel.BEGINNER, DrillCategory.TRAVEL_DAILY, topicId = "real_estate_relocation", isShortForDriving = true))
        add(DrillSentence("re_04", "Utilities are included in rent.", "Hóa đơn điện nước đã bao gồm trong giá thuê.", DifficultyLevel.INTERMEDIATE, DrillCategory.TRAVEL_DAILY, topicId = "real_estate_relocation", isShortForDriving = true))

        // E-Commerce & Global Trade
        add(DrillSentence("ecom_01", "Can you lower the MOQ?", "Bạn có thể giảm số lượng đặt hàng tối thiểu không?", DifficultyLevel.ELEMENTARY, DrillCategory.BUSINESS_WORK, topicId = "ecommerce_global_trade", isShortForDriving = true))
        add(DrillSentence("ecom_02", "Track the shipping container status.", "Theo dõi trạng thái lô hàng container.", DifficultyLevel.INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "ecommerce_global_trade", isShortForDriving = true))
        add(DrillSentence("ecom_03", "Maintain a five-star seller rating.", "Duy trì đánh giá người bán năm sao.", DifficultyLevel.INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "ecommerce_global_trade", isShortForDriving = true))
        add(DrillSentence("ecom_04", "Customs cleared the priority shipment.", "Hải quan đã thông quan lô hàng ưu tiên.", DifficultyLevel.UPPER_INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "ecommerce_global_trade", isShortForDriving = true))

        // Mindfulness & Self-Growth
        add(DrillSentence("mind_01", "Small habits compound over time.", "Những thói quen nhỏ sẽ tích lũy theo thời gian.", DifficultyLevel.ELEMENTARY, DrillCategory.CONVERSATIONAL_REFLEX, topicId = "mindfulness_self_growth", isShortForDriving = true))
        add(DrillSentence("mind_02", "Take a deep breath now.", "Hãy hít một hơi thật sâu ngay lúc này.", DifficultyLevel.BEGINNER, DrillCategory.CONVERSATIONAL_REFLEX, topicId = "mindfulness_self_growth", isShortForDriving = true))
        add(DrillSentence("mind_03", "Set healthy boundaries without guilt.", "Thiết lập ranh giới lành mạnh mà không áy náy.", DifficultyLevel.INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, topicId = "mindfulness_self_growth", isShortForDriving = true))
        add(DrillSentence("mind_04", "Protect your evening mental energy.", "Bảo vệ năng lượng tinh thần vào buổi tối của bạn.", DifficultyLevel.INTERMEDIATE, DrillCategory.CONVERSATIONAL_REFLEX, topicId = "mindfulness_self_growth", isShortForDriving = true))

        // Sports, Fitness & Active Lifestyle
        add(DrillSentence("spo_01", "Stay behind the kitchen line.", "Đứng sau vạch nhà bếp (kitchen line).", DifficultyLevel.ELEMENTARY, DrillCategory.TRAVEL_DAILY, topicId = "sports_fitness", isShortForDriving = true))
        add(DrillSentence("spo_02", "Maintain a steady running pace.", "Duy trì tốc độ chạy ổn định.", DifficultyLevel.BEGINNER, DrillCategory.TRAVEL_DAILY, topicId = "sports_fitness", isShortForDriving = true))
        add(DrillSentence("spo_03", "Warm up before heavy lifting.", "Khởi động kỹ trước khi nâng tạ nặng.", DifficultyLevel.BEGINNER, DrillCategory.TRAVEL_DAILY, topicId = "sports_fitness", isShortForDriving = true))
        add(DrillSentence("spo_04", "Great teamwork on the court!", "Phối hợp đồng đội tuyệt vời trên sân đấu!", DifficultyLevel.ELEMENTARY, DrillCategory.TRAVEL_DAILY, topicId = "sports_fitness", isShortForDriving = true))

        // Public Speaking & Debates
        add(DrillSentence("spk_01", "Hook the audience right away.", "Thu hút khán giả ngay từ giây đầu tiên.", DifficultyLevel.INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "public_speaking_debate", isShortForDriving = true))
        add(DrillSentence("spk_02", "Deliver your message with clarity.", "Truyền tải thông điệp của bạn với sự rõ ràng.", DifficultyLevel.INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "public_speaking_debate", isShortForDriving = true))
        add(DrillSentence("spk_03", "Pause for emphasis before concluding.", "Tạm dừng để nhấn mạnh trước khi kết luận.", DifficultyLevel.UPPER_INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "public_speaking_debate", isShortForDriving = true))
        add(DrillSentence("spk_04", "Support your claim with evidence.", "Hãy củng cố luận điểm của bạn bằng bằng chứng.", DifficultyLevel.INTERMEDIATE, DrillCategory.BUSINESS_WORK, topicId = "public_speaking_debate", isShortForDriving = true))
    }

    /** Returns all available curated drill sentences. */
    fun getAllSentences(): List<DrillSentence> = sentences

    /**
     * Filters curated sentences by difficulty level, category filter, topic, driving safety constraint,
     * and excludes recently practiced sentence texts to ensure endless diversity.
     */
    fun getSentences(
        category: DrillCategory = DrillCategory.ALL,
        level: DifficultyLevel? = null,
        topicId: String? = null,
        isShortOnly: Boolean = false,
        excludeTexts: Set<String> = emptySet()
    ): List<DrillSentence> {
        val matches = sentences.filter { item ->
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

        if (excludeTexts.isNotEmpty()) {
            val fresh = matches.filter { it.text !in excludeTexts }
            if (fresh.isNotEmpty()) return fresh
        }
        return matches
    }

    /**
     * Picks a curated sample of sentences formatted for Gemini Live system instruction.
     * Intelligently avoids sentences the user recently practiced.
     */
    fun getSampleSentencesForPrompt(
        category: DrillCategory = DrillCategory.ALL,
        level: DifficultyLevel = DifficultyLevel.INTERMEDIATE,
        topicId: String? = null,
        isCarConnected: Boolean = false,
        excludeTexts: Set<String> = emptySet(),
        limit: Int = 10
    ): List<DrillSentence> {
        val candidates = getSentences(
            category = category,
            level = level,
            topicId = topicId,
            isShortOnly = isCarConnected,
            excludeTexts = excludeTexts
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

    private val translationLookup by lazy {
        sentences.associate { com.speakdrive.ai.pronunciation.PronunciationDrill.key(it.text) to it.translationVi }
    }

    /**
     * Looks up curated Vietnamese translation for an English practice sentence.
     * Normalized against punctuation and casing.
     */
    fun findTranslationVi(text: String): String? {
        val key = com.speakdrive.ai.pronunciation.PronunciationDrill.key(text)
        return translationLookup[key]
    }
}
