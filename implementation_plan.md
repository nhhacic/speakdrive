# Kế hoạch triển khai: Hoàn thiện 3 tính năng nâng cao (Android Auto IELTS & Custom Scenarios, Fluency Trends, Learner Facts Management)

## 1. Mục tiêu
Nâng cấp và tinh chỉnh trải nghiệm người dùng toàn diện trên cả điện thoại và Android Auto:
1. **Đồng bộ Android Auto cho IELTS & Custom Scenarios**:
   - Thêm nút vào thẳng chế độ *"🎯 Luyện thi IELTS Speaking"* trên màn hình Android Auto.
   - Thêm danh mục *"⭐ Tình huống tự tạo của bạn"* trong tab Nhập vai (Roleplay) trên xe để chọn rảnh tay các kịch bản đời thực đã lưu.
   - Bổ sung nhận diện từ khóa giọng nói Assistant cho IELTS trong `VoiceCommandHandler`.
2. **Biểu đồ xu hướng trôi chảy tuần (Weekly Fluency Trends) trên màn hình Tiến độ (`ProgressScreen`)**:
   - Tổng hợp các chỉ số trôi chảy thực tế (WPM, Từ đệm, Độ dài câu MLU) của tuần hiện tại và so sánh với tuần trước.
   - Hiển thị trực quan qua thẻ `WeeklyFluencyTrendsCard` trên màn hình Tiến độ.
3. **Thêm và quản lý bộ nhớ cá nhân người học (`Learner Facts Management`)**:
   - Cho phép người học tự nhập trực tiếp các thông tin/sở thích/công việc mà mình muốn AI ghi nhớ ngay trên `LearnerMemoryScreen`.
   - Kết nối lưu trực tiếp vào cơ sở dữ liệu để AI nạp ngay vào prompt các buổi học tiếp theo.

---

## 2. Kế hoạch thay đổi chi tiết

### Hạng mục 1: Android Auto IELTS & Custom Scenarios
- **`auto/src/main/java/com/speakdrive/auto/MediaIds.kt`**:
  - Khai báo hằng số `IELTS = "ielts"`, `CUSTOM_SCENARIOS = "custom_scenarios"`.
  - Khai báo `MediaTarget.Ielts`.
  - Xử lý parse `mediaId == IELTS` và `mediaId == CUSTOM_SCENARIOS`.
- **`auto/src/main/java/com/speakdrive/auto/MediaLessonResolver.kt`**:
  - Ánh xạ `MediaTarget.Ielts` sang `LessonRequest(mode = SessionMode.IELTS_SPEAKING, topicId = settings.snapshot().lastTopicId)`.
- **`auto/src/main/java/com/speakdrive/auto/MediaContentProvider.kt`**:
  - Trong `homeItems()`: Thêm mục `playable(MediaIds.IELTS, ...)`.
  - Trong `browseChildren`:
    - Ở tab `ROLEPLAY`: Lấy `sessionStore.customScenarios()`, nếu có kịch bản thì hiển thị danh mục `browsable(MediaIds.CUSTOM_SCENARIOS, "⭐ Tình huống tự tạo của bạn", ...)`.
    - Khi duyệt `CUSTOM_SCENARIOS`: Trả về danh sách kịch bản tự tạo dưới dạng `playable(MediaIds.scenario(it.id), it.titleVi, ...)`.
  - Trong `item(mediaId)`: Hỗ trợ `MediaTarget.Ielts`.
- **`auto/src/main/java/com/speakdrive/auto/VoiceCommandHandler.kt`**:
  - Khai báo `IELTS_WORDS = listOf("ielts", "thi ielts", "luyen thi ielts", "ielts speaking", "practice ielts", "test ielts")`.
  - Nhận diện `if (q.hasAny(IELTS_WORDS)) return MediaIds.IELTS`.
- **Kiểm thử**: Cập nhật `VoiceCommandHandlerTest.kt` và `MediaContentProviderTest.kt`.

### Hạng mục 2: Xu hướng trôi chảy tuần trên màn hình Tiến độ
- **`app/src/main/java/com/speakdrive/ui/screens/ProgressViewModel.kt`**:
  - Tạo model `WeeklyFluencySummary(currentWeekWpm: Int?, previousWeekWpm: Int?, currentWeekFillerRatio: Float?, currentWeekMlu: Float?, hasData: Boolean)`.
  - Bổ sung `weeklyFluency: WeeklyFluencySummary` vào `ProgressUiState`.
  - Tính toán số liệu dựa trên các buổi học hoàn thành trong 7 ngày gần nhất so với 7 ngày trước đó.
- **`app/src/main/java/com/speakdrive/ui/screens/ProgressScreen.kt`**:
  - Thiết kế `WeeklyFluencyTrendsCard`: hiển thị Tốc độ nói WPM (kèm chỉ báo tăng/giảm so với tuần trước), Tỉ lệ từ đệm (filler words %), Độ dài câu trung bình (MLU).
  - Tích hợp thẻ vào `ProgressContent` ngay dưới phần biểu đồ 7 ngày.
- **Chuỗi đa ngôn ngữ (`strings.xml` en & vi)**: Bổ sung các chuỗi mô tả chỉ số trôi chảy tuần.

### Hạng mục 3: Quản lý và thêm thông tin cá nhân (`Learner Facts`)
- **`app/src/main/java/com/speakdrive/data/repository/SessionRepository.kt`**:
  - Bổ sung hàm `addLearnerFact(fact: String)`.
- **`app/src/main/java/com/speakdrive/ui/screens/LearnerMemoryViewModel.kt`**:
  - Thêm hàm `fun addFact(fact: String)`.
- **`app/src/main/java/com/speakdrive/ui/screens/LearnerMemoryScreen.kt`**:
  - Thêm nút `+ Thêm thông tin` ở tiêu đề mục Facts.
  - Hiển thị `AlertDialog` nhập văn bản cho phép người dùng gõ thông tin và bấm "Lưu".
- **Chuỗi đa ngôn ngữ (`strings.xml` en & vi)**: Bổ sung chuỗi cho dialog thêm fact.

---

## 3. Quy trình thực hiện & Tiêu chuẩn phát hành
1. Thực hiện các thay đổi mã nguồn theo kế hoạch.
2. Chạy toàn bộ Unit Tests (`:auto:testDebugUnitTest`, `:ai:testDebugUnitTest`, `:app:testDebugUnitTest`).
3. Tăng phiên bản trong `version.properties` lên `versionCode=45`, `versionName=1.4.5`.
4. Biên dịch APK `:app:assembleDebug`.
5. Tạo Git commit và push lên nhánh `main`.
6. Tạo GitHub Release tag `v1.4.5-debug` và upload `app-debug.apk`.
7. Gửi link tải trực tiếp cho người dùng.
