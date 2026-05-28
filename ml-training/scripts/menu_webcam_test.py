"""
Test scan menu OCR voi webcam laptop, dung EasyOCR + menu_parser.py.

Pipeline:
  1. Webcam preview (OpenCV).
  2. Bam 's' -> chup 1 frame, CLAHE preprocess + 1.5x upscale, chay EasyOCR.
  3. Cluster bbox theo y-coord -> dong menu.
  4. Parse moi dong qua menu_parser (regex gia VND).
  5. Loc header ("TEN", "GIA", ...) + duplicate.
  6. Render ket qua tieng Viet bang PIL (cv2.putText khong support Unicode dau).

Phim:
    s   scan 1 frame
    m   merge mode: chup nhieu frame -> gop ket qua (giam noise)
    r   reset merge buffer
    q   thoat

Run:
    python ml-training/scripts/menu_webcam_test.py
"""

from __future__ import annotations

import sys
import time
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont

SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))
import menu_parser  # noqa: E402

try:
    import easyocr
except ImportError:
    print("[ERR] Chua cai easyocr. Chay: pip install easyocr")
    sys.exit(1)


# ---- Config ----
PRICE_REGEX_STRICT = menu_parser.PRICE_REGEX  # tai dung
HEADER_BLACKLIST = {
    "ten", "ten san pham", "ten mon", "ten mon an",
    "gia", "gia ban", "don gia", "thanh tien",
    "stt", "so luong", "sl", "don vi", "ghi chu",
    "menu", "thuc don", "danh sach mon",
}
ROW_CLUSTER_THRESHOLD = 0.6  # ti le chieu cao box de coi 2 box cung dong
MIN_TEXT_LEN = 2

# Try font path Windows (Arial mac dinh ho tro tieng Viet)
FONT_CANDIDATES = [
    "C:/Windows/Fonts/arial.ttf",
    "C:/Windows/Fonts/segoeui.ttf",
    "/System/Library/Fonts/Helvetica.ttc",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
]


def load_font(size: int) -> ImageFont.FreeTypeFont:
    for p in FONT_CANDIDATES:
        if Path(p).exists():
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def normalize_for_blacklist(s: str) -> str:
    s = s.lower().strip()
    # Bo dau tieng Viet co ban (chi de match blacklist header)
    repl = str.maketrans(
        "àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ",
        "aaaaaaaaaaaaaaaaaeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyyd",
    )
    return s.translate(repl)


def preprocess(frame_bgr: np.ndarray) -> np.ndarray:
    """CLAHE + grayscale + upscale 1.5x de OCR de nhan hon."""
    gray = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2GRAY)
    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
    enhanced = clahe.apply(gray)
    h, w = enhanced.shape
    upscaled = cv2.resize(enhanced, (int(w * 1.5), int(h * 1.5)), interpolation=cv2.INTER_CUBIC)
    # Tra ve BGR de EasyOCR happy
    return cv2.cvtColor(upscaled, cv2.COLOR_GRAY2BGR)


def cluster_rows(detections) -> list[list]:
    """Group EasyOCR detections theo y-coord (cung dong menu)."""
    if not detections:
        return []
    # detections: list of (bbox, text, conf) — bbox la 4 dinh
    boxes = []
    for bbox, text, conf in detections:
        ys = [p[1] for p in bbox]
        xs = [p[0] for p in bbox]
        y_center = sum(ys) / 4
        height = max(ys) - min(ys)
        x_left = min(xs)
        boxes.append({"y": y_center, "h": height, "x": x_left, "text": text, "conf": conf})

    boxes.sort(key=lambda b: b["y"])
    rows: list[list] = []
    current_row: list = []
    current_y = None
    for b in boxes:
        if current_y is None or abs(b["y"] - current_y) <= b["h"] * ROW_CLUSTER_THRESHOLD:
            current_row.append(b)
            current_y = b["y"] if current_y is None else (current_y + b["y"]) / 2
        else:
            rows.append(current_row)
            current_row = [b]
            current_y = b["y"]
    if current_row:
        rows.append(current_row)

    # Trong moi row, sap xep theo x (trai -> phai)
    for r in rows:
        r.sort(key=lambda b: b["x"])
    return rows


def rows_to_items(rows) -> list[menu_parser.MenuItem]:
    items: list[menu_parser.MenuItem] = []
    seen_keys = set()
    for row in rows:
        joined = " ".join(b["text"] for b in row).strip()
        if len(joined) < MIN_TEXT_LEN:
            continue
        norm = normalize_for_blacklist(joined)
        if any(h in norm for h in HEADER_BLACKLIST) and not PRICE_REGEX_STRICT.search(joined):
            continue
        item = menu_parser._parse_row(joined)
        if item is None:
            continue
        # Dedup theo (name, price)
        key = (item.name.lower().strip(), item.price_vnd)
        if key in seen_keys:
            continue
        seen_keys.add(key)
        items.append(item)
    return items


def format_vnd(n: int) -> str:
    return f"{n:,}d".replace(",", ".")


