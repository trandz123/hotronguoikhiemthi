# Mắt AI — Ứng dụng hỗ trợ người khiếm thị

> Đồ án tốt nghiệp. Ứng dụng Android giúp người khiếm thị **nhận diện tiền VND** và **đọc menu nhà hàng** bằng AI, điều khiển hoàn toàn bằng cử chỉ + giọng nói (dùng được khi nhắm mắt).

Phiên bản hiện tại: **v0.10.1** · `minSdk 26` · `targetSdk 36` · Kotlin + Jetpack Compose.

---

## 1. Tổng quan

Mắt AI giải quyết hai nhu cầu hằng ngày mà người khiếm thị khó tự làm:

| Chức năng | Mô tả ngắn | AI sử dụng |
|---|---|---|
| 💵 **Đọc tiền** | Đưa tờ tiền vào camera → app đọc to mệnh giá, cộng dồn nhiều tờ ra tổng tiền | YOLOv10n chạy **on-device** (TFLite) |
| 📋 **Đọc menu** | Chụp ảnh menu → app đọc danh sách món + giá, cho chọn món và tính tổng | VLM **Llama 4 Scout** qua Groq Cloud |
| 🗣️ **Lệnh giọng nói** | Giữ phím tăng âm lượng 1,5 giây rồi nói lệnh | Android `SpeechRecognizer` (vi-VN) |
| ⚙️ **Cài đặt & 🕑 Lịch sử** | Chỉnh tốc độ/giọng đọc, xem lại 20 lần quét gần nhất | DataStore + Room |

Triết lý thiết kế: **accessibility-first**. Mọi thao tác chính làm được mà không cần nhìn — bằng cử chỉ vuốt/chạm trên toàn màn hình, phản hồi bằng giọng nói (TTS) và rung (haptics).

---

## 2. Công nghệ sử dụng

### 2.1 Nền tảng & kiến trúc
- **Ngôn ngữ:** Kotlin (JVM target 17)
- **UI:** Jetpack Compose + Material 3
- **Kiến trúc:** MVVM một chiều — `Screen (Compose)` ⇄ `ViewModel (StateFlow)` → `Repository / ML / TTS`
- **DI:** Hilt (Dagger)
- **Bất đồng bộ:** Kotlin Coroutines + Flow
- **Điều hướng:** Navigation Compose

### 2.2 AI / Machine Learning
- **Nhận diện tiền:** **YOLOv10n** (object detection) chuyển sang **TensorFlow Lite**, chạy hoàn toàn trên máy — không cần mạng.
  - Input `[1, 640, 640, 3]` FLOAT32, chuẩn hoá pixel/255.
  - Output `[1, MAX_DET, 6]` = `[x1, y1, x2, y2, conf, class_id]`, đã NMS + sắp theo confidence giảm dần.
  - Ngưỡng chấp nhận `MIN_CONFIDENCE = 0.70`.
  - File model: `app/src/main/assets/ml/vnd_yolov10n.tflite` (+ `vnd_yolov10n_labels.txt`).
- **Đọc menu:** **Vision-Language Model** `meta-llama/llama-4-scout-17b-16e-instruct` qua **Groq Cloud**.
  - Gửi thẳng **ảnh** (resize ≤1024px, JPEG q85, base64) + prompt → nhận JSON `{"items":[{"name","price"}]}`.
  - Dùng VLM nhìn ảnh trực tiếp (thay vì OCR phẳng) để **ghép đúng món–giá theo vị trí cột**.
- **Camera:** CameraX (`ImageAnalysis` cho luồng tiền real-time, `ImageCapture` cho luồng menu).

### 2.3 Đầu ra giọng nói (TTS) & giọng nói vào (STT)
- **TTS:** `RoutedTtsEngine` định tuyến thông minh giữa **FPT.AI TTS** (giọng Việt tự nhiên, cần mạng) và **Android TextToSpeech** (dự phòng offline).
- **STT / lệnh giọng nói:** Android `SpeechRecognizer` ngôn ngữ `vi-VN`.

