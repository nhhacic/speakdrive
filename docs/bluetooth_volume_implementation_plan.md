# Kế hoạch khắc phục lỗi không điều chỉnh được âm lượng khi kết nối tai nghe Bluetooth

## 1. Phân tích nguyên nhân gốc rễ (Root Cause Analysis)

Khi ứng dụng SpeakDrive kết nối với tai nghe Bluetooth trong lúc học (phiên thoại trực tiếp với AI):
1. **Lỗi cưỡng bức âm lượng cực đại (`boostVoiceVolume`)**:
   - Khi vào chế độ thoại (`MODE_IN_COMMUNICATION`), hàm `boostVoiceVolume()` trong `LiveAudioIO.kt` tự động đẩy âm lượng cuộc gọi `STREAM_VOICE_CALL` lên tối thiểu 80% (trên hầu hết các máy là mức 5/6 hoặc 6/6 - gần như to nhất).
   - Khi người dùng đeo tai nghe Bluetooth vào sát màng nhĩ, mức 80% - 100% âm lượng cuộc gọi là quá lớn, gây chói tai và khó chịu.
   - Thậm chí khi routing thay đổi, hàm này lại được gọi và kéo âm lượng ngược lên 80% nếu người dùng đã hạ xuống.

2. **Lệch stream điều khiển âm lượng giữa ứng dụng, hệ thống và tai nghe (`volumeControlStream`)**:
   - Khi thoại với AI, `AudioTrack` được tạo với `AudioAttributes.USAGE_VOICE_COMMUNICATION` (thuộc stream `STREAM_VOICE_CALL`).
   - Tuy nhiên, trong toàn bộ ứng dụng, `MainActivity` không hề khai báo `volumeControlStream = AudioManager.STREAM_VOICE_CALL`.
   - Kết quả: Khi người dùng bấm phím âm lượng trên cạnh sườn điện thoại, Android mặc định điều khiển thanh âm lượng `STREAM_MUSIC` (Media)!
   - Khi người dùng bấm/vuốt phím âm lượng trên tai nghe Bluetooth, tai nghe cũng gửi tín hiệu AVRCP điều khiển `STREAM_MUSIC` (Bluetooth Media Volume)!
   - Kể cả khi vào Settings Bluetooth của điện thoại kéo "Âm lượng Bluetooth", slider đó cũng chỉ điều chỉnh Media (A2DP).
   - Tất cả các thao tác trên đều chỉ tác động vào `STREAM_MUSIC`, trong khi âm thanh AI đang phát qua `STREAM_VOICE_CALL`, khiến cho việc điều chỉnh hoàn toàn không có tác dụng!

3. **Lỗi Bluetooth SCO Absolute Volume & Fixed Gain trên Android HAL**:
   - Kể cả khi người dùng mở Volume Panel của hệ điều hành kéo thanh "Cuộc gọi" (Call volume): Trên nhiều dòng điện thoại Android và tai nghe Bluetooth, khi ở cấu hình Bluetooth SCO, Android HAL hoặc chip Bluetooth không đồng bộ lệnh volume qua SCO (Absolute volume chỉ hoạt động trên A2DP), hoặc cố định gain ở phần cứng.

4. **Ứng dụng thiếu bộ suy giảm âm lượng phần mềm (Software PCM Digital Attenuation)**:
   - Trong `LiveAudioIO.kt`, các gói dữ liệu PCM 16-bit 24kHz từ AI được ghi trực tiếp 100% biên độ vào `AudioTrack` (`write(chunk, 0, chunk.size)`) và `track.setVolume(1.0f)`.
   - Vì không có bộ nhân mẫu số học giảm biên độ PCM, nếu phần cứng/HAL Bluetooth bị kẹt ở gain tối đa, âm thanh phát ra loa tai nghe sẽ luôn to hết cỡ.

5. **Chưa có tính năng cài đặt và điều khiển âm lượng giọng nói AI (AI Voice Volume)**:
   - Thiếu thanh trượt âm lượng AI trong `SettingsScreen` và `ConversationScreen`.
   - Thiếu lệnh điều khiển bằng giọng nói (Voice Command) cho âm lượng (vi phạm quy định bắt buộc của dự án).

---

## 2. Giải pháp triển khai toàn diện

### Bước 1: Khắc phục triệt để trong tầng Audio (`LiveAudioIO.kt`)
1. **Dập tắt `boostVoiceVolume()` khi đang dùng tai nghe**:
   - Nếu `isHeadsetConnected()` là true (bao gồm Bluetooth headset, wired headset, USB headset...), bỏ qua hoàn toàn việc tự động kích âm lượng lên 80% để bảo vệ thính giác người học.
