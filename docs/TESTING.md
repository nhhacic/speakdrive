# Kiểm thử SpeakDrive

## 1. Test tự động (chạy trên máy tính, không cần điện thoại)

```bash
./gradlew testDebugUnitTest lintDebug
```

| Module | Nội dung được test |
|---|---|
| `:ai` | `ConversationEngine` (bắt đầu/tạm dừng/tiếp tục/kết thúc, cuộc gọi đến, giọng chỉ đường, mất mạng, kết nối lại khi hết ~10 phút, im lặng lâu, "end the lesson" bằng giọng nói, tóm tắt lỗi), prompt, parser tóm tắt JSON, gộp transcript, tìm chủ đề |
| `:auto` | Cây menu Android Auto, nhận diện lệnh giọng nói ("easy travel English", "phỏng vấn"…), media id |
| `:app` | Room (lưu nháp → hoàn tất, từ vựng không trùng, lịch ôn tập, thống kê, xoá dữ liệu), streak, các màn hình Compose, **khởi động app thật** (Hilt + Room + Firebase) |

Báo cáo HTML: `*/build/reports/tests/testDebugUnitTest/index.html`.

Phần **không** test tự động được, vì cần mạng, micro thật và Firebase thật: chất lượng hội thoại, độ trễ,
khử tiếng vọng trong xe. Những phần này kiểm tra theo kịch bản bên dưới.

## 2. Thử trên điện thoại

Điều kiện: đã làm xong [FIREBASE_SETUP.md](FIREBASE_SETUP.md).

```bash
./gradlew installDebug
```

| # | Thao tác | Kết quả mong đợi |
|---|---|---|
| P1 | Mở app | Trang chủ hiện 8 chủ đề, streak bằng 0 |
| P2 | Chọn "Du lịch" → "Trò chuyện tự do" | App xin quyền micro; sau khi cấp, AI chào bằng giọng nói trong vài giây |
| P3 | Trả lời bằng tiếng Anh, cố ý nói sai ngữ pháp | AI sửa lỗi một cách tự nhiên, transcript hiện trên màn hình |
| P4 | Để AI nói hết câu bằng loa ngoài | AI **không** tự ngắt lời và không lặp lại câu chào: micro tạm tắt khi AI nói. Nếu đeo tai nghe, bật *Cài đặt → Cho phép ngắt lời AI* rồi thử nói chen ngang |
| P5 | Khoá màn hình, tiếp tục nói chuyện 1 phút | Hội thoại vẫn chạy, có thông báo media trên màn hình khoá |
| P6 | Gọi điện vào máy | Bài học tự tạm dừng, gác máy thì tự tiếp tục |
| P7 | Bật chế độ máy bay 10 giây rồi tắt | Nghe thông báo "Connection lost…", sau đó bài học tự tiếp tục |
| P8 | Im lặng khoảng 30 giây | AI hỏi nhẹ để kéo bạn vào lại; sau 3 lần không trả lời thì tự tạm dừng |
| P9 | Nói "end the lesson" | AI chào tạm biệt, mở màn hình kết quả, khoảng 5–15 giây sau có điểm và từ mới |
| P10 | Học liên tục trên 10 phút | Không bị ngắt (app tự kết nối lại và nhớ nội dung đã nói) |
| P11 | Trang chủ → Ôn tập từ vựng (sau 1 ngày) | AI hỏi lại các từ đã học |

## 3. Thử Android Auto bằng Desktop Head Unit (DHU)

1. Android Studio → **SDK Manager → SDK Tools** → tick **Android Auto Desktop Head Unit Emulator**.
2. Trên điện thoại: cài app **Android Auto**, mở Settings của Android Auto, chạm 10 lần vào *Version*
   để bật Developer mode, rồi chọn menu ⋮ → **Start head unit server**.
3. Cắm USB, sau đó chạy:

```bash
adb forward tcp:5277 tcp:5277
"%LOCALAPPDATA%\Android\Sdk\extras\google\auto\desktop-head-unit.exe"
```

4. Lần đầu: Android Auto chỉ hiện app cài từ Play Store. Vào Settings của Android Auto →
   **Unknown sources** → bật lên.

| # | Thao tác trên DHU | Kết quả mong đợi |
|---|---|---|
| A1 | Mở launcher của xe | SpeakDrive có trong danh sách app media |
| A2 | Mở SpeakDrive | 4 tab: Bắt đầu, Chủ đề, Nhập vai, Độ khó |
| A3 | Chủ đề → Mua sắm | AI bắt đầu nói; màn hình *Now playing* hiện "🛒 Mua sắm • Đang trò chuyện" |
| A4 | Bấm Pause / Play | Bài học dừng rồi tiếp tục |
| A5 | Bấm Next | Đổi sang chủ đề khác; bài cũ được lưu |
| A6 | Độ khó → Advanced | Bài mới bắt đầu ở mức Advanced, Cài đặt trên điện thoại cũng đổi theo |
| A7 | Nút micro trên DHU → "play easy job interview practice on SpeakDrive" | Bắt đầu chủ đề Phỏng vấn ở mức Beginner |
| A8 | Khoá điện thoại trong khi đang học | **Micro vẫn nhận giọng nói** (xem phần rủi ro bên dưới) |
| A9 | Bấm Stop | Bài học kết thúc; mở app trên điện thoại sẽ thấy kết quả trong Tiến trình |
| A10 | Bật *Tự động tiếp tục phát nội dung đa phương tiện* trong cài đặt Android Auto, học một bài, ngắt kết nối rồi cắm lại | SpeakDrive tự mở và tiếp tục chủ đề gần nhất, không phải Spotify |

### ⚠️ Rủi ro cần kiểm tra kỹ: micro khi điện thoại bị khoá

Từ Android 14, app chỉ được bật foreground service loại **microphone** khi người dùng vừa tương tác với app.
Media3 khởi động service với loại `mediaPlayback`; `SpeakDriveMediaService` sau đó thêm loại `microphone`.

- Nếu bắt đầu bài học từ **điện thoại** (app đang mở) thì luôn chạy được.
- Nếu bắt đầu **chỉ từ màn hình xe** khi điện thoại đang khoá, Android có thể từ chối
  (Logcat: `Could not add the microphone foreground-service type`). Khi đó micro thu toàn im lặng.

Hãy chạy kịch bản A8 trên máy thật Android 14 trở lên. Nếu bị chặn, cách xử lý đơn giản nhất là hiển thị
hướng dẫn "Mở SpeakDrive trên điện thoại một lần trước khi lái". Vào thời điểm viết, Google không có
API chính thức nào khác cho media app cần dùng micro.

## 4. Đo độ trễ (mục tiêu < 2 giây, theo kế hoạch)

Bật Logcat với tag `GeminiLiveManager` và `ConversationEngine`, nói một câu ngắn rồi đo bằng đồng hồ:
khoảng thời gian từ lúc bạn ngừng nói đến lúc nghe tiếng AI. Kết quả phụ thuộc mạng 4G/5G và model đã chọn.