### 2.4 Lưu trữ
- **Room** — lịch sử quét (`ScanHistoryEntity`).
- **DataStore Preferences** — cài đặt người dùng (tốc độ, giọng, tương phản, các toggle).

### 2.5 Quản lý khoá API (bí mật)
Các key đọc từ `local.properties` (đã gitignore) → `BuildConfig`:
```properties
groq.api.key=...      # Groq Cloud (đọc menu) — bắt buộc cho chức năng menu
fpt.tts.api.key=...   # FPT.AI TTS (giọng Việt) — tuỳ chọn, thiếu thì fallback Android TTS
gemini.api.key=...    # legacy, không còn dùng ở luồng chính
```

---

## 3. Kiến trúc tổng thể

```
┌──────────────────────────────────────────────────────────────┐
│                     Tầng giao diện (Compose)                   │
│  MoneyScreen · MenuScreen · SettingsScreen · HistoryScreen    │
│  HomeScreen · AppNavHost · gesture (vuốt/chạm) + haptics      │
└───────────────┬──────────────────────────────────────────────┘
                │ StateFlow / sự kiện cử chỉ
┌───────────────▼──────────────────────────────────────────────┐
│                       Tầng ViewModel (MVVM)                    │
│  MoneyViewModel · MenuViewModel · SettingsViewModel ·         │
│  HistoryViewModel · MainViewModel (điều phối voice/shake)     │
└───────┬──────────────┬──────────────┬───────────────┬────────┘
        │              │              │               │
┌───────▼─────┐ ┌──────▼──────┐ ┌─────▼──────┐ ┌──────▼───────┐
│  ML / AI    │ │     TTS     │ │   Data     │ │    Voice     │
│ YOLOv10n    │ │ RoutedTts → │ │ Room (hist)│ │ SpeechRecog. │
│ (on-device) │ │  FPT/Android│ │ DataStore  │ │ ShakeDetector│
│ Groq VLM    │ │             │ │ (settings) │ │              │
└─────────────┘ └─────────────┘ └────────────┘ └──────────────┘
```

Sơ đồ chi tiết (PNG + nguồn `.drawio`/`.puml`) trong [`docs/diagrams/`](docs/diagrams):
`01_kientruc` · `02_luong_tien` · `03_luong_menu` · `04_usecase` · `05_tuantu` · `06_trienkhai`.

---

## 4. Các luồng hoạt động

### 4.0 Khởi động & điều hướng
- App mở thẳng vào **MenuScreen** (`startDestination = Route.Menu`) — bỏ qua màn Home vì người khiếm thị không "chọn nút" được; đổi chế độ bằng **vuốt dọc** hoặc **lệnh giọng nói**.
- `MainActivity` lắng nghe: lắc máy (`ShakeDetector` → dừng TTS) và giữ phím Volume Up ≥1,5s (→ nghe lệnh giọng nói).
- Lệnh giọng nói/lắc được bắn qua `NavEventBus` để `AppNavHost` điều hướng (`GoMoney`, `GoMenu`, `GoSettings`, `GoHistory`, `GoHome`, `Repeat`).

### 4.1 Luồng đọc tiền (real-time, on-device)

UX "gương" với menu: **nhìn liên tục → đọc mệnh giá → vuốt xuống để chọn**.

```
CameraX ImageAnalysis (KEEP_ONLY_LATEST)
   │  mỗi frame, nếu chưa bận (busy flag)
   ▼
LiveMoneyAnalyzer: ImageProxy → Bitmap → xoay đúng chiều
   ▼
Yolov10MoneyDetector.classify()  (TFLite, ~on-device)
   ▼  MoneyResult.Recognized(mệnhGiá, conf≥0.70)
MoneyViewModel.onLiveDetection()
   ├─ lọc ổn định (REQUIRED_STABLE_FRAMES) → tờ mới?
   │     → TTS "Tờ năm mươi nghìn" + rung nhẹ (hapticTick)
   │
   ├─ Vuốt xuống  → selectCurrent(): cộng tờ vào tổng,
   │     bật awaitingClear (chống đếm trùng), rung mạnh,
   │     TTS "Đã chọn …, tổng N tờ, …"
   ├─ Chạm đôi    → readSelected(): đọc lại tổng đã chọn
   ├─ Giữ lâu     → scanAgain(): xoá hết, đếm lại
   └─ Vuốt lên    → switchToMenu(): lưu tổng vào History rồi sang menu
```

