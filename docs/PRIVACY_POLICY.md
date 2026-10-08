# Chính sách bảo mật – SpeakDrive

*Cập nhật lần cuối: 09/10/2026*

> **Trước khi đăng:** thay `[EMAIL LIÊN HỆ]` bằng email hỗ trợ thật, rồi đưa trang này lên một URL công khai.

SpeakDrive ("ứng dụng") giúp bạn luyện nói tiếng Anh bằng giọng nói, trên điện thoại và Android Auto.
Chính sách này giải thích ứng dụng xử lý dữ liệu của bạn như thế nào.

## 1. Dữ liệu ứng dụng xử lý

| Dữ liệu | Khi nào | Đi đâu | Lưu bao lâu |
|---|---|---|---|
| Âm thanh giọng nói | Chỉ khi bài học đang chạy | Truyền trực tiếp tới Google Gemini (qua Firebase AI Logic) để AI nghe và trả lời | Ứng dụng **không ghi âm** và không lưu file âm thanh |
| Bản ghi chữ (transcript) | Trong và sau bài học | Lưu **trên điện thoại**; khi kết thúc bài, gửi một lần tới Gemini để tạo nhận xét | Đến khi bạn xoá |
| Điểm, lỗi sai, từ vựng, cài đặt | Sau mỗi bài học | Chỉ lưu **trên điện thoại** | Đến khi bạn xoá |
| Ghi nhớ về người học (lỗi hay mắc, điều bạn kể) | Khi bật "AI nhớ bạn" | Lưu **trên điện thoại**; gửi tới Gemini dưới dạng ghi chú ở đầu bài học | Đến khi bạn tắt / xoá |
| Âm thanh câu luyện phát âm | Chỉ khi bạn **tự bật** chấm điểm Azure và nhập khoá Azure của mình | Gửi tới Microsoft Azure Speech để chấm phát âm | Ứng dụng không lưu file âm thanh |

**Sao lưu Android:** lịch sử học (bài học, từ vựng) có thể nằm trong bản sao lưu Google của điện thoại nếu bạn
bật sao lưu. Cài đặt, khoá Azure và mã App Check **không** được sao lưu.

Ứng dụng **không** yêu cầu tài khoản, **không** thu thập tên, email, danh bạ hay vị trí,
**không** hiển thị quảng cáo và **không** bán dữ liệu.

## 2. Bên thứ ba

- **Google Firebase / Gemini**: xử lý âm thanh và văn bản để tạo câu trả lời. Google xử lý với vai trò
  nhà cung cấp dịch vụ, theo <https://policies.google.com/privacy> và
  <https://firebase.google.com/terms/data-processing-terms>.
- **Firebase App Check / Play Integrity**: xác minh yêu cầu đến từ ứng dụng thật, nhằm chống lạm dụng.
- **Microsoft Azure Speech** (chỉ khi bạn tự bật và dùng khoá của chính bạn): chấm phát âm câu luyện,
  theo <https://privacy.microsoft.com/privacystatement>.

## 3. Quyền truy cập

- **Micro**: để AI nghe bạn nói. Micro chỉ hoạt động khi bài học đang chạy.
- **Internet / trạng thái mạng**: để kết nối AI và tự kết nối lại khi mạng chập chờn.
- **Dịch vụ chạy nền**: để bài học tiếp tục khi màn hình tắt hoặc khi dùng Android Auto.

## 4. Quyền của bạn

- Xoá toàn bộ dữ liệu: **Cài đặt → Chính sách bảo mật → Xoá toàn bộ dữ liệu học tập** (xoá lịch sử, từ vựng,
  ghi nhớ của AI và đưa cài đặt về mặc định, gồm cả khoá Azure), hoặc gỡ cài đặt ứng dụng.
- Thu hồi quyền micro bất kỳ lúc nào trong Cài đặt của Android.

## 5. Trẻ em

Ứng dụng dành cho người từ 18 tuổi (người lái xe) và không cố ý thu thập dữ liệu của trẻ em.

## 6. An toàn khi lái xe

Hãy luôn tập trung lái xe và tuân thủ luật giao thông. Chỉ dùng ứng dụng bằng giọng nói khi xe đang chạy.

## 7. Liên hệ

[EMAIL LIÊN HỆ]

---

# Privacy Policy – SpeakDrive (English)

*Last updated: 9 October 2026*

SpeakDrive helps you practise spoken English hands-free on your phone and in Android Auto.

- **Voice audio** is streamed to Google Gemini (via Firebase AI Logic) **only while a lesson is running**,
  so the AI tutor can hear and answer you. The app does not record or store audio.
- **Transcripts, scores, corrections, vocabulary and settings** are stored **on your device**. When a lesson
  ends, its transcript is sent to Gemini once to generate feedback.
- **Learner memory** (your common mistakes and what you tell the tutor) is stored on your device when
  "AI remembers you" is on, and sent to Gemini as lesson notes. You can turn it off or clear it.
- **Optional Azure scoring:** only if you switch it on with your own Azure key, the audio of your pronunciation
  attempts is sent to Microsoft Azure Speech for scoring.
- **Android backup:** your learning history may be part of your phone's Google backup if backup is on. Settings,
  your Azure key and the App Check token are excluded.
- No account, no ads, no location, contacts or other personal identifiers are collected, and no data is sold.
- **Third parties:** Google Firebase / Gemini (processing on our behalf) and Firebase App Check / Play Integrity
  (abuse prevention). See <https://policies.google.com/privacy>.
- **Permissions:** microphone (lessons only), internet and network state, and a foreground service so lessons
  continue with the screen off or in Android Auto.
- **Your choices:** delete all learning data in *Settings → Privacy policy → Delete all learning data* (history,
  vocabulary, the AI's memory and your settings, including the Azure key), or uninstall the app. You can revoke the microphone permission at any time.
- The app is intended for adults (18+). Always keep your attention on the road.
- **Contact:** [CONTACT EMAIL]
