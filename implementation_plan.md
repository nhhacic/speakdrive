# Kế hoạch triển khai: Tối ưu giao diện Android Auto — Chữ sáng bật & Cỡ chữ to rõ

## 1. Mục tiêu
Khắc phục triệt để vấn đề hiển thị trên màn hình Android Auto (Coolwalk):
- Tăng độ sáng và độ tương phản của dòng chữ cần nhắc lại (Target Text): chuyển sang màu trắng tinh rực rỡ (Pure White `#FFFFFF`) với nét đậm (Bold) và shadow layer chống lóa, giúp dòng chữ nổi bật tức thì trong mọi điều kiện ánh sáng xe hơi.
- Mở rộng vùng hiển thị chiếm trọn khung màn hình Artwork (từ giới hạn cũ 279px lên 556px, gấp đôi chiều cao), loại bỏ khoảng trống đen vô nghĩa ở nửa dưới.
- Tăng cỡ chữ (Adaptive Font Size) lên đến 54f (cho câu ngắn thông thường như *"We need a better plan."*) thay vì tối đa 36f như trước, giúp tài xế chỉ cần liếc mắt 0.5 giây là đọc được dễ dàng.
- Tối ưu tương tự cho bản dịch tiếng Việt (màu vàng chanh sáng) và màn hình trò chuyện tự do/kể chuyện.

## 2. Các bước thực hiện
1. **Sửa đổi `AutoCardArtworkGenerator.kt`**:
   - Mở rộng vùng Hero Box và Card chung: `heroBoxTop = 20f`, `heroBoxBottom = 576f` (chiều cao khả dụng 556px).
   - Thêm pill badge "🎯 LẶP LẠI THEO AI" bo góc sáng rõ ở đỉnh thẻ.
   - Nâng cấp `candidateFontSizes` lên `floatArrayOf(54f, 48f, 42f, 38f, 34f, 30f, 26f)`.
   - Cài đặt hiệu ứng shadow layer và nét đậm cho `TextPaint` của dòng chữ mục tiêu.
   - Nâng cấp kích thước font cặp song ngữ (Tiếng Anh trắng sáng + Tiếng Việt vàng sáng).
   - Mở rộng không gian hiển thị cho màn hình AI trò chuyện / kể chuyện (`renderGeneralConversationScreen`).
2. **Cập nhật Unit Test**:
   - Bổ sung/cập nhật test trong `AutoCardArtworkGeneratorTest.kt`.
   - Chạy kiểm tra: `.\gradlew :auto:testDebugUnitTest`.
3. **Phát hành & Đồng bộ hóa theo quy định**:
   - Tăng version trong `version.properties` lên `versionCode=29`, `versionName=1.2.21`.
   - Build APK Debug: `.\gradlew :app:assembleDebug`.
   - Tạo GitHub Release và upload file APK: `gh release create v1.2.21-debug ...`.
   - Chạy script đồng bộ sang máy Linux: `python scripts/sync_workspace.py --direction to-linux`.
   - Gửi direct link tải APK cho người dùng.
