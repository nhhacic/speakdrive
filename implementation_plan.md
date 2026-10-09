# Kế hoạch triển khai: Khắc phục lỗi hiển thị chữ mờ và bị cắt chữ trên Android Auto

## 1. Phân tích nguyên nhân gốc rễ (Root Cause Analysis)

Dựa vào hình ảnh thực tế trên Android Auto (Desktop Head Unit) do người dùng cung cấp:
- Màn hình đang ở chế độ chia đôi Dashboard (Coolwalk split-screen) gồm bản đồ điều hướng bên trái và widget Media Player của SpeakDrive bên phải.
- Hiện tượng: Chữ trong widget bên phải hiện ra là `pronunciation. Repeat after me Let's`, chữ có màu xám tối mờ ảo, câu bị cắt cụt và bị dòng tiêu đề cùng các nút điều khiển đè lên.

### Nguyên nhân 1: Chữ khá mờ do cơ chế Dark Scrim của Android Auto
- Android Auto Media Player lấy ảnh Cover Art (`MediaMetadata.artworkData`) làm ảnh nền cho widget Media.
- Để đảm bảo các nút điều khiển (Play/Pause/Next) và tiêu đề của hệ thống nổi rõ, **Android Auto tự động phủ một lớp làm tối mờ (Dark Scrim / Gradient mờ đen 50%–70%)** lên toàn bộ bức ảnh bìa.
- Trong `AutoCardArtworkGenerator.kt`, ảnh card hiện tại vẽ nền đen sẫm `#0B0F19`, hộp thẻ `#0B132B`, chữ trắng với bóng đen dày `0xB3000000`. Khi đi qua lớp scrim làm tối của xe, màu sắc bị chìm hoàn toàn vào màu đen, chữ trắng bị biến thành màu xám tối mờ nhạt.
- Trong khi đó, **dòng Title và Subtitle nguyên bản của Android Auto** ở góc dưới (`🎯 Lặp lại theo AI`, `💬 Luyện phát âm: Giao tiếp hàng ...`) là chữ vector hệ thống nên hiển thị **sáng trắng 100%, sắc nét tuyệt đối, không hề bị mờ**. Tuy nhiên, app lại đang gán cứng `Title = "🎯 Lặp lại theo AI"` thay vì hiển thị câu tiếng Anh cần luyện!

### Nguyên nhân 2: Màn hình không hiện đủ chữ / chữ bị che khuất
- Trong `AutoCardArtworkGenerator.kt`, khung thẻ Hero Box đang vẽ từ `top = 20f` xuống tận `bottom = 576f` (toàn bộ chiều cao 600px).
- Nửa dưới của màn hình (từ `y ≈ 295f` đến `600f`) là nơi Android Auto hiển thị Title, Subtitle và hàng phím điều khiển Play/Pause/Next.
- Do đó, khi văn bản được căn giữa dọc theo chiều cao 576px, nửa dưới của câu luyện tập và toàn bộ câu dịch tiếng Việt bị các nút bấm của xe đè lên hoàn toàn, người lái không thể đọc được.
- Đồng thời font size 54f / 48f quá lớn khiến mỗi dòng chỉ chứa được 2–3 từ, câu 7 từ đã chiếm 3–4 dòng và tràn xuống khu vực bị che.

### Nguyên nhân 3: Lỗi trích xuất câu luyện tập trong `PronunciationDrill.kt`
- Khi gia sư AI bắt đầu buổi học bằng câu: *"Welcome to SpeakDrive! Today let's practice pronunciation. Repeat after me: Let's start with..."*:
  - Cụm từ `"let's practice"` khớp với `PREFIX_KEYWORD_REGEX`.
  - Trong quá trình streaming, khi từ phía sau `"Repeat after me:"` chỉ mới có 1 từ `"Let's"`, bộ phân tích bỏ qua vì chưa đủ 2 từ.
  - Vòng lặp quét ngược lùi về tiền tố trước đó là `"let's practice"` và lấy đoạn văn bản *"pronunciation. Repeat after me: Let's"* (5 từ) làm câu mục tiêu!
  - Dẫn đến trên màn hình hiển thị nguyên văn 3 dòng: `"pronunciation."`, `"Repeat after me"`, `"Let's"`.

---

## 2. Giải pháp kỹ thuật toàn diện

