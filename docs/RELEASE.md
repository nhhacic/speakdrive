# Phát hành lên Google Play

Thứ tự thực hiện: **ký app → kiểm tra → Play Console → review Android Auto**.
Những bước cần tài khoản hoặc thanh toán thì bạn phải tự làm, phần còn lại trong code đã sẵn sàng.

## 1. Tạo khóa ký (chỉ làm một lần, giữ thật kỹ)

```bash
keytool -genkeypair -v -keystore speakdrive-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
```

Copy `keystore.properties.example` thành `keystore.properties` ở thư mục gốc rồi điền thông tin.
File này và `*.jks` đã nằm trong `.gitignore`, nên **không bao giờ** được commit.

```bash
./gradlew bundleRelease
```

File cần upload: `app/build/outputs/bundle/release/app-release.aab`.
Mỗi lần upload bản mới, tăng `versionCode` trong `app/build.gradle.kts`.

## 2. Checklist trước khi upload

- [ ] `app/google-services.json` là file thật (xem [FIREBASE_SETUP.md](FIREBASE_SETUP.md))
- [ ] `./gradlew testDebugUnitTest lintDebug` không có lỗi
- [ ] Đã chạy đủ kịch bản trong [TESTING.md](TESTING.md), gồm cả DHU và mục A8 (micro khi khoá máy)
- [ ] App Check đã bật Play Integrity và đã thêm SHA-256 của khóa ký vào Firebase
- [ ] Đã đặt budget alert cho Gemini trong Google Cloud
- [ ] Đã đăng chính sách bảo mật lên một URL công khai (xem bên dưới)
- [ ] Đã điền email liên hệ thật vào [PRIVACY_POLICY.md](PRIVACY_POLICY.md)

## 3. Play Console

1. Tạo app: tên **SpeakDrive**, ngôn ngữ mặc định Tiếng Việt, loại **App**, **Free**.
2. **Store listing**: dùng nội dung trong [STORE_LISTING.md](STORE_LISTING.md) và ảnh trong `docs/store/`
   (icon 512×512, feature graphic 1024×500). Ảnh chụp màn hình điện thoại cần ít nhất 2 ảnh,
   chụp từ máy thật hoặc emulator.
3. **App content**
   - Privacy policy: URL công khai của [PRIVACY_POLICY.md](PRIVACY_POLICY.md). Có thể dùng GitHub Pages
     hoặc Google Sites.
   - Ads: **No**.
   - App access: không cần đăng nhập.
   - Content rating: Education, không có nội dung nhạy cảm.
   - Target audience: 18+, vì người dùng là người lái xe.
   - **Data safety**: xem gợi ý bên dưới.
4. **Android Auto**: Advanced settings → **Form factors → Android Auto → Add** → đồng ý điều khoản.
   App phải ở track **Internal / Closed testing** trước thì mới gửi review Auto được.
5. Đẩy lên **Internal testing**, thêm email tester, cài thử qua link Play → chạy lại kịch bản DHU.
6. Khi ổn thì đẩy lên **Production**.

### Gợi ý điền Data safety

> Đây là gợi ý dựa trên cách app đang chạy. Bạn là người chịu trách nhiệm cuối cùng về câu trả lời.

| Câu hỏi | Trả lời |
|---|---|
| App có thu thập hoặc chia sẻ dữ liệu người dùng? | **Có** |
| Audio → *Voice or sound recordings* | Thu thập: **Có**, xử lý tạm thời (*processed ephemerally*); chia sẻ: **Không** (Google xử lý thay mặt bạn với vai trò nhà cung cấp dịch vụ); mục đích: **App functionality** |
| App activity → *Other user-generated content* (transcript gửi đi để tạo nhận xét) | Thu thập: **Có**, xử lý tạm thời; mục đích: **App functionality** |
| Dữ liệu có được mã hoá khi truyền? | **Có** (HTTPS/WSS) |
| Người dùng có thể yêu cầu xoá dữ liệu? | **Có**: Cài đặt → Chính sách bảo mật → Xoá toàn bộ dữ liệu học tập |

## 4. Review Android Auto: những điểm Google sẽ kiểm tra

Đối chiếu với [Car App Quality Guidelines](https://developer.android.com/docs/quality-guidelines/car-app-quality):

| Yêu cầu | Cách SpeakDrive đáp ứng |
|---|---|
| Điều khiển bằng giọng nói | "Hey Google, play … on SpeakDrive" (`VoiceCommandHandler`), nói "end the lesson" để kết thúc |
| Không gây mất tập trung | Màn hình xe chỉ dùng template media chuẩn, không có chữ dài, không có video |
| Tối đa vài bước thao tác | Tab *Bắt đầu* → *Tiếp tục bài học*: 2 lần chạm |
| Audio focus | Tạm dừng khi có cuộc gọi hoặc giọng chỉ đường, tự tiếp tục sau đó (`ConversationEngine`) |
| Nút trên vô-lăng | Play/Pause/Next/Stop được map sang điều khiển bài học (`SpeakDrivePlayer`) |
| Báo lỗi rõ ràng | Lỗi (thiếu quyền micro, mất mạng) hiện trên màn hình xe qua `PlayerError` |
| Không crash | Có smoke test khởi động app; nên theo dõi thêm qua Play Console *Android vitals* |

> ⚠️ **Rủi ro về chính sách:** Android Auto chỉ chấp nhận một số loại app (media, messaging, navigation…).
> SpeakDrive đăng ký là **media app**. Google có thể hỏi vì sao một media app lại dùng micro.
> Khi gửi review, hãy giải thích rõ: *"Interactive spoken English lessons; the microphone is used only
> while a lesson is playing so the learner can answer the AI tutor hands-free."*
> Thời gian review thường từ 1 đến 2 tuần, và có thể bị yêu cầu chỉnh sửa.

## 5. Sau khi phát hành

- Theo dõi crash và ANR trong Play Console → Android vitals.
- Theo dõi chi phí Gemini trong Google Cloud Billing.
- Khi đổi schema Room: thay `fallbackToDestructiveMigration` bằng migration thật (`DatabaseModule.kt`),
  để người dùng không bị mất lịch sử học.
