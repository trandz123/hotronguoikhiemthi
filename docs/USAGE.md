# Hướng dẫn cài đặt và sử dụng app Mắt AI

## 1. Chuẩn bị thiết bị Android

Yêu cầu:
- Android 8.0 (API 26) trở lên
- Có camera sau
- Có cảm biến gia tốc (cho chức năng lắc-dừng-đọc)
- Khoảng 200 MB trống (APK 75 MB + dữ liệu ML Kit OCR tải lần đầu)

### Bật Developer Options (chế độ nhà phát triển)

1. Mở **Cài đặt** → **Giới thiệu về điện thoại** (About phone)
2. Tìm dòng **Số bản dựng** (Build number) — thường ở cuối trang
3. Chạm liên tục 7 lần vào dòng đó
4. Sẽ hiện thông báo "You are now a developer!"
5. Quay lại Cài đặt → vào mục **Tùy chọn dành cho nhà phát triển** vừa xuất hiện

### Bật USB Debugging

Trong **Tùy chọn dành cho nhà phát triển**:
1. Bật **Gỡ lỗi qua USB** (USB Debugging)
2. Nếu định cài qua adb không dây, bật thêm **Gỡ lỗi qua Wi-Fi**

### Bật cài đặt từ nguồn không xác định (nếu dùng cách 2)

**Cài đặt** → **Ứng dụng** → **Truy cập đặc biệt** → **Cài đặt ứng dụng không xác định** → chọn **Trình quản lý file** → bật **Cho phép**.

---

## 2. Cài app — 2 cách

### Cách A: Cài qua adb (khuyến nghị cho dev)

1. Cắm điện thoại vào máy tính bằng dây USB
2. Khi điện thoại hỏi "Cho phép gỡ lỗi USB từ máy tính này?" → bấm **Cho phép** (tích "Always allow")
3. Mở PowerShell tại thư mục project:

```powershell
cd D:\hotronguoikhiemthi
$env:ANDROID_HOME = "C:\Users\minhv\AppData\Local\Android\Sdk"
& "$env:ANDROID_HOME\platform-tools\adb.exe" devices
```

Phải thấy thiết bị xuất hiện trong danh sách, ví dụ:
```
List of devices attached
RFCN30XXXXX     device
```

Nếu thấy `unauthorized` → mở khóa điện thoại, bấm "Allow" trên popup mới hiện.

4. Cài app:

```powershell
.\gradlew.bat installDebug
```

App sẽ build (nếu có thay đổi) + cài. Sau khi xong, mở app từ launcher như bình thường.

### Cách B: Copy file APK + cài thủ công

1. Copy `app\build\outputs\apk\debug\app-debug.apk` sang điện thoại (qua Bluetooth / cáp USB / Google Drive / Telegram self-chat)
2. Trên điện thoại, mở **Trình quản lý file** → tìm file `app-debug.apk` vừa copy
3. Chạm vào → bấm **Cài đặt** → đợi xong → **Mở** hoặc **Xong**

Nếu Android Play Protect cảnh báo "Ứng dụng có hại" → bấm **Vẫn cài** (debug APK chưa ký nên Play Protect không nhận diện được, không có vấn đề).

---

## 3. Lần đầu mở app

App sẽ lần lượt:

1. **Hỏi quyền Camera** → bấm **Cho phép** (bắt buộc cho cả 2 chức năng chính)
2. **Hỏi quyền Microphone** → bấm **Cho phép** (cho voice command). Nếu từ chối, voice command sẽ tắt, các chức năng khác vẫn dùng được.
3. **TTS welcome:** _"Chào mừng đến với Mắt AI. Chạm đôi vào nút Đọc tiền hoặc Đọc menu để bắt đầu. Lắc điện thoại để dừng đọc."_

Lần đầu, ML Kit OCR sẽ tải khoảng 20 MB dữ liệu ngầm (cần Wi-Fi/3G). Tải xong cache lại, lần sau dùng offline được.

---

## 4. Sử dụng từng chức năng

### 4.1. Màn hình chính

4 nút lớn 96dp, xếp dọc:
- 💰 **Đọc tiền**
- 📋 **Đọc menu**
- 🕐 **Lịch sử**
- ⚙️ **Cài đặt**

Cách dùng:
- Chạm 1 lần để **chọn** (TalkBack đọc tên + mô tả)
- Chạm đôi (double-tap) để **kích hoạt**

### 4.2. Đọc tiền

Sau khi mở:
- Camera bật full màn hình
- Phía dưới có overlay đen, hiện status hiện tại
- TTS: _"Đang tìm tờ tiền — đưa camera lại gần"_

**Trường hợp auto-capture BẬT** (vào Cài đặt để bật):
- Đưa tờ tiền cách camera ~20cm, đủ sáng, cầm chắc tay
- App tự phát hiện khung hình đủ rõ (độ sáng + độ nét) → tự chụp
- TTS đọc kết quả: _"Hai trăm nghìn đồng"_
- Sau đó hiện 2 nút: **Quét tiếp** | **Đọc lại** | **Quay lại**

