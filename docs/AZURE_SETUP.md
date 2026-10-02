# Chấm phát âm bằng Azure (tuỳ chọn)

Khi bật tuỳ chọn này, chế độ **Luyện phát âm: nhắc lại theo AI** có thêm một giám khảo thứ ba là
**Azure Pronunciation Assessment** của Microsoft. Azure chấm điểm 0–100 cho từng câu, từng từ và từng âm.
Một câu chỉ **ĐẠT** khi cả ba giám khảo cùng đồng ý:

| Giám khảo | Chấm gì |
|---|---|
| AI (Gemini) | Nghe trực tiếp âm thanh và nhận xét |
| Bản ghi chữ | So từng từ với câu mẫu |
| **Azure** | Điểm phát âm, độ chính xác, độ trôi chảy, độ đầy đủ; tìm ra âm yếu nhất của từng từ (vd: `three (th 12)`) |

Điều kiện để Azure tính là đạt (cố ý đặt khắt khe): điểm tổng ≥ 80, độ đầy đủ ≥ 80, và **không có từ nào**
bị đánh dấu phát âm sai, bị thiếu, hoặc có độ chính xác dưới 60.

## Chi phí

- Gói **Free (F0)**: **5 giờ âm thanh mỗi tháng**, miễn phí. Chỉ tính phần **bạn nói** khi luyện phát âm,
  mỗi lần khoảng 3–5 giây. Học cá nhân hầu như không vượt mức này.
- Gói Standard (S0): khoảng 1,3 USD cho mỗi giờ âm thanh.

## 1. Tạo Speech resource (khoảng 5 phút)

1. Vào <https://portal.azure.com> và đăng nhập. Tài khoản mới cần xác minh bằng thẻ, nhưng gói F0 không trừ tiền.
2. Thanh tìm kiếm → **Speech services** → **Create**.
3. Điền:
   - **Resource group**: tạo mới, ví dụ `speakdrive`
   - **Region**: **Southeast Asia** (gần Việt Nam nhất)
   - **Name**: tên bất kỳ, ví dụ `speakdrive-speech`
   - **Pricing tier**: **Free F0**
4. **Review + create** → **Create**, đợi khoảng 1 phút.
5. Vào resource vừa tạo → **Keys and Endpoint**. Copy **KEY 1** và **Location/Region** (ví dụ `southeastasia`).

## 2. Nhập vào app

**Cách A: trên điện thoại.** Mở **Cài đặt → Chấm phát âm bằng Azure**, bật lên, nhập **Region** và **Key**,
rồi bấm **Lưu & kiểm tra kết nối**. Thấy dòng *✓ Kết nối Azure thành công* là xong.

**Cách B: khi build bản debug.** Thêm vào `local.properties` (file này không được commit):

```properties
azure.speechKey=KEY_1_CUA_BAN
azure.speechRegion=southeastasia
```

Sau đó build lại (`./gradlew installDebug`) và bật công tắc trong Cài đặt. Key nhập trên điện thoại
sẽ ưu tiên hơn key trong `local.properties`.

## 3. Bảo mật

- Key chỉ lưu trên điện thoại và chỉ gửi tới máy chủ Azure. Bản **release** không bao giờ chứa key có sẵn.
- Không đưa key vào code hay Git. Nếu lỡ lộ, vào Azure portal → **Keys and Endpoint** → **Regenerate Key 1**.
- Khi phát hành cho nhiều người dùng, không nên dùng chung một key trong app. Nên làm một máy chủ nhỏ
  (ví dụ Firebase Cloud Functions) để cấp token tạm thời qua endpoint `issueToken`.

## Khi có lỗi

| Thông báo | Nguyên nhân |
|---|---|
| *Key không đúng hoặc không thuộc region này (HTTP 401)* | Copy sai key, hoặc region không khớp với resource |
| *Azure không phản hồi: …* trên thẻ luyện tập | Mất mạng hoặc Azure quá tải. Lần đó app vẫn chấm bằng 2 giám khảo còn lại |
| *Chưa nhập Region/Key Azure* | Đã bật công tắc nhưng chưa lưu Region/Key |
| Hết 5 giờ miễn phí | Azure trả lỗi 403/429 cho đến tháng sau, hoặc nâng lên gói S0 |
