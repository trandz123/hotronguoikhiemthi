"""
Test YOLOv10n money detector voi webcam laptop.

Pipeline khop voi Android (Yolov10MoneyDetector):
  - Resize 640x640 BILINEAR
  - FP32 input, normalize / 255
  - Output [1, 300, 6] -- [x1, y1, x2, y2, conf, class_id]
    NMS built-in, da sort theo conf giam dan, padding 0 neu < 300 det.

Chay:
    python ml-training/scripts/webcam_test_yolov10.py

Phim:
    q  thoat
    s  luu khung hinh hien tai (debug)
"""

from __future__ import annotations

import sys
import time
from pathlib import Path

import cv2
import numpy as np

try:
    from tflite_runtime.interpreter import Interpreter
except ImportError:
    from tensorflow.lite.python.interpreter import Interpreter  # type: ignore

ROOT = Path(__file__).resolve().parents[2]
MODEL_PATH = ROOT / "app" / "src" / "main" / "assets" / "ml" / "vnd_yolov10n.tflite"
LABELS_PATH = ROOT / "app" / "src" / "main" / "assets" / "ml" / "vnd_yolov10n_labels.txt"

INPUT_SIZE = 640
CONFIDENCE_THRESHOLD = 0.5  # khop voi MIN_CONFIDENCE Kotlin (0.70 prod, ha thap khi debug)


def load_labels(path: Path) -> list[str]:
    return [ln.strip() for ln in path.read_text(encoding="utf-8").splitlines() if ln.strip()]


def format_vnd(label: str) -> str:
    try:
        return f"{int(label):,}d".replace(",", ".")
    except ValueError:
        return label


def preprocess(frame_bgr: np.ndarray) -> np.ndarray:
    rgb = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2RGB)
    resized = cv2.resize(rgb, (INPUT_SIZE, INPUT_SIZE), interpolation=cv2.INTER_LINEAR)
    arr = resized.astype(np.float32) / 255.0
    return np.expand_dims(arr, axis=0)


def main() -> int:
    if not MODEL_PATH.exists():
        print(f"[ERR] Model not found: {MODEL_PATH}")
        return 1
    if not LABELS_PATH.exists():
        print(f"[ERR] Labels not found: {LABELS_PATH}")
        return 1

    labels = load_labels(LABELS_PATH)
    print(f"[OK] Loaded {len(labels)} labels: {labels}")

    interpreter = Interpreter(model_path=str(MODEL_PATH))
    interpreter.allocate_tensors()
    inp = interpreter.get_input_details()[0]
    out = interpreter.get_output_details()[0]
    print(f"[OK] Input:  shape={inp['shape']} dtype={inp['dtype']}")
    print(f"[OK] Output: shape={out['shape']} dtype={out['dtype']}")

    cap = cv2.VideoCapture(0, cv2.CAP_DSHOW)
    if not cap.isOpened():
        print("[ERR] Khong mo duoc webcam")
        return 1
    cap.set(cv2.CAP_PROP_FRAME_WIDTH, 1280)
    cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 720)

    print("[OK] Bat dau scan. Q de thoat, S de luu frame.")
    last_t = time.time()
    fps = 0.0

    while True:
        ok, frame = cap.read()
        if not ok:
            print("[WARN] Khong doc duoc frame")
            break

        frame_h, frame_w = frame.shape[:2]
        x = preprocess(frame)
        interpreter.set_tensor(inp["index"], x)
        interpreter.invoke()
        # Output shape [1, 300, 6] = [x1, y1, x2, y2, conf, class_id]
        detections = interpreter.get_tensor(out["index"])[0]

        # Filter detection co conf >= threshold (YOLO da sort, da NMS)
        valid = [d for d in detections if d[4] >= CONFIDENCE_THRESHOLD]

        now = time.time()
        fps = 0.9 * fps + 0.1 * (1.0 / max(now - last_t, 1e-6))
        last_t = now

        # Ve bounding box len frame goc (scale tu 640 -> frame size)
        scale_x = frame_w / INPUT_SIZE
        scale_y = frame_h / INPUT_SIZE
        for det in valid:
            x1, y1, x2, y2, conf, cls = det
            cls_idx = int(cls)
            if cls_idx < 0 or cls_idx >= len(labels):
                continue
            label_text = format_vnd(labels[cls_idx])
            px1 = int(x1 * scale_x)
            py1 = int(y1 * scale_y)
            px2 = int(x2 * scale_x)
            py2 = int(y2 * scale_y)
            color = (0, 255, 0)
            cv2.rectangle(frame, (px1, py1), (px2, py2), color, 2)
            text = f"{label_text} {conf*100:.1f}%"
            (tw, th), _ = cv2.getTextSize(text, cv2.FONT_HERSHEY_SIMPLEX, 0.7, 2)
            cv2.rectangle(frame, (px1, py1 - th - 10), (px1 + tw + 4, py1), color, -1)
            cv2.putText(frame, text, (px1 + 2, py1 - 6),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 0, 0), 2, cv2.LINE_AA)

        # Status bar
        cv2.rectangle(frame, (0, 0), (frame_w, 50), (0, 0, 0), -1)
        status = f"Detected: {len(valid)}  |  FPS {fps:4.1f}  |  conf>={CONFIDENCE_THRESHOLD}"
        cv2.putText(frame, status, (12, 32),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.7, (200, 220, 255), 2, cv2.LINE_AA)

        # Top detection summary (top-3 conf cao nhat)
        top3 = detections[:3]
        for k, det in enumerate(top3):
            if det[4] < 0.05:
                break
            cls_idx = int(det[5])
            lbl = labels[cls_idx] if 0 <= cls_idx < len(labels) else "?"
            cv2.putText(
                frame,
                f"top{k+1}: {format_vnd(lbl):>15s}  {det[4]*100:5.1f}%",
                (12, 70 + k * 22),
                cv2.FONT_HERSHEY_SIMPLEX, 0.55, (200, 220, 255), 1, cv2.LINE_AA,
            )

        cv2.imshow("VND YOLOv10 webcam test", frame)

        key = cv2.waitKey(1) & 0xFF
        if key in (ord("q"), 27):
            break
        if key == ord("s"):
            out_path = ROOT / "ml-training" / "scripts" / f"yolo_snap_{int(now)}.jpg"
            cv2.imwrite(str(out_path), frame)
            print(f"[OK] Saved {out_path}")

    cap.release()
    cv2.destroyAllWindows()
    return 0


if __name__ == "__main__":
    sys.exit(main())
