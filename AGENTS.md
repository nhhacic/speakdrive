# Quy định dành cho AI Agent — SpeakDrive (English Speaking App)

## 1. Nguyên tắc cốt lõi: Voice-First & Lái xe rảnh tay
Ứng dụng SpeakDrive được thiết kế để học viên luyện nói tiếng Anh an toàn khi đang lái xe hoặc rảnh tay. Vì vậy, mọi thao tác người dùng có thể thực hiện trên màn hình UI đều phải thực hiện được bằng giọng nói.

## 2. Bắt buộc: Tự động thêm điều khiển bằng giọng nói khi tạo Setting mới
Khi bất kỳ AI agent nào thêm hoặc cập nhật một mục cài đặt (setting) mới trong ứng dụng (ví dụ: trong DataStore preferences, `UserPreferencesRepository`, `SettingsScreen`, `LearnerSettings`...), AI agent **BẮT BUỘC PHẢI TỰ ĐỘNG THÊM TÍNH NĂNG THIẾT LẬP BẰNG GIỌNG NÓI** để điều chỉnh setting đó:

1. **Gemini Live Function Calling Tool (`VoiceSettingsTools.kt`)**:
   - Khai báo tool tương ứng (tên hàm, mô tả rõ ràng bằng tiếng Anh & ví dụ tiếng Việt, tham số) trong `VoiceSettingsTools.kt`.
   - Đăng ký tool vào danh sách công cụ gửi lên Gemini Live session.

2. **Parser dự phòng từ Speech Transcript (`VoiceCommandParser.kt`)**:
   - Bổ sung hàm parse và các tập từ khóa (cả tiếng Việt có dấu, không dấu, và tiếng Anh) để nhận diện yêu cầu điều chỉnh setting khi Gemini không chủ động kích hoạt tool.
   - Hỗ trợ các cách nói tương đối (ví dụ: nhanh hơn/chậm hơn, to hơn/nhỏ hơn, bật/tắt, v.v.).

3. **Android Auto / Media Query Handler (`VoiceCommandHandler.kt`)**:
   - Nếu setting ảnh hưởng đến chế độ khởi động hoặc phiên học qua Google Assistant / Android Auto media command, cập nhật bộ phân tích trong `VoiceCommandHandler.kt`.

4. **Xử lý trạng thái và lưu trữ (`UserPreferencesRepository.kt` & `ConversationEngine.kt`)**:
   - Khi nhận lệnh giọng nói, cập nhật ngay vào repository/DataStore và áp dụng trực tiếp cho phiên học hiện tại nếu phiên đang diễn ra.

5. **Phản hồi xác nhận bằng giọng nói (Voice Confirmation Feedback)**:
   - Thông báo ngắn gọn bằng giọng nói cho người học biết setting đã được điều chỉnh thành công (ví dụ qua TTS thông báo nhanh hoặc câu thoại xác nhận của AI).

6. **Viết Unit Test đầy đủ**:
   - Luôn bổ sung test case trong `VoiceSettingsToolsTest.kt`, `VoiceCommandParserTest.kt` và `VoiceCommandHandlerTest.kt` để kiểm tra cả trường hợp tiếng Việt lẫn tiếng Anh.

## 3. Bắt buộc: Tự động cập nhật số phiên bản và tải file APK lên GitHub Releases sau mỗi khi build xong
Mỗi khi hoàn thành phiên làm việc có tạo hoặc build file APK (`:app:assembleDebug` hoặc `:app:assembleRelease`), AI agent **BẮT BUỘC PHẢI THỰC HIỆN**:
- **Đồng bộ số phiên bản**: Trước khi chạy lệnh build APK, **bắt buộc phải cập nhật và tăng số phiên bản** (`versionName` và `versionCode`) trong file `version.properties` ở thư mục gốc để thông tin hiển thị trong ứng dụng và mã phiên bản Android khớp chính xác với tag phát hành mới.
- **Tên file APK cố định là `app-debug.apk`**: Tuyệt đối không đổi tên file APK khi upload, luôn luôn giữ nguyên tên file là `app-debug.apk` (từ `app/build/outputs/apk/debug/app-debug.apk`).
- **Tải APK lên GitHub Releases**: Tự động upload file `app-debug.apk` lên GitHub Releases của repo `nhhacic/speakdrive` bằng lệnh:
  `gh release upload <tag> <path_to_apk> --clobber` hoặc `gh release create <tag> <path_to_apk> --repo nhhacic/speakdrive --title <title> --notes <notes>`.
- **Gửi link tải trực tiếp**: Luôn gửi đường link direct download định dạng:  
  `https://github.com/nhhacic/speakdrive/releases/download/<tag>/app-debug.apk`  
  để người dùng bấm vào là tải file APK về điện thoại ngay lập tức (không có quảng cáo, tốc độ cao qua CDN GitHub, không bị chặn bởi nhà mạng Việt Nam).
- Tuyệt đối không dùng các dịch vụ chia sẻ file có quảng cáo, đếm ngược hoặc dễ bị nhà mạng Việt Nam chặn.
## 4. Đồng bộ hóa Code & Phiên làm việc sang máy đối tác (Multi-Machine Sync) — [ĐÃ TẮT TỰ ĐỘNG]
- **Chế độ tự động đồng bộ ĐÃ TẮT theo yêu cầu của người dùng**: AI Agent **TUYỆT ĐỐI KHÔNG TỰ ĐỘNG CHẠY** script `sync_workspace.py` sau mỗi nhiệm vụ hoặc sau khi commit code.
- **Chỉ đồng bộ thủ công**: Chỉ chạy đồng bộ khi người dùng đưa ra yêu cầu trực tiếp. Người dùng có thể tự kích hoạt bằng cách chạy file batch `sync_to_linux.bat` / `pull_from_linux.bat` trên Windows hoặc khi có lệnh cụ thể.

