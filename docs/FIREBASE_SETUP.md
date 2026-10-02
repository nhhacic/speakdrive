# Cài đặt Firebase & Gemini

SpeakDrive gọi Gemini qua **Firebase AI Logic** nên không có API key nào nằm trong code.
File `app/google-services.json` trong repo chỉ là **file mẫu** (`PLACEHOLDER`). App vẫn build được,
nhưng phải thay bằng file thật thì AI mới trả lời.

## 1. Tạo project và đăng ký app Android

1. Vào <https://console.firebase.google.com> → **Add project** (ví dụ `speakdrive`).
2. **Project settings → Your apps → Add app → Android**
   - Package name: `com.speakdrive`
   - SHA-1: chưa cần ở bước này. App Check bằng Play Integrity sẽ cần SHA-256 của khóa ký (xem bước 4).
3. Tải `google-services.json` về và **ghi đè** `app/google-services.json`.

## 2. Bật Firebase AI Logic (Gemini Developer API)

1. Menu trái → **AI Logic** → **Get started**.
2. Chọn **Gemini Developer API**. Gói miễn phí đủ để phát triển và thử nghiệm.
   App dùng `GenerativeBackend.googleAI()`, khớp với lựa chọn này.
3. Không cần tạo hay dán API key: Firebase tự quản lý key.

Model mặc định (có thể đổi trong `gradle.properties` mà không cần sửa code):

| Mục đích | Thuộc tính | Mặc định |
|---|---|---|
| Hội thoại giọng nói (Live API) | `speakdrive.liveModel` | `gemini-3.1-flash-live-preview` |
| Tóm tắt & chấm điểm cuối buổi | `speakdrive.textModel` | `gemini-3.8-flash` |

Nếu model Live dạng preview bị thay hoặc ngừng hỗ trợ, đổi sang model khác trong
[danh sách model Live API](https://firebase.google.com/docs/ai-logic/live-api), ví dụ:

```properties
# gradle.properties
speakdrive.liveModel=gemini-2.5-flash-native-audio-preview-12-2025
```

## 3. App Check: bắt buộc trước lần chạy đầu tiên

> ⚠️ Project Firebase mới **bật sẵn chế độ bắt buộc App Check cho AI Logic**. Bỏ qua bước này thì app báo
> *"Không kết nối được với AI"*, và Logcat có dòng `403 App attestation failed`.

App đã cài sẵn App Check: bản **debug** dùng *debug provider*, bản **release** dùng *Play Integrity*
(xem `app/src/debug` và `app/src/release`).

1. **Project settings → General → app Android → Add fingerprint**: thêm SHA-1 và SHA-256 của khóa debug.
   Lấy bằng lệnh `keytool -list -v -keystore ~/.android/debug.keystore -storepass android`.
   Sau này thêm cả khóa release.
2. **Security → App Check → Apps** → chọn app Android → **Play Integrity** → **Save**.
3. Cài bản debug lên máy, mở app một lần, rồi tìm trong Logcat dòng `Firebase App Check debug token: …`.
4. App Check → menu ⋮ của app → **Manage debug tokens** → **Add debug token** → dán token vừa copy.

Mỗi lần **gỡ cài đặt** app hoặc xoá dữ liệu app, token debug sẽ đổi và phải thêm token mới.
Cài đè bằng `./gradlew installDebug` thì token được giữ nguyên.

## 4. SHA-256 cho Play Integrity (khi phát hành)

Play Console → **Setup → App signing** → copy **SHA-256 của khóa ký app** → Firebase
**Project settings → Your apps → Add fingerprint**.

## 5. Kiểm tra nhanh

1. `./gradlew installDebug` rồi mở app → chọn một chủ đề → cấp quyền micro.
2. Nếu AI chào bạn bằng giọng nói là đã cấu hình xong.
3. Nếu màn hình báo *"Chưa cấu hình Firebase/Gemini"*: kiểm tra lại `google-services.json` và bước 2.
4. Nếu báo *"Không kết nối được với AI"*: xem Logcat (lọc theo `GeminiLiveManager`) để biết lỗi cụ thể,
   thường do tên model, quota hoặc App Check đang Enforce mà chưa đăng ký debug token.

## Chi phí

Live API tính theo số token âm thanh. Trong Firebase/Google Cloud Console nên đặt **budget alert**
trước khi phát hành. App đã tự đóng kết nối khi tạm dừng quá 2 phút và tự dừng khi người học im lặng lâu,
để tránh tốn token vô ích.
