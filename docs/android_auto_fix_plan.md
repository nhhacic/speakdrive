# Kế hoạch khắc phục lỗi giao diện Android Auto và lỗi âm thanh AI bị ngắt quãng

## 1. Phân tích nguyên nhân gốc rễ (Root Cause Analysis)

### Vấn đề 1: Giao diện trên Android Auto không đúng như thiết kế
- **Nguyên nhân Artwork**: Trong `AutoCardArtworkGenerator.kt`, ảnh card kích thước 600x600 px đang vẽ khung nội dung chính (`heroBox` / `mainBox`) tràn từ `top = 78f` xuống tận `bottom = 526f`, cộng thêm status footer ở `y = 565f`. Trong khi đó, hệ thống Android Auto tự động bố trí các thành phần UI ở nửa dưới (từ `y ≈ 310f` đến `y = 600f`) gồm: Title, Subtitle và hàng phím điều khiển Play/Pause/Next. Do đó, các thành phần UI của Android Auto bị đè trực tiếp lên chữ của bức ảnh, gây rối mắt và che khuất nội dung.
- **Nguyên nhân Metadata**: Trong `MediaContentProvider.kt`, khi ở chế độ kể chuyện (`isStory`) hoặc AI nói (`hasAiText`), app đưa nguyên đoạn văn bản dài của AI (`lastAiText`) vào thuộc tính `title` của `MediaMetadata`. Khiến Android Auto hiển thị dòng tiêu đề bị cắt cụt luộm thuộm (`"📖 Chapter One: The Flight of No Return. Christmas Eve, 1971. T..."`) đè lên chính bức ảnh cũng đang chứa đoạn văn đó.

### Vấn đề 2: Tiếng AI nói bị ngắt liên tục, xe nhảy qua lại giữa chế độ phát nhạc và chế độ cuộc gọi
- **Nguyên nhân Audio Routing**: `AudioFocusHandler` xin focus dạng `USAGE_MEDIA`. Nhưng khi bắt đầu bài học, `LiveAudioIO.kt` luôn ép gọi `enterCommunicationRoute()` -> đặt `audioManager.mode = AudioManager.MODE_IN_COMMUNICATION` và kích hoạt thiết bị `AudioDeviceInfo.TYPE_BLUETOOTH_SCO`.
- **Hệ quả trên xe hơi**: 
  1. Khi Bluetooth SCO kích hoạt, hệ thống âm thanh ô tô (Head Unit) nhận định có "Cuộc gọi thoại rảnh tay" (Hands-free phone call) từ điện thoại, liền lập tức tắt kênh nhạc Media của Android Auto và chuyển sang màn hình/chế độ cuộc gọi.
  2. Android Auto nhận thấy xung đột và mất kênh Media, liền gửi tín hiệu `AUDIOFOCUS_LOSS_TRANSIENT` tới SpeakDrive.
  3. `ConversationEngine` nhận `AUDIOFOCUS_LOSS_TRANSIENT` -> lập tức gọi `pauseLocked()` -> bài học chuyển sang trạng thái `PAUSED` (hiển thị "Tạm dừng" và nút Play trên xe).
  4. Khi bị Pause -> AI ngưng phát -> Bluetooth SCO tắt -> Xe hơi thấy hết cuộc gọi, tự động chuyển về chế độ phát nhạc Media.
  5. Vừa chuyển về Media -> Android Auto cấp lại `AUDIOFOCUS_GAIN` -> `ConversationEngine` gọi `resumeLocked()` để tiếp tục.
  6. Tiếp tục học -> lại bật `MODE_IN_COMMUNICATION` và `BLUETOOTH_SCO` -> xe lại chuyển sang cuộc gọi -> lại bị mất focus -> lại Pause!
  -> Vòng lặp vô tận làm tiếng AI bị ngắt quãng liên tục và xe bị nhảy qua lại giữa phát nhạc và cuộc gọi.

---

## 2. Giải pháp triển khai chi tiết

