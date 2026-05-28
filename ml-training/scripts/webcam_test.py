"""
Test model nhan dien tien VND voi webcam laptop.

Pipeline khop voi Android (TfliteMoneyClassifier):
  - Resize 224x224 BILINEAR
  - FP32 input
  - ImageNet normalization: (pixel - mean*255) / (std*255)

Chay:
    python ml-training/scripts/webcam_test.py

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
MODEL_PATH = ROOT / "app" / "src" / "main" / "assets" / "ml" / "vnd_classifier.tflite"
LABELS_PATH = ROOT / "app" / "src" / "main" / "assets" / "ml" / "vnd_labels.txt"

INPUT_SIZE = 224
IMAGENET_MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32) * 255.0
IMAGENET_STD = np.array([0.229, 0.224, 0.225], dtype=np.float32) * 255.0
CONFIDENCE_THRESHOLD = 0.30  # softmax prob threshold (low for debug)


def softmax(x: np.ndarray) -> np.ndarray:
    x = x - x.max()
    e = np.exp(x)
    return e / e.sum()


def load_labels(path: Path) -> list[str]:
    return [ln.strip() for ln in path.read_text(encoding="utf-8").splitlines() if ln.strip()]


def format_vnd(label: str) -> str:
    if label == "unknown":
        return "Khong xac dinh"
    try:
        return f"{int(label):,}d".replace(",", ".")
    except ValueError:
        return label


def preprocess(frame_bgr: np.ndarray) -> np.ndarray:
    rgb = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2RGB)
    resized = cv2.resize(rgb, (INPUT_SIZE, INPUT_SIZE), interpolation=cv2.INTER_LINEAR)
    arr = resized.astype(np.float32)
    arr = (arr - IMAGENET_MEAN) / IMAGENET_STD
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
    print(f"[OK] Model input: shape={inp['shape']} dtype={inp['dtype']}")
    print(f"[OK] Model output: shape={out['shape']} dtype={out['dtype']}")

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

        x = preprocess(frame)
        interpreter.set_tensor(inp["index"], x)
        interpreter.invoke()
        logits = interpreter.get_tensor(out["index"])[0]
        probs = softmax(logits)

        top3_idx = np.argsort(probs)[::-1][:3]
        top_idx = int(top3_idx[0])
        top_conf = float(probs[top_idx])
        top_label = labels[top_idx] if top_idx < len(labels) else "?"

        now = time.time()
        fps = 0.9 * fps + 0.1 * (1.0 / max(now - last_t, 1e-6))
        last_t = now

        if top_conf >= CONFIDENCE_THRESHOLD and top_label != "unknown":
            text = f"{format_vnd(top_label)}  ({top_conf*100:.1f}%)"
            color = (0, 255, 0)
        else:
            text = f"Khong ro  (top={format_vnd(top_label)} {top_conf*100:.1f}%)"
            color = (0, 165, 255)

        # Debug overlay: hien top-3 prediction de soi accuracy
        cv2.rectangle(frame, (0, 0), (frame.shape[1], 160), (0, 0, 0), -1)
        cv2.putText(frame, text, (12, 42), cv2.FONT_HERSHEY_SIMPLEX, 1.1, color, 2, cv2.LINE_AA)
        for k, i in enumerate(top3_idx):
            lbl = labels[int(i)] if int(i) < len(labels) else "?"
            cv2.putText(
                frame,
                f"top{k+1}: {format_vnd(lbl):>15s}  {probs[int(i)]*100:5.1f}%",
                (12, 80 + k * 26),
                cv2.FONT_HERSHEY_SIMPLEX, 0.6, (200, 220, 255), 1, cv2.LINE_AA,
            )
        cv2.putText(frame, f"FPS {fps:4.1f}", (frame.shape[1] - 140, 42),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.7, (200, 200, 200), 1, cv2.LINE_AA)
        cv2.imshow("VND money scan - webcam test", frame)

        key = cv2.waitKey(1) & 0xFF
        if key in (ord("q"), 27):
            break
        if key == ord("s"):
            out_path = ROOT / "ml-training" / "scripts" / f"snap_{int(now)}.jpg"
            cv2.imwrite(str(out_path), frame)
            print(f"[OK] Saved {out_path}")

    cap.release()
    cv2.destroyAllWindows()
    return 0


if __name__ == "__main__":
    sys.exit(main())
