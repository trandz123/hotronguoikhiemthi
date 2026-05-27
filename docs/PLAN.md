# Plan thực hiện — 10 tuần

## Mục tiêu cuối

App Android `.apk` chạy được trên thiết bị thật, có 2 tính năng chính (nhận diện tiền VND + đọc menu OCR), accuracy nhận diện tiền > 92% trên test set, đã test với người khiếm thị thật.

---

## Tuần 1 — Khởi tạo & khảo sát

- [x] Tạo repo GitHub
- [ ] Setup Android Studio project (Empty Activity, Kotlin, Min SDK 26)
- [ ] Liên hệ Hội Người Mù Hà Nội (Tây Sơn) xin phỏng vấn 2-3 user khiếm thị
- [ ] Khảo sát app hiện có: Seeing AI, Lookout, Be My Eyes, Cash Reader
- [ ] Vẽ user flow chính + wireframe accessible

**Deliverable:** Android project khởi tạo + báo cáo khảo sát (5-10 trang)

## Tuần 2 — Thu thập dataset tiền VND

- [ ] Chuẩn bị 9 mệnh giá: 500k, 200k, 100k, 50k, 20k, 10k, 5k, 2k, 1k
- [ ] Chụp 200 ảnh/mệnh giá, đa dạng:
  - Ánh sáng (sáng, tối, đèn vàng)
  - Góc (thẳng, nghiêng 30-60°)
  - Trạng thái (mới, cũ, gấp)
  - Background đa dạng
- [ ] Thêm ~200 ảnh class `unknown` (không phải tiền)
- [ ] Tổng ~2000 ảnh, organize theo folder
- [ ] Script Python: rename, resize 224x224, split 70/15/15
- [ ] Upload Google Drive (mount Colab)

**Deliverable:** Dataset trên Drive + script preprocess

## Tuần 3-4 — Train model nhận diện tiền

- [ ] Notebook 1: load dataset, EDA, augmentation
- [ ] Notebook 2: fine-tune MobileNetV3-Small (pretrained ImageNet)
  - Optimizer: AdamW, lr 1e-3
  - Augmentation: RandomResizedCrop, ColorJitter, Rotation, GaussianBlur
  - 30-50 epochs, early stopping
- [ ] Target: top-1 accuracy > 92% trên test set
- [ ] Notebook 3: export PyTorch → ONNX → TFLite (INT8 quantization)
- [ ] Test inference time trên Android device (< 200ms)

**Deliverable:** `vnd_classifier.tflite` (~5MB) + confusion matrix + training report

## Tuần 5 — Module nhận diện tiền (Android)

- [ ] CameraX preview + ImageAnalysis use case
- [ ] Auto-capture khi: đủ sáng + đủ rõ + có vật thể rectangular
- [ ] TFLite Interpreter wrap trong `MoneyClassifier.kt`
- [ ] Pipeline: frame → resize → TFLite → softmax → label
- [ ] `NumberToVietnamese`: 200000 → "Hai trăm nghìn đồng"
- [ ] Confidence threshold < 70% → "Không nhận diện được"
- [ ] Haptic feedback xác nhận chụp
- [ ] Unit test cho NumberToVietnamese + integration test cho classifier

**Deliverable:** Tab "Đọc tiền" hoạt động end-to-end

## Tuần 6 — Module đọc menu (OCR)

- [ ] Tích hợp ML Kit Text Recognition v2
- [ ] Parser:
  - Gom block theo y-coordinate thành dòng
  - Regex giá: `(\d{1,3}([.,\s]?\d{3})*)\s*(k|nghìn|đồng|đ|₫|VNĐ)?`
  - Pair "tên món - giá"
- [ ] 2 chế độ đọc:
  - Đọc toàn bộ (TTS liệt kê)
  - Lướt từng món (vuốt trái/phải)
- [ ] Edge cases: menu không có giá, menu nhiều cột
- [ ] Lưu lịch sử 20 lần quét gần nhất (Room DB)

**Deliverable:** Tab "Đọc menu" hoạt động end-to-end

## Tuần 7 — Accessibility, TTS, Voice Command

- [ ] Audit toàn UI: contentDescription, focus order, touch target ≥ 64dp
- [ ] Color contrast ratio ≥ 7:1 (WCAG AAA)
- [ ] High-contrast mode (nền đen, chữ vàng)
- [ ] TtsManager interface + 2 engine (FPT, Android)
- [ ] Voice command service: "đọc tiền", "đọc menu", "đọc lại", "dừng"
- [ ] Shake-to-stop (panic stop TTS)
- [ ] Settings screen: tốc độ đọc, giọng, rung, contrast

**Deliverable:** App đầy đủ chức năng, test bằng TalkBack OK

## Tuần 8 — Test với user thật

- [ ] Mời 3-5 người khiếm thị test
- [ ] Đo:
  - Accuracy thực tế (tiền + menu)
  - Thời gian từ mở app → kết quả (target < 10s)
  - SUS score (System Usability Scale)
- [ ] Ghi nhận feedback → fix UX
- [ ] Iterate 1-2 vòng

**Deliverable:** Báo cáo UX test + danh sách fix

## Tuần 9 — Polish & tối ưu

- [ ] Fix bugs từ tuần 8
- [ ] Tối ưu memory (target peak RAM < 200MB)
- [ ] Tối ưu battery (camera background)
- [ ] Firebase Crashlytics
- [ ] Build release APK, ký key
- [ ] Test trên 3-5 thiết bị thật (low-end, mid, high)
- [ ] (Optional) Upload Play Store internal track

**Deliverable:** APK release v1.0

## Tuần 10 — Báo cáo & slide

- [ ] Báo cáo (~60-80 trang):
  - Chương 1: Tổng quan, động lực
  - Chương 2: Cơ sở lý thuyết (CNN, MobileNet, OCR, TTS)
  - Chương 3: Phân tích & thiết kế
  - Chương 4: Triển khai (dataset, train, app)
  - Chương 5: Kiểm thử & đánh giá
  - Chương 6: Kết luận, hướng phát triển
- [ ] Slide bảo vệ (~20 slide)
- [ ] Demo video 2-3 phút
- [ ] Q&A prep

**Deliverable:** Bộ hồ sơ bảo vệ hoàn chỉnh

---

## Risk & mitigation

| Risk | Mitigation |
|---|---|
| Dataset overfit | Heavy augmentation + chụp đa dạng nhiều người |
| OCR sai dấu tiếng Việt | ML Kit v2 hỗ trợ tốt; backup VietOCR |
| FPT.AI hết quota | Fallback Android TTS |
| Không tìm được user khiếm thị | Test sơ bộ bằng bịt mắt + TalkBack |
| Inference chậm máy yếu | INT8 quantization + GPU delegate |
