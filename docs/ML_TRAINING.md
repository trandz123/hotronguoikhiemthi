# Hướng dẫn ML training & eval

Folder `ml-training/` chứa 2 notebook Colab để:
1. **Train model nhận diện tiền** (`vnd_classifier.tflite`) từ dataset Roboflow.
2. **Eval parser menu** trên dataset Viet-Menu để đo accuracy + tìm case sai.

Cả 2 notebook chạy trên Google Colab free (GPU T4 cho notebook 01).

---

## 📦 Notebook 01 — Train money classifier

**File:** `ml-training/notebooks/01_money_train.ipynb`
**Dataset:** [Vietnamese Currency Detector (Roboflow Universe)](https://universe.roboflow.com/cv-aal82/vietnamese-currency-detector) — 2569 ảnh
**Output:** `vnd_classifier.tflite` (~5 MB INT8) + `vnd_labels.txt`

### Bước thực hiện

1. **Đăng ký Roboflow** (free) tại https://app.roboflow.com → Settings → API Keys → copy "Private API Key"

2. **Mở notebook trên Colab:**
   - Vào https://colab.research.google.com
   - File → Upload notebook → chọn `01_money_train.ipynb`
   - Runtime → Change runtime type → **T4 GPU** (Free tier có)

3. **Paste API key:** cell #4, sửa dòng:
   ```python
   ROBOFLOW_API_KEY = 'YOUR_API_KEY_HERE'  # ← paste vào đây
   ```

4. **Verify class mapping** (cell #5):
   - Cell sẽ in ra Roboflow class names + mapping sang VND
   - Kiểm tra bằng mắt, nếu Roboflow đặt tên class lạ (vd `bill_500k` thay vì `500000`) → sửa hàm `roboflow_name_to_vnd`

5. **Runtime → Run all** → đợi ~30-60 phút (chủ yếu là 30 epoch training)

6. **Download output:**
   - Files panel (icon thư mục bên trái) → `/content/vnd_classifier.tflite`
   - Right-click → Download
   - Tương tự với `/content/vnd_labels.txt`

7. **Drop vào app:**
   ```
   D:/hotronguoikhiemthi/app/src/main/assets/ml/
   ├── vnd_classifier.tflite   ← thay file dummy
   └── vnd_labels.txt
   ```

8. **Rebuild:**
   ```powershell
   .\gradlew.bat installDebug
   ```

Đối tượng `TfliteMoneyClassifier` sẽ tự pick up model thay vì fallback `FakeMoneyClassifier`. Verify bằng logcat: filter tag `MlModule` → thấy `TFLite model loaded from ml/vnd_classifier.tflite`.

### Target

- Val accuracy **> 92%** (theo plan tuần 3-4 trong `docs/PLAN.md`)
- Inference time **< 200 ms** trên Snapdragon 6-series

Nếu val_acc < 80% → check:
- Roboflow class name mapping (cell #5) có sai class nào không
- Số sample per class có cân không (cell #6 print count)
- Augmentation có quá mạnh không

### Class order quan trọng

Output 10 logits của model phải khớp THỨ TỰ `MONEY_LABELS` trong `app/.../ml/MoneyClassifier.kt`:

```
index 0 → 500000
index 1 → 200000
index 2 → 100000
index 3 → 50000
index 4 → 20000
index 5 → 10000
index 6 → 5000
index 7 → 2000
index 8 → 1000
index 9 → unknown
```

Notebook đã hardcode order này (`OUTPUT_LABELS`) + custom `FixedOrderImageFolder` để PyTorch không alphabetical sort class. **Không sửa thứ tự này** nếu không sửa luôn Kotlin.

---

## 📊 Notebook 02 — Eval menu parser

**File:** `ml-training/notebooks/02_menu_eval.ipynb`
**Dataset:** [5CD-AI/Viet-Menu-gemini-VQA (Hugging Face)](https://huggingface.co/datasets/5CD-AI/Viet-Menu-gemini-VQA) — 840 ảnh menu Việt + ground truth dish list + giá
**Output:** `menu_eval_summary.csv` (precision/recall/F1) + `menu_eval_results.csv` (per-sample)

### Bước thực hiện

1. **Mở Colab** → Upload `02_menu_eval.ipynb`
2. Không cần GPU (eval pure-CPU, regex matching)
3. **Runtime → Run all** → ~5-10 phút
4. **Inspect output:**
   - Cell #5 in metric tổng hợp (dish precision/recall/F1, price accuracy)
   - Cell #6 in 10 case fail tiêu biểu → đọc để tìm pattern lỗi
5. **Cải tiến parser** dựa trên failure mode:
   - Sửa file `ml-training/scripts/menu_parser.py` để verify nhanh trên Colab
   - **Sync** lại logic về `app/src/main/kotlin/.../ml/MenuOcrParser.kt` (production)
6. **Chạy lại notebook** → so sánh F1 trước/sau

### Output cho báo cáo đồ án

Trong báo cáo (chương "Kiểm thử & đánh giá"), paste bảng:

```
Số ảnh menu đánh giá:        840
Dish detection precision:    0.X
Dish detection recall:       0.X
Dish detection F1:           0.X
Price extraction accuracy:   0.X
```

Kèm 3-5 case fail tiêu biểu + giải thích nguyên nhân.

---

## 🔄 Quy trình end-to-end

Khi sửa parser logic:

```
1. Sửa ml-training/scripts/menu_parser.py
2. Chạy notebook 02 trên Colab → confirm F1 tăng
3. Sửa app/src/main/kotlin/.../ml/MenuOcrParser.kt (đồng bộ logic)
4. Build app → test trên menu thật
5. Commit cả 2 file trong 1 commit
```

---

## ⚠️ Limitation đã biết

### Notebook 01

- **Dataset Roboflow chỉ có ảnh tiền** → class `unknown` được tăng bằng CIFAR-10 (objects ngẫu nhiên). Real-world có thể vẫn confuse với đồ vật phẳng/giấy.
- **Inference time chưa benchmark trên thiết bị thật.** Sau khi drop file vào app, dùng `adb logcat` đo thời gian từ `classify()` start → return.
- **INT8 quantization** đôi khi fail nếu model dùng op không support → fallback FP32 (~5-6 MB thay vì ~1.5 MB).

### Notebook 02

- Dataset Viet-Menu có raw OCR text từ **Gemini/PPOCR**, không phải ML Kit → kết quả eval khác một chút với production. Vẫn validate parser logic OK.
- Ground truth `Danh sách món` đôi khi gộp 2 món thành 1 → recall thấp nhân tạo.

---

## 📁 File structure

```
ml-training/
├── requirements.txt              # pip deps (cài trên Colab)
├── notebooks/
│   ├── 01_money_train.ipynb      # train MobileNetV3 → TFLite
│   └── 02_menu_eval.ipynb        # eval parser trên Viet-Menu
└── scripts/
    └── menu_parser.py            # Python port của MenuOcrParser.kt
```
