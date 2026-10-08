# Tiến độ triển khai SpeakDrive

*Cập nhật: 09/10/2026 (v1.3.2)*

Ký hiệu:
- ✅ xong, đã có test tự động
- 🧪 xong trong code, cần thử trên thiết bị thật hoặc DHU
- 👤 cần bạn tự làm (tài khoản, thanh toán, ký app)
- ➖ chưa làm, có lý do kèm theo

## Theo phase của kế hoạch

### Phase 1 – Nền tảng & Prototype
| Hạng mục | Trạng thái |
|---|---|
| Project 4 module, version catalog, Hilt; build thành công | ✅ `assembleDebug`, `assembleRelease` (APK release 2,8 MB) |
| Firebase project thật | 👤 [FIREBASE_SETUP.md](FIREBASE_SETUP.md). Repo đang dùng `google-services.json` mẫu |
| Audio layer: ghi âm 16 kHz, phát 24 kHz, audio focus | ✅ `LiveAudioIO` riêng (đường cuộc gọi có khử vọng phần cứng, dự phòng đường media + MicGate); audio focus có test |
| Gemini Live: nói vào mic → AI trả lời bằng giọng | 🧪 `GeminiLiveManager` dùng `LiveSession.startAudioConversation` |
| Barge-in (ngắt lời AI) | 🧪 Đã bật `enableInterruptions` |
| Độ trễ < 2 giây | 🧪 Đo trên máy thật ([TESTING.md §4](TESTING.md)) |

### Phase 2 – Android Auto
| Hạng mục | Trạng thái |
|---|---|
| Media app xuất hiện trên Auto, cây menu đúng | ✅ test `MediaContentProvider`; 🧪 kiểm tra trên DHU |
| Chọn chủ đề → bắt đầu bài học | 🧪 `SpeakDrivePlayer` + `onAddMediaItems` |
| Play/Pause/Next/Stop trên vô-lăng | 🧪 |
| Metadata hiển thị chủ đề và trạng thái | ✅ test `lessonItem` |
| "Hey Google, play … on SpeakDrive" | ✅ test `VoiceCommandHandler`; 🧪 thử bằng giọng thật trên DHU |
| Micro hoạt động khi điện thoại khoá | 🧪 **Rủi ro lớn nhất**: xem [TESTING.md](TESTING.md), mục A8 |

### Phase 3 – Phone UI
| Hạng mục | Trạng thái |
|---|---|
| Trang chủ: chủ đề, streak, phút, từ mới, mục tiêu ngày, banner Android Auto | ✅ test Compose |
| Luyện trên điện thoại (khi không có Auto) | ✅ test UI; 🧪 hội thoại thật |
| Màn tóm tắt sau buổi học | ✅ test Compose + Room |
| Cài đặt (độ khó, giọng AI, giải thích tiếng Việt, màn hình khi luyện nói, mục tiêu) lưu bằng DataStore | ✅ |

### Phase 4 – AI Engine nâng cao
| Hạng mục | Trạng thái |
|---|---|
| AI mở đầu theo chủ đề và cấp độ | ✅ test |
| Im lặng → nhắc nhẹ; "wait" → chờ; "stop / end the lesson" → kết thúc | ✅ test engine; 🧪 hành vi thật của model |
| Tóm tắt (điểm, từ mới, lỗi sai) được tạo và lưu | ✅ test (cả trường hợp AI lỗi thì dùng tóm tắt dự phòng) |
| Tự kết nối lại khi hết ~10 phút hoặc mất mạng; nhớ nội dung đã nói | ✅ test |

### Phase 5 – Testing
| Hạng mục | Trạng thái |
|---|---|
| Unit test + Robolectric + Compose UI test | ✅ hơn 400 test (4 module), tất cả pass; lint chặn lỗi mới (baseline cho lỗi cũ) |
| Smoke test khởi động app thật (Hilt + Room + Firebase) | ✅ |
| CI GitHub Actions | ✅ `.github/workflows/android.yml`: test, lint, build debug + release mỗi lần push |
| Test trên DHU / xe thật | 👤🧪 Kịch bản chi tiết trong [TESTING.md](TESTING.md) |