**Trường hợp auto-capture TẮT** (mặc định):
- Chạm đôi vào màn hình bất kỳ chỗ nào → chụp ngay

> **Lưu ý:** model TFLite thật chưa có (chờ tuần 3-4 train). Hiện dùng `FakeMoneyClassifier` cycle qua 9 mệnh giá theo thứ tự (500k → 200k → 100k → ... → 1k → quay lại). Đây là để demo flow, không phải kết quả thật.

### 4.3. Đọc menu (OCR — đã chạy thật)

Sau khi mở:
- Camera bật
- TTS: _"Chạm đôi để chụp menu"_

Quy trình:
1. Chĩa camera vào tờ menu in (giấy, không phải màn hình)
2. Chạm đôi để chụp
3. ML Kit OCR quét text (~1-2 giây)
4. TTS: _"Đã tìm thấy 12 mục. Vuốt phải để nghe từng món, hoặc chạm đôi để nghe toàn bộ"_

**Gesture sau khi có kết quả:**
| Cử chỉ | Hành động |
|---|---|
| **Vuốt phải →** | Món tiếp theo |
| **Vuốt trái ←** | Món trước |
| **Vuốt lên ↑** | Đọc lại món hiện tại |
| **Vuốt xuống ↓** | Dừng TTS |
| **Chạm đôi** | Đọc toàn bộ menu liên tục |
| **Quét lại** (nút) | Chụp menu mới |

**Format đọc:** _"Phở bò, giá năm mươi nghìn đồng"_

**Edge case:**
- Menu không có giá → chỉ đọc tên món
- Camera không thấy text → _"Không tìm thấy văn bản, vui lòng đưa camera đến gần hơn"_

### 4.4. Lịch sử

- Hiện 20 lần quét gần nhất (cả tiền + menu)
- Mỗi card có: loại (Đọc tiền / Đọc menu), thời gian, nội dung TTS, nút **Nghe lại**
- Nút **Xóa hết** xoá toàn bộ lịch sử

### 4.5. Cài đặt

**Tốc độ đọc (0.5x → 2.0x):**
- 3 nút: **− Chậm hơn** | **Reset** | **+ Nhanh hơn**
- Mỗi lần bấm, app đọc xác nhận: _"Tốc độ 80 phần trăm"_
- Slider trên cùng hiển thị giá trị hiện tại (chỉ visual, không bấm-kéo được — dùng 3 nút)

**Giọng đọc (4 option):**
- ● = đang chọn, ○ = chưa chọn
- **Tự động** (mặc định): FPT.AI nếu có API key + online, else Android
- **Nữ Bắc (FPT)** / **Nam Bắc (FPT)** / **Nữ Nam (FPT)**: cần API key
- **Android mặc định**: luôn dùng TTS Android (offline được)

**Độ tương phản (3 option):**
- **Theo hệ thống** (mặc định): theo Dark/Light mode của Android
- **Luôn nền đen, chữ vàng**: tối ưu cho người khiếm thị nhẹ còn nhìn được
- **Luôn nền trắng, chữ đen**

**3 toggle:**
- **Rung phản hồi** (mặc định ON)
- **Tự động chụp tiền** (mặc định OFF)
- **Điều khiển bằng giọng nói** (mặc định ON)

### 4.6. Voice command

Trigger: **Giữ phím Tăng âm lượng (Vol+) khoảng 1.5 giây rồi thả ra**.

Sau khi thả, app sẽ hiện popup nhận giọng nói (giao diện của hệ thống). Nói rõ 1 trong các lệnh:

| Lệnh tiếng Việt | Hành động |
|---|---|
| _"đọc tiền"_, _"tiền"_ | Mở tab Đọc tiền |
| _"đọc menu"_, _"thực đơn"_ | Mở tab Đọc menu |
| _"lịch sử"_ | Mở tab Lịch sử |
| _"thoát"_, _"về"_, _"trang chính"_ | Quay về Home |
| _"đọc lại"_, _"lặp lại"_ | Phát lại nội dung TTS gần nhất |
| _"dừng"_, _"stop"_ | Dừng TTS |
| _"nhanh hơn"_, _"đọc nhanh"_ | Tăng tốc độ TTS 0.2 |
| _"chậm hơn"_, _"đọc chậm"_ | Giảm tốc độ TTS 0.2 |

> Volume sẽ tăng 1 nấc trước khi voice command bật — đây là hành vi mặc định của Android (không thể chặn mà không làm hỏng nút âm lượng). Có thể kéo xuống ngay sau khi voice command xong.

### 4.7. Shake-to-stop (panic stop)

Bất kỳ lúc nào TTS đang đọc mà muốn dừng ngay:
- **Lắc mạnh điện thoại 1 cái** (đủ mạnh, không phải rung nhẹ khi cầm đi lại)
- Hoặc bấm phím **Vol−** (volume down)

