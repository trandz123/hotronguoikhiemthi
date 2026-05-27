# Chức năng & cách hoạt động

## Trải nghiệm tổng quan (cho người khiếm thị)

User mở app → nghe câu chào: *"Chào mừng đến với Mắt AI. Chạm đôi lên màn hình để đọc tiền, vuốt sang phải để đọc menu, vuốt lên để cài đặt."*

### Cử chỉ điều khiển

| Cử chỉ | Hành động |
|---|---|
| Chạm đôi (double-tap) | Kích hoạt nút đang focus |
| Vuốt trái/phải | Chuyển chức năng / món tiếp theo |
| Vuốt lên/xuống | Đọc lại / dừng |
| Lắc điện thoại | Dừng TTS (panic stop) |
| Giữ Volume Up 2s | Voice command |

---

## 1. 💵 Nhận diện tiền VND

### Luồng
1. User chọn "Đọc tiền" → camera tự bật
2. Voice guide: *"Đặt tờ tiền cách camera 20cm"*
3. App tự chụp khi đủ điều kiện (sáng, không rung, có vật thể)
4. TFLite inference (~150ms) → đọc to: *"Hai trăm nghìn đồng"*
5. Rung 1 nhịp ngắn xác nhận

### Mở rộng
- **Đếm nhiều tờ**: detect nhiều tờ trong 1 ảnh, đọc tổng
- **Phát hiện tiền giả** (optional): so sánh đặc điểm bảo an

### Pipeline kỹ thuật
```
CameraX frame
   ↓ (ImageAnalysis, mỗi 100ms)
Quality check (sáng, độ rõ, vật thể)
   ↓ (pass)
Capture 224x224
   ↓
TFLite Interpreter (vnd_classifier.tflite)
   ↓
Softmax → argmax → label
   ↓
NumberToVietnamese → text
   ↓
TtsManager.speak()
   ↓
Haptic feedback + lưu Room DB
```

Latency mục tiêu: **< 1 giây** end-to-end.

---

## 2. 📋 Đọc menu đồ ăn

### Luồng
1. User chọn "Đọc menu" → camera bật
2. Voice guide: *"Đưa camera đến menu"*
3. ML Kit OCR detect text → auto-capture khi confidence cao
4. Parser tách thành `[(tên món, giá)]`
5. TTS: *"Đã tìm thấy 12 món. Vuốt phải để nghe từng món."*

### Điều khiển khi đang đọc menu
- Vuốt phải → món tiếp: *"Phở bò, năm mươi nghìn"*
- Vuốt trái → món trước
- Vuốt lên → đọc lại
- Vuốt xuống → dừng
- Chạm đôi → đánh dấu yêu thích

### Edge cases
- Menu không có giá → đọc tên thôi
- Menu không có text → *"Không tìm thấy text, vui lòng thử lại"*
- Menu nhiều cột → sắp xếp trái-phải, trên-xuống

---

## 3. 🔍 Mô tả vật thể xung quanh (optional)

ML Kit Object Detection → đọc: *"Phía trước có bàn, ghế, ly nước"*

Hữu ích khi vào quán mới, tìm chỗ ngồi.

---

## 4. 🗣️ Voice Command

| Câu lệnh | Hành động |
|---|---|
| "đọc tiền" | Mở camera tiền |
| "đọc menu" | Mở camera menu |
| "đọc lại" | Phát lại TTS gần nhất |
| "dừng" | Dừng TTS |
| "to hơn" / "nhỏ hơn" | Chỉnh tốc độ đọc |
| "thoát" | Về màn chính |

---

## 5. ⚙️ Cài đặt

- **Tốc độ đọc**: 0.5x → 2x
- **Giọng TTS**: Nữ Bắc / Nam Bắc / Nữ Nam (FPT) / Google / Android
- **Tự động chụp**: bật/tắt
- **Độ tương phản cao**: bật/tắt
- **Rung phản hồi**: bật/tắt
- **Lịch sử**: 20 lần quét gần nhất

---

## So sánh với app hiện có

| App | Hạn chế | Mắt AI giải quyết |
|---|---|---|
| Microsoft Seeing AI | Không có tiếng Việt, không có VND | TTS tiếng Việt, train riêng VND |
| Google Lookout | OCR menu chưa parse giá | Parser custom regex giá VND |
| Be My Eyes | Cần người tình nguyện online | 100% AI, offline |
| Cash Reader | Không hỗ trợ VND | Tập trung VND |