### Giai đoạn 1: Sửa tầng Audio (`LiveAudioIO.kt` & `:audio`)
1. Bổ sung dependency `libs.car.app.library` vào `:audio/build.gradle.kts` để `LiveAudioIO` có thể kiểm tra trực tiếp trạng thái kết nối Android Auto (`CarConnection.CONNECTION_TYPE_PROJECTION`).
2. Trong `LiveAudioIO`:
   - Xác định trạng thái kết nối xe: `isCarConnected() = isCarProjected() || isCarAudioConnected()`.
   - Khi `isCarConnected()` là true:
     + Tuyệt đối **KHÔNG** gọi `enterCommunicationRoute()` và **KHÔNG** kích hoạt `AudioDeviceInfo.TYPE_BLUETOOTH_SCO`.
     + Luôn chuyển thẳng sang `enterMediaRoute()`: `audioManager.mode = AudioManager.MODE_NORMAL`.
     + `AudioTrack`: phát qua `AudioAttributes.USAGE_MEDIA` và `CONTENT_TYPE_SPEECH`. Âm thanh AI sẽ truyền qua kênh Media của Android Auto / A2DP với chất lượng âm thanh stereo cao cấp, không ngắt quãng và không kích hoạt chế độ cuộc gọi.
     + `AudioRecord`: thu âm qua `MediaRecorder.AudioSource.VOICE_RECOGNITION` hoặc `MIC`.
     + Khử vọng: Sử dụng `MicGate` (half-duplex) để ngắt mic khi AI nói và mở mic ngay khi AI dừng lời.
     + Nếu xe hơi kết nối giữa phiên học: tự động thoát `communicationRoute` và chuyển sang `enterMediaRoute()`.
3. Trong `MainActivity.kt`:
   - Đồng bộ `volumeControlStream`: nếu đang kết nối xe hơi hoặc không ở chế độ communication route, đặt stream là `AudioManager.STREAM_MUSIC`.

### Giai đoạn 2: Thiết kế lại giao diện Android Auto (`AutoCardArtworkGenerator.kt` & `MediaContentProvider.kt`)
1. Trong `AutoCardArtworkGenerator.kt`:
   - Thu gọn Hero Card vào **Nửa trên của ảnh** (từ `y = 16f` đến `y = 290f`):
     + **Chế độ Luyện phát âm / Nhắc lại (Repeat)**: Khung viền vàng rực rỡ bo góc (`COLOR_TARGET_BORDER`), nền `COLOR_TARGET_HERO_BG`. Câu mục tiêu hiển thị chữ to rõ, căn giữa, vừa vặn không bị tràn.
     + **Chế độ Kể chuyện (Story)**: Khung viền bo góc Sky (`COLOR_AI_LABEL`), tiêu đề nhỏ `📖 NỘI DUNG CÂU CHUYỆN:`, nội dung 4-6 dòng chữ trắng rõ ràng.
     + **Chế độ Trò chuyện tự do / Nhập vai**: Khung viền bo góc, tiêu đề nhỏ `🤖 GIA SƯ AI NÓI:`, nội dung lời thoại AI hiển thị nổi bật.
   - **Nửa dưới của ảnh** (từ `y = 300f` đến `y = 600f`): Để trống hoàn toàn với màu nền tối `COLOR_CARD_BG` (`#0B0F19`), không vẽ text footer hay box nào, dành trọn không gian cho Android Auto hiển thị Title, Subtitle và nút Play/Pause/Next.
2. Trong `MediaContentProvider.kt`:
   - Hàm `lessonItem()`:
     + Chế độ Story:
       * `title`: Tiêu đề câu chuyện ngắn gọn (ví dụ: `📖 ${lesson.scenario?.titleVi ?: lesson.titleVi}`).
       * `subtitle`: `${status} • Bấm Next để đổi truyện`.
     + Chế độ Repeat:
       * `title`: `🎯 Lặp lại theo AI`
       * `subtitle`: `${lesson.topic.emoji} ${lesson.titleVi} • Đang nghe bạn nói...`
     + Chế độ Trò chuyện tự do:
       * `title`: `💬 ${lesson.topic.emoji} ${lesson.titleVi}`
       * `subtitle`: `${status} • Nói tiếng Anh để trò chuyện`

### Giai đoạn 3: Kiểm thử, Build & Upload APK
1. Cập nhật và chạy Unit Tests:
   - `AutoCardArtworkGeneratorTest.kt`: Kiểm tra card render đúng kích thước, không lỗi.
   - `LiveAudioIOTest.kt`: Kiểm tra logic car connection routing và media route.
   - `MediaContentProviderTest.kt`: Kiểm tra title/subtitle mới cho lesson item.
2. Build APK: `./gradlew :app:assembleDebug`.
3. Tự động tải file APK lên GitHub Releases của repo `nhhacic/speakdrive` và cung cấp link direct download.