Lắc nhẹ khi đi lại không trigger nhờ debounce 800ms + threshold 15 m/s².

---

## 5. Test với TalkBack (giả lập trải nghiệm khiếm thị)

Để hiểu app như người khiếm thị thật:

1. Vào **Cài đặt** → **Trợ năng** → **TalkBack** → **Bật**
2. Sau khi bật, mọi thao tác đổi:
   - **Chạm 1 lần** = chọn (TalkBack đọc tên)
   - **Chạm đôi** = kích hoạt
   - **Vuốt ngang** = chuyển focus giữa các phần tử
   - **Vuốt 2 ngón** = scroll
3. Bịt mắt + dùng app bằng TalkBack → đây gần đúng trải nghiệm thật.

Tắt TalkBack: vào lại Cài đặt → Trợ năng → TalkBack → Tắt. Hoặc giữ đồng thời 2 phím tăng + giảm volume 3 giây (shortcut Android).

---

## 6. FPT.AI TTS — bật giọng đẹp tự nhiên (optional)

Mặc định app dùng Android TTS (giọng máy, không tự nhiên lắm). Để có giọng đẹp:

1. Đăng ký tài khoản tại `https://fpt.ai/vi/products/text-to-speech`
2. Lấy API key (free 5 triệu ký tự / tháng)
3. Tạo file `D:\hotronguoikhiemthi\local.properties` (đã gitignored) với nội dung:

```properties
sdk.dir=C:\\Users\\minhv\\AppData\\Local\\Android\\Sdk
fpt.tts.api.key=PASTE_API_KEY_O_DAY
```

4. Build + cài lại:

```powershell
.\gradlew.bat installDebug
```

5. Trong app → Cài đặt → Giọng đọc → chọn **Tự động** hoặc giọng FPT cụ thể.

Nếu không có internet hoặc hết quota → `RoutedTtsEngine` tự fallback Android TTS.

---

## 7. Troubleshooting

### App crash khi mở
- Xem log: `adb logcat -s "AndroidRuntime:E"`
- Thường do thiếu quyền — kiểm tra lại Camera + Microphone

### "Không bật được camera"
- Quyền chưa cấp → vào Cài đặt → Ứng dụng → Mắt AI → Quyền → bật Camera

### TTS không đọc
- Kiểm tra âm lượng media (không phải nhạc chuông)
- Kiểm tra Android TTS đã có data tiếng Việt: **Cài đặt** → **Hệ thống** → **Ngôn ngữ và bàn phím** → **Tổng hợp giọng nói (TTS)** → chọn engine → cài data Vietnamese
- Nếu vẫn không có → fallback dùng tiếng Anh (giọng máy đọc text Việt có sai phát âm)

### OCR đọc sai dấu tiếng Việt
- Đảm bảo menu in rõ ràng, không bị che, ánh sáng đủ
- Chụp menu in giấy (không phải màn hình laptop — gây moire pattern)
- Distance ~30cm, camera vuông góc menu (không nghiêng quá 20°)

### Voice command không nhận
- Kiểm tra quyền Microphone đã cấp
- Phải giữ Vol+ ÍT NHẤT 1.5 giây mới trigger
- Sau khi thả nút, popup hệ thống mới mở — nói trong vòng 5s
- Nói rõ, không quá nhanh
- Cần kết nối mạng (SpeechRecognizer dùng cloud trừ khi device hỗ trợ offline)

### Build APK lại sau khi sửa code
```powershell
$env:ANDROID_HOME = "C:\Users\minhv\AppData\Local\Android\Sdk"
cd D:\hotronguoikhiemthi
.\gradlew.bat installDebug
```

### Gỡ app
```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" uninstall com.trandz123.hotronguoikhiemthi
```

Hoặc giữ icon app → **Gỡ cài đặt**.

---

## 8. Logging để debug

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" logcat -s "AndroidTtsEngine:*" "FptTtsEngine:*" "MlModule:*" "MoneyVM:*" "TfliteMoneyClassifier:*" "VoiceCommandService:*"
```

Mỗi component log với tag riêng, dễ filter.

---

## 9. Bước tiếp theo cho đồ án

Sau khi cài và dùng thử app, các việc còn lại (xem `docs/PLAN.md`):

| Tuần | Việc |
|---|---|
| 2 | Chụp 200 ảnh × 9 mệnh giá + 200 ảnh unknown |
| 3-4 | Train MobileNetV3-Small trên Colab → export `vnd_classifier.tflite` |
| 5 | Drop file `.tflite` vào `app/src/main/assets/ml/` → rebuild → `TfliteMoneyClassifier` tự pick up |
| 7 | Polish UX dựa trên feedback test với mắt bịt + TalkBack |
| 8 | Test với 3-5 người khiếm thị thật (Hội Người Mù Hà Nội — Tây Sơn) |
| 9 | Release APK, ký key, test 3-5 thiết bị |
| 10 | Viết báo cáo + slide + demo video |
