"""
Python port cua MenuOcrParser.kt (app/src/main/kotlin/.../ml/MenuOcrParser.kt).

Dung de eval parser tren dataset Viet-Menu-gemini-VQA (xem notebook 02_menu_eval.ipynb).
Khac biet voi ban Kotlin:
  - Khong co Text.Line.boundingBox (vi input la raw text khong co geometry)
  - Coi moi `\n` la 1 row → bo qua y-coord grouping
  - Cac regex va logic price khac giu nguyen 100% cho ket qua eval reproducible

Khi update logic parser, can sync ca 2 file:
  - app/src/main/kotlin/.../ml/MenuOcrParser.kt  (production tren Android)
  - ml-training/scripts/menu_parser.py             (eval Python)
"""
from dataclasses import dataclass
from typing import Optional, List
import re


# Regex gia VND — copy nguyen tu Kotlin (de regex character escape khac chut)
PRICE_REGEX = re.compile(
    r"(\d{1,3}(?:[.,\s]\d{3})+|\d{1,4})\s*(k|nghìn|nghin|đồng|dong|đ|d|₫|VND|vnd|VNĐ)?",
    re.IGNORECASE,
)


@dataclass
class MenuItem:
    raw_text: str
    name: str
    price_vnd: Optional[int]

    @property
    def has_price(self) -> bool:
        return self.price_vnd is not None


def parse(text: str) -> List[MenuItem]:
    """Parse raw OCR text → danh sach MenuItem."""
    if not text or not text.strip():
        return []
    items = []
    for raw_line in text.split("\n"):
        line = raw_line.strip()
        if not line:
            continue
        item = _parse_row(line)
        if item:
            items.append(item)
    return items


def _parse_row(line: str) -> Optional[MenuItem]:
    match = _find_best_price_match(line)
    if match is None:
        return MenuItem(raw_text=line, name=line, price_vnd=None)
    start, end, price = match
    name_part = (line[:start] + line[end:]).strip()
    name = _clean_name(name_part)
    return MenuItem(raw_text=line, name=name if name else line, price_vnd=price)


def _clean_name(s: str) -> str:
    return s.strip(" \t-:.,")


def _find_best_price_match(text: str):
    """Tra ve tuple (start, end, price_vnd) hoac None."""
    candidates = []
    for m in PRICE_REGEX.finditer(text):
        num_str = m.group(1)
        unit = (m.group(2) or "").lower()
        parsed = _parse_amount(num_str, unit)
        if parsed is None:
            continue
        if not 1_000 <= parsed <= 10_000_000:
            continue
        candidates.append((m.start(), m.end(), parsed, bool(unit)))
    if not candidates:
        return None
    # Uu tien: co unit, sau do match cuoi cung
    candidates.sort(key=lambda c: (c[3], c[0]))
    best = candidates[-1]
    return best[0], best[1], best[2]


def _parse_amount(num_str: str, unit: str) -> Optional[int]:
    cleaned = re.sub(r"[.,\s]", "", num_str)
    try:
        n = int(cleaned)
    except ValueError:
        return None
    if unit in ("k", "nghìn", "nghin"):
        return n * 1_000
    return n


# -------- Helpers cho eval (so sanh fuzzy giua ground truth va du doan) -------- #

def normalize_dish_name(name: str) -> str:
    """Lowercase + bo dau cau + collapse whitespace de fuzzy match."""
    s = name.lower().strip()
    s = re.sub(r"[^\w\sÀ-ỹà-ỹ]+", " ", s)  # giu Unicode tieng Viet
    s = re.sub(r"\s+", " ", s).strip()
    return s


def parse_price_ground_truth(price_str) -> Optional[int]:
    """Convert ground truth price string (e.g. '20k', '50,000', '15 nghìn') → int VND."""
    if isinstance(price_str, (int, float)):
        return int(price_str)
    if not price_str:
        return None
    s = str(price_str).strip().lower()
    # Try price regex
    m = PRICE_REGEX.search(s)
    if not m:
        return None
    return _parse_amount(m.group(1), (m.group(2) or "").lower())


if __name__ == "__main__":
    sample = """Phở bò 50.000
Bún chả 70k
Trà đá 5,000
Cơm tấm sườn nướng - 60.000đ
"""
    for item in parse(sample):
        print(f"  {item.name!r} -> {item.price_vnd}")
