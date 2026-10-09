# Kế hoạch: Bố trí lại màn hình chính (Home) trên điện thoại

Ngày khảo sát: 2026-10-09, bản v1.5.12 (Build 62), trên Galaxy Z Fold7 (màn ngoài 1080×2520, 420dpi ≈ 411dp ngang, **cỡ chữ hệ thống 150%**).

## 1. Hiện trạng (đo trên máy thật)

Màn Home hiện là một `LazyColumn` dài 8 khối ([HomeScreen.kt](../app/src/main/java/com/speakdrive/ui/screens/HomeScreen.kt)), cuộn gần **5 màn hình** mới hết:

1. Thanh trên: logo + "SpeakDrive / Voice-First English" + chip xe + icon Tiến trình + icon Cài đặt
2. Lời chào "Chào buổi tối 🌙" + "Sẵn sàng luyện nói khi lái xe"
3. Thẻ Hero (tiếp tục bài / bắt đầu)
4. Bento 2 cột: Streak + mục tiêu ngày | Hàng đợi ôn tập
5. "Chế độ học nhanh": 3 thẻ cuộn ngang (Luyện nghe, Luyện phát âm, Ngẫu nhiên)
6. "Chủ đề luyện tập" + chip lọc
7. Lưới 2 cột 15 chủ đề (mỗi thẻ ≥144dp)
8. Kịch bản tùy chỉnh (nếu có) + mẹo lái xe

### Vấn đề cụ thể

| # | Vấn đề | Thấy ở đâu |
|---|--------|-----------|
| 1 | Đầu trang tốn ~20% màn hình đầu cho logo, tagline, lời chào 2 dòng | Màn 1 |
| 2 | Icon Tiến trình và Cài đặt trên thanh trên **trùng** với 2 tab ở thanh điều hướng dưới | Màn 1 |
| 3 | Hero lặp cấp độ 2 lần ("A2–B1 • Tiền trung cấp" và dòng "Tiền trung cấp") | Màn 1 |
| 4 | Gợi ý "Nói 'Bắt đầu học'…" **không đúng**: Home không hề nghe giọng nói, không có nút mic | Màn 1 |
| 5 | Bento 2 cột vỡ ở cỡ chữ 150%: "Hàng đợi ôn tập" xuống 2 dòng, "Ôn tập từ vự…" bị cắt **mất luôn số từ đến hạn**, "108/30 phút (100%)" xuống dòng | Màn 1 |
| 6 | Thẻ "Chế độ học nhanh" rộng cố định 136dp: badge tiếng Anh bị gãy ("Shadow/ing", "Surp/e"), tên bị cắt ("Luyện ng…", "Luyện ph…"), thẻ thứ 3 bị che | Màn 1–2 |
| 7 | Một việc xuất hiện nhiều chỗ: Ngẫu nhiên (Hero + Chế độ nhanh), Phát âm (Bento + Chế độ nhanh + sheet chủ đề) | Màn 1–2 |
| 8 | Lưới chủ đề dài ~3 màn hình; tên dài bị cắt ("Công nghệ Thông tin & …", "Kỹ thuật Xây dựng & Công…"); pill "Chưa học" lặp 12/15 thẻ, gây nhiễu | Màn 2–5 |
| 9 | Chip lọc "Công việc & Chuyên môn" bị cắt; danh sách id theo nhóm đang viết cứng trong UI | Màn 2 |
| 10 | Kịch bản tùy chỉnh và mẹo lái xe nằm cuối cùng, sau 15 chủ đề, gần như không ai thấy | Màn 5 |
| 11 | Chip "đang kết nối xe" chỉ là chấm + icon 14dp, không có chữ | Màn 1 |
| 12 | Chế độ IELTS Speaking chỉ vào được bằng giọng nói / Android Auto, không có lối vào trên điện thoại | — |
| 13 | Khi mở màn hình trong (≈750dp ngang) vẫn là 1 cột kéo giãn | — |

## 2. Nguyên tắc bố cục mới

- **Màn đầu tiên phải đủ để học ngay**: ở cỡ chữ 150%, không cần cuộn vẫn thấy: Tiếp tục/Bắt đầu, việc cần ôn (kèm con số), các chế độ luyện.
- **Mỗi hành động chỉ một chỗ** trên Home.
- **Không đặt 2 cột chữ cạnh nhau** trên điện thoại (dễ vỡ khi chữ to). Chỉ dùng lưới cho ô ngắn (icon + 1–2 từ).
- **Không cố định chiều rộng/chiều cao** cho khối có chữ; chỉ dùng `heightIn(min = …)`.
- **Voice-first**: chữ gợi ý "nói lệnh" phải đi kèm một nút mic thật.
- Không thêm setting mới (nếu sau này thêm, phải theo mục 2 của AGENTS.md: tool Gemini Live + parser + test).