### Phase 6 – Deployment
| Hạng mục | Trạng thái |
|---|---|
| Icon app (adaptive + monochrome), icon 512 và feature graphic cho Play | ✅ `docs/store/` |
| Chính sách bảo mật (VI/EN), nội dung Store (VI/EN), gợi ý Data safety | ✅ (cần điền email liên hệ) |
| Cấu hình ký release (`keystore.properties`), R8 | ✅ |
| App Check (debug / Play Integrity) | ✅ trong code; 👤 bật trên Console |
| Tạo app trên Play Console, upload, gửi review Android Auto | 👤 [RELEASE.md](RELEASE.md) |
| Firebase Auth + Firestore đồng bộ đám mây | ➖ Cố ý chưa làm: MVP lưu cục bộ, không cần tài khoản. Muốn làm thì cần Firebase thật để test |

## Theo tính năng (F1–F12)

| # | Tính năng | Trạng thái |
|---|---|---|
| F1 | Hội thoại AI real-time | 🧪 |
| F2 | Chủ đề hội thoại (8 chủ đề, dùng chung một nguồn) | ✅ |
| F3 | Android Auto Media UI | ✅ / 🧪 |
| F4 | Lệnh giọng nói khởi động | ✅ / 🧪 |
| F5 | Phản hồi phát âm và ngữ pháp | 🧪 Qua prompt, cộng thêm danh sách lỗi trong phần tóm tắt |
| F6 | Điều chỉnh độ khó | ✅ |
| F7 | Tóm tắt sau chuyến đi | ✅ |
| F8 | Roleplay | ✅ 32 tình huống |
| F9 | Ôn từ vựng lặp lại ngắt quãng | ✅ |
| F10 | Tiến trình & thống kê | ✅ |
| F11 | Offline mode | ➖ Live API bắt buộc có mạng. App hiện báo bằng giọng nói và tự tiếp tục khi có mạng lại |
| F12 | Giao diện tiếng Việt, giải thích bằng tiếng Việt | ✅ (giải thích tiếng Việt bật/tắt trong Cài đặt) |

## Nâng cấp 08/10/2026 (v1.3.1)

| Tính năng | Trạng thái |
|---|---|
| Sửa gợi ý lộ trình cấp độ: dùng số lỗi thật của từng buổi | ✅ test `ProgressTrendTest`, `LevelEvaluatorTest` |
| AI nhớ người học (lỗi hay mắc, từ khó, điều người học kể), bật/tắt bằng giọng nói, màn "AI đang nhớ gì" | ✅ test engine, prompt, repository; 🧪 hành vi thật của model |
| Chế độ Ôn lỗi sai (`MISTAKE_REVIEW`): điện thoại, Android Auto, "Hey Google, play review my mistakes" | ✅ test; 🧪 trên DHU |
| Room v6: bảng `mistakes`, `learner_facts`; migration 5→6 chuyển lỗi cũ thành lỗi cần ôn | ✅ `MigrationTest` |
| Nhắc luyện tập hằng ngày (WorkManager), giờ tự động theo thói quen hoặc tự chọn | ✅ test logic; 🧪 thông báo thật trên máy |
| Bảo toàn chuỗi ngày học (streak freeze) | ✅ `LearningMathTest` |
| Luyện offline khi mất sóng: TTS + nhận dạng giọng nói trên máy, tự quay lại AI khi có sóng | ✅ test coach và engine; 🧪 **cần thử trên máy thật**: máy phải có gói nhận dạng tiếng Anh offline (Cài đặt → Google → Giọng nói → Nhận dạng giọng nói ngoại tuyến). Không có thì app chuyển sang nghe và nói theo, không chấm điểm |
| Điều khiển bằng giọng nói cho mọi setting mới (tool Gemini, parser dự phòng, bộ nhận dạng lệnh) | ✅ test tiếng Việt và tiếng Anh |

## Rà soát & sửa lỗi 08–09/10/2026 (v1.3.2)