### 2.1. Đưa Câu luyện tập vào Native Title & Subtitle của Android Auto (`MediaContentProvider.kt`)
Tận dụng hệ thống hiển thị văn bản sắc nét 100% của Android Auto:
- **Khi ở chế độ Luyện phát âm / Nhắc lại (`hasTarget = true`)**:
  - `title`: Hiển thị chính câu tiếng Anh cần đọc (`drillTarget`).
    *(Ví dụ: `"Where is the nearest train station?"`)*
  - `subtitle`: Hiển thị bản dịch tiếng Việt nếu có (`"🇻🇳 $drillTargetTranslation"`), hoặc `"🎯 Lặp lại theo AI • ${lesson.topic.titleVi}"`.
  - `artist`: `"🎯 Luyện phát âm • ${lesson.level.displayName}"`.
- **Lợi ích**: Câu luyện tập và nghĩa tiếng Việt sẽ hiển thị trực tiếp trên dòng chữ to, rõ ràng, sáng trắng 100% của xe hơi, hoàn toàn không bị lớp phủ làm mờ của xe ảnh hưởng.

### 2.2. Thiết kế lại Card Artwork chống mờ & an toàn (`AutoCardArtworkGenerator.kt`)
1. **Giới hạn tuyệt đối trong Vùng an toàn nửa trên (Top Safe Zone)**:
   - Thẻ nội dung chỉ nằm từ `y = 14f` đến `y = 295f` (chiều cao ~281px).
   - Nửa dưới từ `y = 295f` đến `600f` giữ nền tối trơn sạch sẽ `#0B0F19`, không vẽ bất kỳ chữ hay viền nào để không xung đột với các nút bấm của xe.
2. **Tăng cường độ sáng & tương phản chống mờ (High Luminance Contrast)**:
   - Nền thẻ dùng màu xanh navy sâu có độ sáng cao hơn (`#132347`), viền dày `3.5f` màu xanh Sky rực rỡ (`#38BDF8`).
   - Text tiếng Anh: Màu trắng tinh `#FFFFFF`, bật `isFakeBoldText = true`, bóng nét nhẹ chống nhòe.
   - Text tiếng Việt: Màu vàng rực rỡ `#FDE047` (Yellow 300), tương phản mạnh mẽ xuyên qua lớp scrim tối của Android Auto.
3. **Adaptive Font Sizing thông minh cho Top Safe Zone**:
   - Khi không có bản dịch: Dải font `34f, 30f, 26f, 22f, 19f`, `maxLines = 4`, căn giữa dọc hoàn hảo trong 220px an toàn.
   - Khi có cả câu tiếng Anh và bản dịch: Dải cặp font `(25f, 18f), (22f, 16f), (19f, 14.5f)`, `spacing = 8f`. Cả 2 câu nằm gọn gàng 100% không bao giờ bị cắt.
4. **Áp dụng tương tự cho chế độ Story và Free Talk**:
   - `mainBoxTop = 14f`, `mainBoxBottom = 295f`.
   - Chữ hiển thị tối đa 5 dòng, font `21f -> 16f`.

### 2.3. Sửa triệt để lỗi trích xuất câu mục tiêu (`PronunciationDrill.kt`)
1. Trong `cleanTargetSentence(raw)`:
   - Nếu đoạn văn bản trích xuất vẫn còn chứa bất kỳ tiền tố drill nào (như `repeat after me`, `next sentence`...), từ chối ngay (`return null`) vì đây là câu mở đầu dính tiền tố lồng nhau.
2. Tinh chỉnh `PREFIX_KEYWORD_REGEX`:
   - Chỉ khớp `let's practice` khi đi kèm chỉ định câu/từ (ví dụ `let's practice saying:`, `let's practice this sentence:`), không khớp các câu trò chuyện chung chung như `let's practice pronunciation`.

---

## 3. Kế hoạch kiểm thử & Phát hành

1. **Unit Tests**:
   - Cập nhật và bổ sung test cases trong `AutoCardArtworkGeneratorTest.kt`, `MediaContentProviderTest.kt`, `PronunciationDrillTest.kt`.
   - Chạy toàn bộ test `:auto:testDebugUnitTest` và `:ai:testDebugUnitTest`.
2. **Tăng số phiên bản**:
   - Cập nhật `version.properties` lên `versionCode=43`, `versionName=1.4.3`.
3. **Build & Release APK**:
   - Chạy build `./gradlew :app:assembleDebug`.
   - Upload file `app-debug.apk` lên GitHub Releases repo `nhhacic/speakdrive` với tag `v1.4.3`.
   - Cung cấp link direct download cho người dùng cài đặt ngay.
