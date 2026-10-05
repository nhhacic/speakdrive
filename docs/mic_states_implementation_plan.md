# Kế hoạch triển khai: Nâng cấp nút Mic thể hiện đa trạng thái hội thoại

## 1. Mục tiêu
Nâng cấp nút Mic (`VoiceMicButton`) và thanh trạng thái (`ConversationScreen`) để trực quan hóa sinh động và phân biệt rõ ràng các trạng thái của hệ thống hội thoại:
- **AI đang nói (`AI_SPEAKING`)**: Hiển thị sóng âm sống động với hào quang xoay đa sắc tím/cyan đặc trưng của Gemini Live.
- **AI đang đợi học viên nói (`LISTENING`)**: Hiển thị biểu tượng Microphone với hiệu ứng "thở" (breathing pulse) xanh dương dịu nhẹ, mời gọi học viên nói tự nhiên.
- **Học viên đang nói (`USER_SPEAKING`)**: Hiển thị biểu tượng Microphone với sóng phản hồi xanh ngọc (Emerald) theo nhịp nói của học viên.
- **AI đang suy nghĩ (`AI_THINKING`)**: Hiển thị biểu tượng ngôi sao ma thuật trí tuệ nhân tạo (`AutoAwesome`) với vòng quét tư duy xoay tròn (orbital shimmer) màu tím huyền ảo, báo hiệu AI đang phân tích và chuẩn bị phản hồi.
- **Tạm dừng hội thoại (`PAUSED`)**: Hiển thị nút Play màu hổ phách/cam ấm, báo hiệu trạng thái nghỉ và mời chạm để tiếp tục.
- **Đang kết nối / bận (`BUSY`)**: Hiển thị biểu tượng xoay đồng bộ màu xám slate.

---

## 2. Chi tiết các tệp thay đổi

### A. Chuỗi đa ngôn ngữ (`strings.xml`)
- `app/src/main/res/values/strings.xml`: Thêm chuỗi trợ năng và thông báo trạng thái cho `AI_THINKING`, `USER_SPEAKING`, `LISTENING`.
- `app/src/main/res/values-vi/strings.xml`: Thêm bản dịch tiếng Việt tương ứng.
- Các file ngôn ngữ khác (`values-de`, `values-es`, `values-fr`, `values-ja`, `values-ko`, `values-zh`): Bổ sung chuỗi tiếng Anh mặc định để tránh lỗi biên dịch resource.

### B. Thành phần nút Mic (`VoiceMicButton.kt`)
- Mở rộng enum `MicState`: Thêm `USER_SPEAKING`, `AI_THINKING`.
- Thêm hiệu ứng animation phong phú:
  - `thinkingRotation`: Tốc độ quay chuyên biệt cho trạng thái AI suy nghĩ.
  - `thinkingPulse`: Hiệu ứng nhấp nháy cho biểu tượng AI tư duy.
  - `haloColor`, `coreBrush`, `icon`, `label` phù hợp cho từng trạng thái mới.
  - Vòng hào quang quỹ đạo cho `AI_THINKING`.

### C. Quản lý trạng thái Engine (`ConversationEngine.kt`)
- Thêm flow `isAiThinking: StateFlow<Boolean>`.
- Kích hoạt `_isAiThinking = true`:
  - Sau khi gửi tin nhắn mở đầu (kickoff) hoặc chuyển câu hỏi.
  - Khi học viên dứt lời (sau khoảng thời gian im lặng kết thúc lượt nói của người dùng) trước khi AI phản hồi.
  - Khi gửi câu lệnh phụ hoặc nhắc nhở tự động.
- Chuyển `_isAiThinking = false`:
  - Khi AI bắt đầu phát âm thanh/transcript (`Speaker.AI`).
  - Khi học viên nói ngắt lời (`LiveEvent.Interrupted` hoặc `Speaker.USER`).
  - Khi tạm dừng (`pauseLocked`) hoặc kết thúc buổi học (`endLocked`).
  - Có timeout bảo vệ tự động xóa trạng thái suy nghĩ sau 10 giây nếu server không phản hồi.

### D. Tầng ViewModel & Screen UI (`ConversationViewModel.kt` & `ConversationScreen.kt`)
- `ConversationViewModel.kt`:
  - Đưa `engine.isAiThinking` vào kết hợp `ConversationUiState`.
  - Cập nhật hàm `micStateFor(state, speaker, isThinking)`.
- `ConversationScreen.kt`:
  - Cập nhật hàm `rememberStatusText` để hiển thị trạng thái văn bản "Alex đang suy nghĩ..." tương ứng trên status pill.

### E. Kiểm thử tự động (Unit Tests)
- Bổ sung test kiểm tra chuyển đổi trạng thái trong `ConversationEngineTest.kt` và ViewModel/UI test nếu cần.
- Chạy toàn bộ test suite để đảm bảo không có regression.

---

## 3. Kế hoạch xác minh (Verification Plan)
1. **Kiểm tra biên dịch & Unit Test**: Chạy `./gradlew testDebugUnitTest` cho toàn bộ dự án.
2. **Kiểm tra trực quan trạng thái**: Đảm bảo tất cả các trạng thái trong `MicState` ánh xạ chính xác và mượt mà trong Jetpack Compose.