def render_results(frame_bgr: np.ndarray, items: list[menu_parser.MenuItem],
                   header: str = "Ket qua scan menu") -> np.ndarray:
    """Render danh sach mon + gia len anh, ho tro Unicode tieng Viet qua PIL."""
    img_rgb = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2RGB)
    pil = Image.fromarray(img_rgb)
    draw = ImageDraw.Draw(pil)
    font_h = load_font(28)
    font = load_font(22)

    pad = 12
    panel_w = 520
    panel_h = min(pil.height, 80 + len(items) * 32)
    draw.rectangle([(0, 0), (panel_w, panel_h)], fill=(0, 0, 0, 200))
    draw.text((pad, pad), header, font=font_h, fill=(0, 255, 100))

    y = pad + 40
    if not items:
        draw.text((pad, y), "Khong nhan dien duoc mon nao", font=font, fill=(255, 165, 0))
    else:
        for i, item in enumerate(items, 1):
            price_str = format_vnd(item.price_vnd) if item.has_price else "(?)"
            line = f"{i}. {item.name}  -  {price_str}"
            if len(line) > 60:
                line = line[:57] + "..."
            color = (255, 255, 255) if item.has_price else (200, 200, 200)
            draw.text((pad, y), line, font=font, fill=color)
            y += 30
            if y > pil.height - 20:
                break

    return cv2.cvtColor(np.array(pil), cv2.COLOR_RGB2BGR)


def merge_items(buffer: list[list[menu_parser.MenuItem]]) -> list[menu_parser.MenuItem]:
    """Gop nhieu lan scan -> chi giu mon xuat hien >=2 lan, hoac scan don le neu chi 1 lan."""
    if not buffer:
        return []
    if len(buffer) == 1:
        return buffer[0]
    counts: dict = {}
    sample: dict = {}
    for items in buffer:
        seen_this_scan = set()
        for it in items:
            key = (it.name.lower().strip(), it.price_vnd)
            if key in seen_this_scan:
                continue
            seen_this_scan.add(key)
            counts[key] = counts.get(key, 0) + 1
            sample[key] = it
    threshold = max(2, len(buffer) // 2)
    merged = [sample[k] for k, c in counts.items() if c >= threshold]
    merged.sort(key=lambda it: (it.price_vnd or 0))
    return merged


def main() -> int:
    print("[INFO] Loading EasyOCR (lan dau ~30s tai model)...")
    reader = easyocr.Reader(["vi", "en"], gpu=False)
    print("[OK] EasyOCR ready")

    cap = cv2.VideoCapture(0, cv2.CAP_DSHOW)
    if not cap.isOpened():
        print("[ERR] Khong mo duoc webcam")
        return 1
    cap.set(cv2.CAP_PROP_FRAME_WIDTH, 1280)
    cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 720)

    print("\n[OK] Phim:")
    print("    s   scan 1 frame")
    print("    m   merge mode: scan them frame moi, gop ket qua")
    print("    r   reset buffer merge")
    print("    q   thoat\n")

    last_render: np.ndarray | None = None
    merge_buffer: list[list[menu_parser.MenuItem]] = []
    status = "San sang. Giu menu thang truoc cam, bam 's' de scan."

    while True:
        ok, frame = cap.read()
        if not ok:
            break

        if last_render is not None:
            display = last_render.copy()
        else:
            display = frame.copy()

        cv2.rectangle(display, (0, display.shape[0] - 36), (display.shape[1], display.shape[0]),
                      (0, 0, 0), -1)
        cv2.putText(display, status, (12, display.shape[0] - 12),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 255, 255), 1, cv2.LINE_AA)
        cv2.imshow("Menu OCR scan - webcam test", display)

        key = cv2.waitKey(1) & 0xFF
        if key in (ord("q"), 27):
            break
        if key in (ord("s"), ord("m")):
            status = "Dang xu ly OCR..."
            cv2.imshow("Menu OCR scan - webcam test", display)
            cv2.waitKey(1)

            t0 = time.time()
            processed = preprocess(frame)
            detections = reader.readtext(processed, detail=1, paragraph=False)
            rows = cluster_rows(detections)
            items = rows_to_items(rows)
            dt = time.time() - t0

            if key == ord("m"):
                merge_buffer.append(items)
                merged = merge_items(merge_buffer)
                last_render = render_results(
                    frame, merged,
                    header=f"Merge scan ({len(merge_buffer)} lan) - {dt:.1f}s",
                )
                status = f"Merged {len(merged)} mon tu {len(merge_buffer)} lan scan. Bam 'm' them, 'r' reset."
                print(f"\n[Scan #{len(merge_buffer)}] {len(items)} items -> merged {len(merged)} items")
            else:
                merge_buffer = []
                last_render = render_results(
                    frame, items,
                    header=f"Scan 1 frame - {dt:.1f}s",
                )
                status = f"Scan xong: {len(items)} mon. Bam 's' scan lai, 'm' merge them."
                print(f"\n[Scan] {len(items)} items in {dt:.1f}s")

            for it in (merged if key == ord("m") else items):
                price = format_vnd(it.price_vnd) if it.has_price else "?"
                print(f"  - {it.name}  |  {price}")

        if key == ord("r"):
            merge_buffer = []
            last_render = None
            status = "Da reset. Bam 's' scan moi."
            print("[Reset] merge buffer cleared")

    cap.release()
    cv2.destroyAllWindows()
    return 0


if __name__ == "__main__":
    sys.exit(main())