## 3. Bố cục đề xuất (điện thoại, từ trên xuống)

```
┌─────────────────────────────────────┐
│ Chào buổi tối 🌙        🔥 1   🚗 Trên xe │  A. 1 dòng
├─────────────────────────────────────┤
│ ⏸ Đang tạm dừng · A2–B1              │
│ 💼 Luyện phát âm: Công việc & KD     │  B. Thẻ "Tiếp tục"
│ [ ▶   TIẾP TỤC BÀI HỌC            ]  │     56dp
│ [ 🎙 Nói lệnh ]     [ 🎲 Ngẫu nhiên ] │     48dp
├─────────────────────────────────────┤
│ Hôm nay ▓▓▓▓▓▓▓▓▓▓ 108/30 phút · +29 từ › │  C. 1 dải
├─────────────────────────────────────┤
│ 📚 12 từ đến hạn ôn                ›  │  D. Cần ôn (ẩn nếu = 0)
│ 🔁 3 lỗi sai cần sửa                ›  │
├─────────────────────────────────────┤
│ Luyện theo kiểu                      │  E. Lưới 2×2
│ [🎧 Nghe truyện ] [🗣 Phát âm     ]   │
│ [🎓 IELTS       ] [🎯 Kịch bản riêng]  │
├─────────────────────────────────────┤
│ Chủ đề gợi ý          Tất cả 15 ›    │  F. 5 dòng gọn
│ 💼 Công việc & Kinh doanh   2 buổi › │
│ 🚦 Kỹ thuật Giao thông      3 buổi › │
│ 🏖 Du lịch & Đi lại              ›   │
└─────────────────────────────────────┘
```

### A. Đầu trang (1 dòng, ~56dp)
- Lời chào theo buổi + chip 🔥 streak + chip xe **có chữ** ("Trên xe") khi kết nối.
- Bỏ logo/tagline và 2 icon Tiến trình/Cài đặt (đã có ở thanh dưới).
- Bỏ dòng "Sẵn sàng luyện nói khi lái xe".

### B. Thẻ "Tiếp tục" (Hero gọn)
- Dòng trạng thái: "Đang diễn ra" / "Đang tạm dừng" / "Gợi ý cho bạn" + cấp độ (một lần duy nhất).
- Tiêu đề bài, tối đa 2 dòng.
- Nút chính 56dp, rộng hết thẻ.
- Hàng phụ 2 nút bằng nhau, cao ≥48dp: **🎙 Nói lệnh** (mới, xem giai đoạn 2) và **🎲 Ngẫu nhiên** (chuyển từ "Chế độ học nhanh" về đây).
- Bỏ câu gợi ý 2 dòng.

### C. Dải "Hôm nay" (1 hàng toàn chiều ngang)
- Thanh tiến độ + "108/30 phút · +29 từ", chạm để mở Tiến trình. Không lặp số streak (đã ở đầu trang).

### D. "Cần ôn" (chỉ hiện khi có việc)
- Mỗi việc một hàng toàn chiều ngang, **con số đứng đầu**: "📚 12 từ đến hạn ôn ›", "🔁 3 lỗi sai cần sửa ›".
- Khi không còn gì: một dòng nhỏ "Đã ôn xong hết 🎉" (không chèn nút phát âm vào đây nữa).

### E. "Luyện theo kiểu" — lưới 2×2 ô bằng nhau
- 🎧 Nghe truyện (badge "Đang nghe dở" nếu có) · 🗣 Phát âm · 🎓 IELTS Speaking (lối vào mới, `MediaIds.IELTS`) · 🎯 Kịch bản riêng (mở sheet danh sách kịch bản tùy chỉnh; ẩn ô nếu chưa có, thay bằng 🎭 Nhập vai).
- Bỏ badge tiếng Anh ("AI Podcast", "Shadowing", "Surprise"); nhãn cho phép 2 dòng.

### F. "Chủ đề gợi ý" + màn "Tất cả chủ đề"
- Home chỉ hiện **5 chủ đề**: đang học dở / học gần đây trước, rồi tới chủ đề chưa học.
- Mỗi chủ đề là một hàng ~72dp: emoji 40dp, tên tiếng Việt (2 dòng tối đa), tên tiếng Anh nhỏ, số buổi **chỉ khi > 0**, mũi tên.
- "Tất cả 15 ›" mở màn mới `TopicsRoute`: chip lọc theo nhóm + ô tìm kiếm + danh sách đầy đủ. Chạm chủ đề vẫn mở `TopicSheet` như hiện tại.
- Chuyển nhóm chủ đề từ các `setOf(...)` trong UI sang trường `category` của `Topic` (module `ai`).

