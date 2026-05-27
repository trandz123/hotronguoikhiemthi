# Tài liệu cho assets

## `app/src/main/assets/ml/`

Thư mục này chứa TFLite model + label files của module nhận diện tiền.

Các file sẽ commit vào đây sau khi train xong (tuần 3-4 trong [PLAN.md](PLAN.md)):

| File | Mô tả |
|---|---|
| `vnd_classifier.tflite` | Model MobileNetV3-Small fine-tuned, INT8 quantize, ~5 MB |
| `vnd_labels.txt` | 10 nhãn, mỗi dòng 1 mệnh giá (xem dưới) |

### Nội dung `vnd_labels.txt`

```
500000
200000
100000
50000
20000
10000
5000
2000
1000
unknown
```

### Lưu ý

- **KHÔNG** commit checkpoint trung gian (`*.pth`, `*.pt`, `*.onnx`) — chúng đã bị `.gitignore` chặn.
- Chỉ commit file `.tflite` cuối cùng vào `app/src/main/assets/ml/`.
- Asset trong thư mục này **không bị nén** (`noCompress.add("tflite")` trong `app/build.gradle.kts`) để `Interpreter` có thể `mmap` trực tiếp, tăng tốc load model lên ~3x.