2. **Triển khai Software PCM Digital Attenuation + Hardware AudioTrack.setVolume**:
   - Bổ sung `fun setVolume(volumeFraction: Float)` vào `LiveAudio` interface và `LiveAudioIO`.
   - Trong `LiveAudioIO.playbackThread`: Trước khi ghi buffer PCM vào `AudioTrack`, nếu `volumeGain < 0.999f`, nhân trực tiếp từng mẫu 16-bit PCM với `volumeGain`:
     $$s_{new} = \text{clamp}(s \times volumeGain, -32768, 32767)$$
   - Đảm bảo 100% biên độ tín hiệu âm thanh thực tế gửi xuống tai nghe sẽ giảm chính xác theo tỷ lệ người dùng chọn, bất chấp mọi lỗi phần cứng hay driver Bluetooth!

### Bước 2: Đồng bộ stream phím âm lượng phần cứng (`MainActivity.kt`)
- Thiết lập `volumeControlStream = AudioManager.STREAM_VOICE_CALL` khi có bài học đang diễn ra (hoặc khi `audioManager.mode == AudioManager.MODE_IN_COMMUNICATION`), và chuyển về `AudioManager.STREAM_MUSIC` khi không trong bài học.
- Đảm bảo khi bấm phím cứng Volume trên sườn điện thoại, hệ thống hiển thị và điều chỉnh đúng stream mà AI đang phát.

### Bước 3: Thêm cài đặt Âm lượng giọng nói AI (`LearnerSettings` & `UserPreferencesRepository`)
- Thêm `aiVolume: Int = 80` (từ 10% đến 100%, mặc định 80%) vào `LearnerSettings`.
- Lưu trữ trong DataStore qua `UserPreferencesRepository` với hàm `setAiVolume(volume: Int)`.
- Khi khởi động bài học hoặc khi `aiVolume` thay đổi trong Preferences Flow, tự động cập nhật ngay `liveClient.setVolume(aiVolume / 100f)`.

### Bước 4: Điều khiển bằng giọng nói (Voice Commands - Tuân thủ quy định dự án)
1. **Gemini Live Function Calling Tool (`VoiceSettingsTools.kt`)**:
   - Khai báo tool `set_ai_volume` với tham số `volume: String`.
   - Hỗ trợ cả phần trăm ("50%", "80%") lẫn cách nói tương đối ("nói nhỏ lại", "cho nhỏ tiếng", "bé lại", "nói to lên", "tăng âm lượng", "louder", "softer", "quieter", "volume down", "volume up").
   - Đăng ký vào danh sách `allTools`.
2. **Bộ phân tích dự phòng từ speech transcript (`VoiceCommandParser.kt`)**:
   - Bổ sung hàm `parseVolumeCommand(utterance: String, currentVolume: Int): Int?`.
   - Nhận diện các câu nói tiếng Việt và tiếng Anh phổ biến.
3. **Xử lý trong `ConversationEngine.kt`**:
   - Tiếp nhận lệnh từ cả Tool Call và Safety Net transcript.
   - Áp dụng ngay lập tức mức âm lượng mới vào `LiveAudioIO` và lưu vào DataStore.
   - Phản hồi xác nhận bằng giọng nói (Voice Confirmation):
     + Tiếng Việt: *"Đã điều chỉnh âm lượng giọng nói thành X phần trăm."*
     + Tiếng Anh: *"AI volume set to X percent."*

### Bước 5: Giao diện người dùng (UI)
1. **Trong `SettingsScreen.kt`**:
   - Thêm thẻ cài đặt "Âm lượng giọng nói AI" / "AI Voice Volume" với slider trực quan từ 10% đến 100%, hiển thị nhãn phần trăm và icon Volume.
2. **Trong `ConversationScreen.kt`**:
   - Thêm nút Quick Volume Control trên TopAppBar.
   - Khi bấm vào, hiển thị thanh slider điều chỉnh âm lượng nhanh để người học có thể kéo tăng/giảm ngay trong lúc đang nói chuyện mà không cần rời màn hình.

### Bước 6: Kiểm thử (Unit Tests) & Build APK
1. Bổ sung Unit Tests:
   - `VoiceSettingsToolsTest.kt`: Kiểm tra khai báo tool và phân tích lệnh volume tiếng Việt / tiếng Anh.
   - `VoiceCommandParserTest.kt`: Kiểm tra nhận diện các mẫu câu điều chỉnh âm lượng.
   - `ConversationEngineTest.kt`: Kiểm tra xử lý tool call và safety net volume.
2. Build APK: `:app:assembleDebug`.
3. Tự động tải file APK lên GitHub Releases của repo `nhhacic/speakdrive` và cung cấp link direct download chuẩn cho người dùng.