Chống đếm trùng (`anti-double-count`): sau khi chọn một tờ **cùng mệnh giá**, phải thấy ≥ `REQUIRED_CLEAR_FRAMES` frame "không có tiền" mới cho đếm tờ tiếp theo (ép người dùng nhấc tờ cũ ra). Mệnh giá khác thì bỏ qua ràng buộc này.

### 4.2 Luồng đọc menu (cloud VLM)

```
MenuScreen (Idle) → tự đếm "Ba… Hai… Một…" (audio countdown 3s)
   ▼
doCapture(): ImageCapture → Bitmap
   ▼  (audio cue "Đang phân tích menu…" nếu >1,5s)
GroqMenuAnalyzer.parseMenuImage()
   │  Bitmap → resize ≤1024px JPEG → base64
   │  POST Groq /chat/completions (Llama 4 Scout Vision, temp 0.1)
   │  retry backoff khi gặp 429/503/rate_limit
   ▼  JSON {"items":[{name, price}]}
MenuViewModel.state = Loaded(items)
   ▼  TTS "Menu có N món. Món một: …" + hướng dẫn cử chỉ
   ├─ Vuốt phải/trái → nextItem()/prevItem()
   ├─ Vuốt xuống     → selectCurrent(): chọn món, cộng tổng
   ├─ Chạm đôi       → readSelected(): đọc lại danh sách đã chọn + tổng
   ├─ Giữ lâu        → scanAgain(): quét lại menu
   └─ Vuốt lên       → switchToMoney(): sang chế độ đếm tiền
```

Xử lý lỗi: hết quota (429) / sai key (403) / mất mạng → TTS thông báo cụ thể, gợi ý "vuốt lên thử lại". Menu rỗng → "Không nhận diện được món nào".

### 4.3 Luồng TTS (định tuyến giọng đọc)
`RoutedTtsEngine.speak()`:
1. Đọc giọng đã chọn trong Settings.
2. Nếu giọng dùng FPT **và** có API key **và** đang online → gọi **FPT.AI** (giọng Việt tự nhiên).
3. Ngược lại (không có key) → **Android TTS**. Khi offline mà đã chọn FPT → im lặng còn hơn đọc sai accent.

### 4.4 Luồng lệnh giọng nói
Giữ Volume Up ≥1,5s → `VoiceCommandService.listenOnce()` (vi-VN) → `VoiceCommand.parse()` so khớp từ khoá (contains) → `MainViewModel.onVoiceCommand()`:

| Nói | Hành động |
|---|---|
| "đọc tiền" | sang chế độ tiền |
| "đọc menu" / "thực đơn" | sang chế độ menu |
| "đọc lại" / "nhắc lại" | phát lại |
| "dừng" / "stop" | dừng TTS |
| "nhanh hơn" / "chậm hơn" | chỉnh tốc độ đọc ±20% |
| "cài đặt" / "lịch sử" / "thoát" | mở màn tương ứng |

### 4.5 Luồng lịch sử & cài đặt
- Mỗi lần đọc menu hoặc chốt tổng tiền (khi rời màn) → `HistoryRepository.record(ScanType, text)` vào Room. `HistoryScreen` hiển thị tối đa 20 mục, cho **nghe lại** / **xoá hết**.
- `SettingsScreen` (lưu qua DataStore): tốc độ đọc (0.5×–2×), giọng đọc (`TtsVoice`), độ tương phản (`ContrastMode`), và 3 toggle: rung phản hồi, tự động chụp tiền, điều khiển bằng giọng nói.

---

## 5. Bản đồ cử chỉ (toàn màn hình)