| Nội dung | Trạng thái |
|---|---|
| CI treo 45 phút từ 05/10 (`CarConnectionAutoStarterTest` lặp vô hạn) | ✅ đã sửa, thêm timeout cho mọi task test |
| Khoá Azure và App Check debug token bị nhúng vào APK công khai | ✅ đã gỡ; khoá Azure nhập trong Cài đặt, token App Check sinh riêng mỗi máy (màn Giới thiệu). 👤 xem mục "Việc bạn cần làm" |
| Lệnh giọng nói dự phòng bắt nhầm câu nói thường ("Tôi học tiếng Anh", "đồng ý", câu luyện "Could you say that again?") | ✅ chỉ xét khi nói xong lượt, có cổng chặn; test hồi quy với toàn bộ câu luyện |
| Phiên học: tạm dừng do cuộc gọi/rời app, AI nói đè cuộc gọi sau khi mất mạng, Stop bị treo khi kết nối lại, kết nối lại 2 lần | ✅ test engine |
| Android Auto: 4 tab, nút Next trên vô-lăng, dịch vụ không crash khi bấm Play lúc app đã tắt, chỉ controller tin cậy được kết nối | ✅ test; 🧪 DHU |
| Âm thanh: quyền `MODIFY_AUDIO_SETTINGS`, âm lượng không bị nhân đôi, không rò micro khi lỗi | ✅ |
| Điều khiển giọng nói cho mọi cài đặt (thêm: tự bắt đầu khi lên xe, mục tiêu ngày, Azure, màn hình khi học) | ✅ test tiếng Việt và tiếng Anh |
| Giao diện đủ 8 ngôn ngữ (de/es/fr/ja/ko/zh trước đây chỉ dịch 72/369 chuỗi) | ✅ lint chặn thiếu bản dịch |
| Script đồng bộ hai máy: không tự commit việc làm dở, pull chỉ fast-forward, receiver chỉ nghe trên Tailscale | ✅ (đồng bộ tự động đã tắt theo yêu cầu) |

## Thay đổi so với kế hoạch gốc

- **Audio**: module `audio` có pipeline riêng (`LiveAudioIO`): ưu tiên đường cuộc gọi (khử vọng phần
  cứng, Bluetooth hands-free), dự phòng đường media; khi Android Auto chiếu thì dùng đường media.
- **Đường điều khiển chung**: điện thoại cũng bắt đầu bài học qua Media3 service giống Android Auto,
  nhờ vậy bài học không bị dừng khi tắt màn hình.
- **Model**: `speakdrive.liveModel` cho hội thoại, `speakdrive.textModel` cho tóm tắt và dịch phụ đề
  (đổi trong `gradle.properties`; model "preview" có thể bị Google ngừng bất cứ lúc nào).
- **Mô hình kinh doanh** (gói Free/Plus/Pro): chưa làm. Cần sản phẩm trên Play Console và Play Billing.

## Việc bạn cần làm tiếp

0. **Bảo mật (làm ngay):** các APK debug cũ trên GitHub Releases chứa khoá Azure và App Check token.
   - Azure Portal → Speech resource → **Keys and Endpoint** → **Regenerate Key 1**, rồi nhập khoá mới trong Cài đặt app.
   - Firebase Console → App Check → **Manage debug tokens**: xoá token cũ, thêm token mới hiện trong màn Giới thiệu.
   - Google Cloud Console: đặt cảnh báo ngân sách / giới hạn quota cho Gemini (Firebase AI Logic).
   - Nếu còn dùng receiver đồng bộ: tạo `~/.speakdrive_sync_token` (cùng nội dung trên cả 2 máy), vì token cũ đã lộ.
1. Tạo Firebase project và thay `google-services.json`: [FIREBASE_SETUP.md](FIREBASE_SETUP.md)
2. Cài lên điện thoại, chạy kịch bản P1–P11, rồi A1–A9 trên DHU: [TESTING.md](TESTING.md)
3. Tạo khóa ký và tài khoản Play Console, điền email vào chính sách bảo mật, upload lên Internal testing:
   [RELEASE.md](RELEASE.md)
4. Thử trên máy thật / DHU các điểm có 🧪 ở trên, đặc biệt: micro khi khoá máy (A8), nút Next trên vô-lăng,
   giọng chỉ đường của Maps trong lúc học, cuộc gọi đến khi đang mất sóng.