### Bỏ khỏi Home
- Khối "Kịch bản tùy chỉnh" ở cuối → vào ô 🎯 ở mục E.
- Mẹo "Hey Google…" ở cuối → hiện trong banner khi xe đã kết nối (giai đoạn 2) và trong phần trợ giúp của nút 🎙.

## 4. Các giai đoạn

### Giai đoạn 1: Bố cục lại (rủi ro thấp, không đổi logic học)
1. Tách `HomeScreen.kt` (1448 dòng) thành nhiều file trong cùng package `com.speakdrive.ui.screens`: `HomeScreen.kt` (khung), `HomeHeader.kt`, `HomeResumeCard.kt`, `HomeTodayAndReview.kt`, `HomePracticeModes.kt`, `HomeTopicList.kt`, `HomeSheets.kt` (`TopicSheet`, `StorySheet`, sheet kịch bản riêng). Giữ nguyên chữ ký `HomeContent(...)` để test cũ không vỡ.
2. Làm mục A–F như trên.
3. `HomeViewModel`: thêm `recommendedTopics` (sắp theo lần học gần nhất / số buổi), cờ `isLessonPaused` lấy từ `engine.state`.
4. `Topic.category` + màn `TopicsScreen` + `TopicsRoute` trong `MainActivity`.
5. Chuỗi mới ở `values` và `values-vi`; 6 ngôn ngữ còn lại (`de, es, fr, ja, ko, zh`) thêm bản tiếng Anh để lint không báo thiếu bản dịch.
6. Test (`ScreensTest`, `EnglishUiTest`): cập nhật các câu đang tìm "Tiếp tục", "Ôn tập từ vựng", "Du lịch & Đi lại"; thêm test ở cỡ chữ 1.5 (`LocalDensity` với `fontScale = 1.5f`, cấu hình `w411dp-h900dp`) kiểm tra nút Tiếp tục, dòng "từ đến hạn" và ô chế độ hiện ra mà **không cần cuộn**.

### Giai đoạn 2: Giọng nói và trạng thái bài học
1. **Nút 🎙 Nói lệnh trên Home**: dùng `SpeechRecognizer` (vi-VN, có en-US dự phòng) → `VoiceCommandHandler.resolve(transcript)` (đã có ở module `auto`, đã hiểu "ôn từ vựng", "nghe truyện", "IELTS", "chủ đề du lịch", "ngẫu nhiên"…) → `onStartLesson(mediaId)`. Cần thêm test cho đường nối UI → handler.
2. **Thanh "đang học" thu nhỏ** phía trên thanh điều hướng dưới, hiện ở các tab Sổ từ vựng / Tiến trình / Cài đặt khi có bài đang chạy hoặc tạm dừng: tên bài + nút ▶/⏸ + nút mở bài.
3. **Khi xe đã kết nối**: banner "Đang kết nối Android Auto, điều khiển trên màn hình xe hoặc nói 'Hey Google, play … on SpeakDrive'" ngay dưới đầu trang.

### Giai đoạn 3: Màn hình gập (Z Fold, máy tính bảng)
- Dùng `BoxWithConstraints`: khi rộng ≥ 600dp chia 2 cột: trái (B, C, D, E), phải (danh sách chủ đề đầy đủ, 2 cột thẻ). Không cần thêm thư viện.

## 5. Kiểm tra trên máy thật
- Cài qua ADB lên Z Fold7, chụp màn hình ngoài ở cỡ chữ 150% (mặc định của máy) và thử 100%, 200% (`adb shell settings put system font_scale …`, nhớ trả lại 1.5).
- Mở máy để thử màn hình trong (giai đoạn 3).
- Theo AGENTS.md: tăng `version.properties`, build, tải `app-debug.apk` lên GitHub Releases, gửi link tải trực tiếp, cài lên máy qua ADB.

## 6. Cần người dùng chốt
1. Chủ đề trên Home: **5 gợi ý + màn "Tất cả chủ đề"** (đề xuất) hay giữ đủ 15 chủ đề trên Home dạng danh sách gọn?
2. Có thêm ô **IELTS Speaking** vào Home không?
3. Bỏ 2 icon Tiến trình/Cài đặt trên đầu trang (đề xuất: bỏ)?
4. Làm cả 3 giai đoạn liền, hay giai đoạn 1 trước rồi xem trên máy?