| Cử chỉ | Màn tiền | Màn menu |
|---|---|---|
| Vuốt **xuống** | Chọn tờ đang thấy | Chọn món hiện tại |
| Vuốt **lên** | Sang chế độ menu | Sang chế độ tiền |
| Vuốt **phải/trái** | — | Món kế / món trước |
| **Chạm đôi** | Đọc lại tổng đã chọn | Đọc lại danh sách đã chọn |
| **Giữ lâu** | Xoá, đếm lại | Quét lại menu |
| **Lắc máy** | Dừng TTS | Dừng TTS |
| Giữ **Volume Up** 1,5s | Lệnh giọng nói | Lệnh giọng nói |

Phản hồi rung: `hapticTick` khi thấy tờ mới / đổi thao tác, `hapticStrong` khi chọn thành công.

---

## 6. Cấu trúc mã nguồn

```
app/src/main/
├── kotlin/com/trandz123/hotronguoikhiemthi/
│   ├── MainActivity.kt          # entry, phím Volume, shake → voice/stop
│   ├── MainViewModel.kt         # điều phối voice command + NavEvent
│   ├── ml/                      # AI
│   │   ├── Yolov10MoneyDetector.kt   # TFLite money (production)
│   │   ├── TfliteMoneyClassifier.kt  # MobileNetV3 (fallback)
│   │   ├── FakeMoneyClassifier.kt    # fallback UI test
│   │   ├── GroqMenuAnalyzer.kt       # VLM đọc menu (production)
│   │   ├── GeminiMenuAnalyzer.kt     # legacy
│   │   └── MoneyClassifier.kt        # interface + nhãn mệnh giá
│   ├── tts/                     # RoutedTts → FPT / Android
│   ├── voice/                   # SpeechRecognizer + ShakeDetector
│   ├── ui/
│   │   ├── money/  menu/  settings/  history/  home/
│   │   ├── camera/ # CameraPreviewView, LiveMoneyAnalyzer, FrameQuality
│   │   ├── nav/    # AppNavHost, Route, NavEventBus
│   │   └── theme/
│   ├── data/
│   │   ├── history/  # Room: Entity, Dao, Database, Repository
│   │   └── settings/ # DataStore: UserPreferences, PreferencesRepository
│   ├── di/         # Hilt: AppModule, MlModule, TtsModule
│   └── util/       # NumberToVietnamese, Haptics
└── assets/ml/      # vnd_yolov10n.tflite (+labels), vnd_classifier.tflite (fallback)

docs/diagrams/      # 6 sơ đồ PNG + nguồn .drawio/.puml
tools/              # script Python sinh báo cáo Word + render sơ đồ
```

**Chuỗi fallback model tiền** (`MlModule`): `Yolov10MoneyDetector` → `TfliteMoneyClassifier` → `FakeMoneyClassifier` — đập file model vào asset là tự nâng cấp, không cần sửa code.

---

## 7. Build & chạy

```powershell
# 1. Tạo local.properties (gitignored) với khoá API
#    groq.api.key=...   (bắt buộc cho menu)
#    fpt.tts.api.key=... (tuỳ chọn, giọng Việt)

# 2. Build & cài lên máy/emulator
.\gradlew.bat installDebug
```

Yêu cầu quyền: **Camera** (đọc tiền/menu) và **Micro** (lệnh giọng nói — xin khi dùng lần đầu).

> Nếu thiếu `groq.api.key` → chức năng menu báo lỗi cấu hình. Nếu thiếu model TFLite → tự fallback (cuối cùng là `FakeMoneyClassifier` để test UI).

---

## 8. Tài liệu kèm theo
- [`docs/diagrams/`](docs/diagrams) — sơ đồ kiến trúc, luồng, use case, tuần tự, triển khai.
- `tools/gen_report2.py` — sinh báo cáo đồ án (Word) có hình.
- `tools/gen_diagrams.py` — render lại 6 sơ đồ PNG.

---

*Đồ án tốt nghiệp 2026 — "Mắt AI".*
