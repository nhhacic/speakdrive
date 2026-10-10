# Kế hoạch triển khai: Tự động đồng bộ cấp độ (Difficulty Level) từ Cài đặt vào phiên học đang diễn ra

## Mục tiêu
Đảm bảo khi người dùng thay đổi cấp độ học tập (`DifficultyLevel`) trong màn hình Cài đặt (UI Settings) hoặc qua repository cài đặt trong lúc đang có một phiên học (Active Session) diễn ra ngầm, phiên học đó sẽ:
1. Cập nhật ngay lập tức `_lesson.value.level` sang cấp độ mới.
2. Gửi chỉ thị trực tiếp đến Gemini Live (`liveClient.sendText(...)`) để AI lập tức điều chỉnh từ vựng, ngữ pháp, độ dài câu và nhịp độ kể chuyện/hội thoại cho các lượt tiếp theo.
3. Đảm bảo phiên kể chuyện (Story Listening) tự động áp dụng cấp độ của người học khi tạo bài học hoặc đề xuất câu chuyện ban đầu (`storyRecommender.recommendStory`).
4. Bổ sung Unit Test xác thực việc thay đổi cài đặt cấp độ ngoài phiên học sẽ lập tức cập nhật trạng thái bài học và gửi chỉ thị đến `liveClient`.

## Các bước thực hiện

### 1. Bổ sung `difficultyLevelSwitchMessage` trong `PromptTemplates.kt`
- Tạo hàm `PromptTemplates.difficultyLevelSwitchMessage(level: DifficultyLevel, mode: SessionMode)` để chuẩn hóa thông điệp gửi tới Gemini Live khi cấp độ thay đổi.
- Bổ sung `PromptTemplates.vietnameseHelpSwitchMessage(enabled: Boolean)` để đồng bộ trợ giúp tiếng Việt nếu được bật/tắt trong cài đặt.

### 2. Cập nhật `ConversationEngine.kt`
- Trong `ConversationEngine.init`:
  - Theo dõi `previousLevel = learnerSettings.level` và `previousVietnameseHelp = learnerSettings.allowVietnameseHelp`.
  - Khi `updated.level != previousLevel && _state.value == ConversationState.ACTIVE`:
    - Cập nhật `_lesson.value = currentLesson.copy(level = updated.level)`.
    - Gửi thông điệp `PromptTemplates.difficultyLevelSwitchMessage(updated.level, currentLesson.mode)` tới `liveClient`.
  - Khi `updated.allowVietnameseHelp != previousVietnameseHelp && _state.value == ConversationState.ACTIVE`:
    - Gửi thông điệp `PromptTemplates.vietnameseHelpSwitchMessage(updated.allowVietnameseHelp)` tới `liveClient`.
- Trong `resolveLesson`:
  - Truyền `learnerLevel = request.level ?: prefs.level` khi gọi `storyRecommender.recommendStory(recent)` để kịch bản truyện ban đầu được tính điểm ưu tiên và thích ứng đúng cấp độ người học.

### 3. Viết Unit Test
- Bổ sung unit test trong `PromptTemplatesTest.kt` kiểm tra `difficultyLevelSwitchMessage`.
- Bổ sung unit test trong `ConversationEngineTest.kt` kiểm tra việc thay đổi cấp độ trong `settings` (qua Flow của fake repository) khi session đang `ACTIVE` sẽ cập nhật `engine.lesson.value?.level` và gửi thông điệp tới `liveClient`.

### 4. Quy trình bàn giao APK & Release (theo AGENTS.md)
- Tăng `versionCode` (68 -> 69) và `versionName` (1.7.4 -> 1.7.5) trong `version.properties`.
- Chạy unit tests để đảm bảo toàn bộ tests pass.
- Build file APK debug `:app:assembleDebug`.
- Tự động cài đặt APK lên điện thoại qua ADB (`192.168.1.46:5555`).
- Tạo GitHub Release và tải `app-debug.apk` lên repo `nhhacic/speakdrive`.
- Gửi link direct download cho người dùng.
