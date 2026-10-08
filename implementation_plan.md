# Kế hoạch triển khai: Nút Next và Repeat cho Android Auto & Phím điều khiển vô lăng

## 1. Yêu cầu & Mục tiêu
- **Yêu cầu 1**: Ở chế độ Android Auto, bổ sung nút **Next** (chuyển sang câu tiếp theo) và nút **Repeat** (lặp lại câu vừa rồi).
- **Yêu cầu 2**: Hai nút này tương ứng hoàn toàn với các nút điều khiển media trên vô lăng ô tô (**Next Track** và **Previous Track** / Rewind).
- **Nguyên tắc**: Voice-First & Driving Safety — Cho phép người lái điều khiển linh hoạt qua màn hình xe, phím vô lăng và giọng nói rảnh tay.

---

## 2. Thiết kế kỹ thuật chi tiết

### 2.1. Phím vô lăng & Transport Controls tiêu chuẩn (`SpeakDrivePlayer.kt`)
1. **Available Commands trong Player**:
   - Bổ sung `COMMAND_SEEK_TO_PREVIOUS` và `COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM` bên cạnh `COMMAND_SEEK_TO_NEXT` và `COMMAND_SEEK_TO_NEXT_MEDIA_ITEM`.
   - Giúp Media3 tự động export hai cờ hành động chuẩn `PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS` và `PlaybackStateCompat.ACTION_SKIP_TO_NEXT` tới hệ thống Android Auto.
2. **Xử lý `handleSeek(...)`**:
   - Khi nhận lệnh **Next** (`COMMAND_SEEK_TO_NEXT` / `COMMAND_SEEK_TO_NEXT_MEDIA_ITEM`):
     + Nếu đang trong bài học (`isInLesson`):
       * Chế độ `STORY_LISTENING`: gọi `engine.nextStory()` để đổi câu chuyện kế tiếp.
       * Chế độ `REPEAT_AFTER_ME`: gọi `engine.next()` để bỏ qua câu drill hiện tại và chuyển sang câu luyện phát âm tiếp theo.
       * Chế độ khác (`FREE_TALK`, `ROLEPLAY`...): gọi `engine.next()` nhắc AI tóm lược ý và chuyển sang câu hỏi/chủ đề tiếp theo.
     + Nếu ngoài bài học: chuyển sang chủ đề ngẫu nhiên mới.
   - Khi nhận lệnh **Previous / Repeat** (`COMMAND_SEEK_TO_PREVIOUS` / `COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM`):
     + Nếu đang trong bài học (`isInLesson`):
       * Chế độ `REPEAT_AFTER_ME`: gọi `engine.repeat()`, AI phát âm lại câu mẫu rõ ràng với thông báo `"Đọc lại câu"`.
       * Chế độ `STORY_LISTENING`: gọi `engine.repeat()`, phát lại câu chuyện từ đầu (`replayStory()`).
       * Chế độ khác (`FREE_TALK`, `ROLEPLAY`...): gọi `engine.repeat()`, nhắc AI đọc lại câu vừa nói rõ ràng.

### 2.2. Custom Command Buttons trên màn hình xe (`SpeakDriveMediaService.kt`)
1. Đăng ký hai Custom Command trong `MediaLibrarySession`:
   - `CUSTOM_ACTION_REPEAT = "com.speakdrive.auto.ACTION_REPEAT"` với icon `ic_repeat` và tên hiển thị `"Lặp lại câu"`.
   - `CUSTOM_ACTION_NEXT = "com.speakdrive.auto.ACTION_NEXT"` với icon `ic_skip_next` và tên hiển thị `"Câu tiếp theo"`.
2. Thiết lập `onConnect`:
   - Cung cấp `customLayout` chứa 2 nút này cho các Media Controller được xác thực (Android Auto, Assistant).
3. Xử lý `onCustomCommand`:
   - Nhận lệnh `CUSTOM_ACTION_REPEAT` -> gọi `engine.repeat()`.
   - Nhận lệnh `CUSTOM_ACTION_NEXT` -> gọi `engine.next()`.

### 2.3. Tài nguyên đồ họa & Đa ngôn ngữ
- Vector drawable: `auto/src/main/res/drawable/ic_repeat.xml` và `ic_skip_next.xml`.
- Chuỗi ngôn ngữ: `values/strings.xml` (Tiếng Việt) và `values-en/strings.xml` (Tiếng Anh).

### 2.4. Điều khiển bằng giọng nói dự phòng
- Bộ phân tích transcript `VoiceCommandParser` đã hỗ trợ đầy đủ các cách nói:
  + Chuyển câu: `"next"`, `"next sentence"`, `"câu tiếp theo"`, `"chuyển câu"`, `"bỏ qua"`, `"skip"`, v.v.
  + Lặp lại: `"repeat"`, `"again"`, `"say again"`, `"đọc lại"`, `"nhắc lại"`, `"lặp lại"`, v.v.

---

## 3. Kiểm thử & Đánh giá (Test Suite)
- Toàn bộ unit tests của module `:auto` và `:ai` vượt qua 100%:
  - `available commands include seek to previous and seek to next` -> PASSED
  - `steering wheel next during free talk advances to next sentence` -> PASSED
  - `steering wheel next during a pronunciation drill skips the sentence instead of leaving the drill` -> PASSED
  - `steering wheel next during a story moves to the next story` -> PASSED
  - `steering wheel previous during repeat drill repeats the current sentence` -> PASSED
  - `steering wheel previous during free talk repeats what AI just said` -> PASSED
  - `steering wheel previous during story listening calls engine repeat` -> PASSED
  - `steering wheel previous when not in lesson does not call repeat` -> PASSED
  - `steering wheel next when not in lesson starts a new random topic` -> PASSED
