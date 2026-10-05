# Tiến độ triển khai SpeakDrive

*Cập nhật: 02/10/2026*

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
| Audio layer: ghi âm 16 kHz, phát 24 kHz, audio focus | ✅ Dùng audio pipeline của Firebase SDK (có khử tiếng vọng); audio focus có test |
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
| Unit test + Robolectric + Compose UI test | ✅ **79 test**, tất cả pass; lint không còn lỗi |
| Smoke test khởi động app thật (Hilt + Room + Firebase) | ✅ |
| CI GitHub Actions | ✅ `.github/workflows/android.yml` (chạy khi bạn đẩy code lên GitHub) |
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

## Thay đổi so với kế hoạch gốc

- **Audio**: không tự viết `MicrophoneRecorder`/`AudioPlayer` nữa mà dùng audio pipeline của Firebase SDK.
  SDK đã có khử tiếng vọng, barge-in và phát ở 24 kHz. Module `audio` chỉ còn audio focus và
  thông báo bằng giọng nói.
- **Đường điều khiển chung**: điện thoại cũng bắt đầu bài học qua Media3 service giống Android Auto,
  nhờ vậy bài học không bị dừng khi tắt màn hình.
- **Model**: `gemini-3.1-flash-live-preview` cho hội thoại, `gemini-3.8-flash` cho tóm tắt
  (đổi được trong `gradle.properties`).
- **Mô hình kinh doanh** (gói Free/Plus/Pro): chưa làm. Cần sản phẩm trên Play Console và Play Billing.

## Việc bạn cần làm tiếp

1. Tạo Firebase project và thay `google-services.json`: [FIREBASE_SETUP.md](FIREBASE_SETUP.md)
2. Cài lên điện thoại, chạy kịch bản P1–P11, rồi A1–A9 trên DHU: [TESTING.md](TESTING.md)
3. Tạo khóa ký và tài khoản Play Console, điền email vào chính sách bảo mật, upload lên Internal testing:
   [RELEASE.md](RELEASE.md)
4. (Tuỳ chọn) Tạo repo GitHub và push lên, CI sẽ tự chạy test mỗi lần push
