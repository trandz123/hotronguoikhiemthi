# Hỗ Trợ Người Khiếm Thị (Mắt AI)

> Đồ án tốt nghiệp — Ứng dụng Android hỗ trợ người khiếm thị đọc menu đồ ăn và nhận diện tiền VND bằng AI.

## Tổng quan

App Android dùng AI on-device giúp người khiếm thị:
- **Nhận diện tiền VND**: chụp ảnh → đọc to mệnh giá ("Hai trăm nghìn đồng")
- **Đọc menu đồ ăn**: chụp menu → OCR + parse → đọc danh sách món + giá
- **Mô tả vật thể xung quanh** (mở rộng)
- **Voice command**: điều khiển không cần chạm
- **Accessibility-first**: tối ưu cho TalkBack, cử chỉ đơn giản, haptic feedback

## Tech stack

| Layer | Công nghệ |
|---|---|
| Mobile | Android Native (Kotlin), Min SDK 26 |
| Camera | CameraX |
| AI tiền | TensorFlow Lite + MobileNetV3-Small (custom train) |
| OCR | Google ML Kit Text Recognition v2 |
| TTS | FPT.AI TTS (cloud) + fallback Android TTS |
| Voice | Android SpeechRecognizer |
| DI | Hilt |
| Async | Kotlin Coroutines + Flow |
| Train model | Google Colab, PyTorch → ONNX → TFLite |

## Cấu trúc thư mục

```
hotronguoikhiemthi/
├── app/                    # Android app
│   └── src/main/
│       ├── kotlin/com/.../
│       │   ├── ui/         # Các màn hình
│       │   ├── ml/         # TFLite + OCR
│       │   ├── tts/        # TTS engines
│       │   ├── voice/      # Voice command
│       │   └── di/         # Hilt modules
│       └── assets/ml/      # model .tflite + labels
├── ml-training/            # Notebook + script train model
│   ├── notebooks/
│   ├── dataset/            # gitignored
│   └── models/             # gitignored
└── docs/
    ├── PLAN.md             # Plan chi tiết 10 tuần
    └── FEATURES.md         # Mô tả chức năng & flow user
```

## Trạng thái dự án

🚧 **Đang khởi tạo** — xem [docs/PLAN.md](docs/PLAN.md) để biết roadmap.

## Tác giả

Đồ án tốt nghiệp 2026.
