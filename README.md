# 🚗 SpeakDrive — Luyện Giao Tiếp Tiếng Anh Khi Lái Xe

App Android hỗ trợ luyện nói tiếng Anh hoàn toàn rảnh tay (100% hands-free), tích hợp Android Auto, sử dụng AI Gemini để tạo hội thoại tự nhiên.

## ✨ Tính năng chính

- 🎤 **Hội thoại AI real-time** — Trò chuyện tiếng Anh với AI bằng giọng nói
- 🚗 **Android Auto** — Luyện tập khi lái xe, điều khiển bằng giọng nói
- 📱 **Phone App** — Luyện tập trên điện thoại khi không lái xe
- 📚 **8 chủ đề** — Du lịch, Công việc, Ăn uống, Mua sắm, Y tế, Giải trí, Giao tiếp, Phỏng vấn
- 🎯 **3 cấp độ** — Beginner, Intermediate, Advanced
- ✏️ **Sửa lỗi tự nhiên** — AI nhận xét phát âm và ngữ pháp
- 📊 **Theo dõi tiến trình** — Streak, thời gian học, từ mới

## 🏗️ Kiến trúc

```
SpeakDrive/
├── app/        # Phone App (Jetpack Compose UI)
├── audio/      # Audio Layer (MicrophoneRecorder, AudioPlayer, AudioFocus)
├── ai/         # AI Engine (Gemini API, ConversationEngine, Prompts)
└── auto/       # Android Auto (MediaBrowserService, MediaSession)
```

## 🛠️ Tech Stack

- **Kotlin** + **Jetpack Compose** (UI)
- **Hilt** (Dependency Injection)
- **Media3** + **Car App Library** (Android Auto)
- **Firebase AI** (Gemini API)
- **Room** + **DataStore** (Local Storage)
- **Coroutines** + **Flow** (Async)

## 🚀 Cài đặt

### Yêu cầu
- Android Studio Ladybug hoặc mới hơn
- JDK 17+
- Android SDK 36
- Firebase project đã cấu hình

### Bước 1: Firebase Setup
1. Tạo project trên [Firebase Console](https://console.firebase.google.com)
2. Thêm Android app với package `com.speakdrive`
3. Tải `google-services.json` và thay thế file placeholder trong `app/`
4. Bật Firebase AI / Vertex AI trong Firebase Console

### Bước 2: Build
```bash
./gradlew assembleDebug
```

### Bước 3: Test Android Auto
```bash
adb forward tcp:5277 tcp:5277
desktop-head-unit.exe
```

## 📄 License

MIT License
