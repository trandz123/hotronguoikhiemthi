# -*- coding: utf-8 -*-
"""Ve 6 so do cua bao cao Mat AI thanh PNG (tieng Viet co dau)."""
import os
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, Polygon, Ellipse, FancyArrowPatch, Circle
from matplotlib.lines import Line2D

plt.rcParams["font.family"] = "DejaVu Sans"

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "docs", "diagrams")
os.makedirs(OUT, exist_ok=True)

C = {
    "blue":   ("#dae8fc", "#6c8ebf"),
    "green":  ("#d5e8d4", "#82b366"),
    "orange": ("#ffe6cc", "#d79b00"),
    "yellow": ("#fff2cc", "#d6b656"),
    "red":    ("#f8cecc", "#b85450"),
    "gray":   ("#f5f5f5", "#666666"),
    "purple": ("#e1d5e7", "#9673a6"),
}


def canvas(W, H):
    fig, ax = plt.subplots(figsize=(W / 100, H / 100))
    ax.set_xlim(0, W)
    ax.set_ylim(H, 0)
    ax.set_aspect("equal")
    ax.axis("off")
    return fig, ax


def box(ax, x, y, w, h, text, color="blue", fs=10, bold=False, rounded=True):
    fc, ec = C[color]
    style = "round,pad=0,rounding_size=10" if rounded else "square,pad=0"
    ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle=style,
                                linewidth=1.4, edgecolor=ec, facecolor=fc,
                                mutation_aspect=1))
    ax.text(x + w / 2, y + h / 2, text, ha="center", va="center",
            fontsize=fs, fontweight="bold" if bold else "normal", wrap=True)


def diamond(ax, cx, cy, w, h, text, color="orange", fs=9):
    fc, ec = C[color]
    pts = [(cx, cy - h / 2), (cx + w / 2, cy), (cx, cy + h / 2), (cx - w / 2, cy)]
    ax.add_patch(Polygon(pts, closed=True, linewidth=1.4, edgecolor=ec, facecolor=fc))
    ax.text(cx, cy, text, ha="center", va="center", fontsize=fs)


def ellipse(ax, x, y, w, h, text, color="blue", fs=10):
    fc, ec = C[color]
    ax.add_patch(Ellipse((x + w / 2, y + h / 2), w, h, linewidth=1.4,
                         edgecolor=ec, facecolor=fc))
    ax.text(x + w / 2, y + h / 2, text, ha="center", va="center", fontsize=fs)


def arrow(ax, p1, p2, label=None, dashed=False, both=False, rad=0.0, fs=8):
    astyle = "<|-|>" if both else "-|>"
    ax.add_patch(FancyArrowPatch(p1, p2, arrowstyle=astyle, mutation_scale=14,
                                 linewidth=1.2, color="#333333",
                                 linestyle="--" if dashed else "-",
                                 connectionstyle=f"arc3,rad={rad}",
                                 shrinkA=1, shrinkB=1))
    if label:
        mx, my = (p1[0] + p2[0]) / 2, (p1[1] + p2[1]) / 2
        ax.text(mx, my, label, ha="center", va="center", fontsize=fs,
                bbox=dict(boxstyle="round,pad=0.15", fc="white", ec="none"))


def save(fig, name):
    path = os.path.join(OUT, name)
    fig.savefig(path, dpi=150, bbox_inches="tight", pad_inches=0.15, facecolor="white")
    plt.close(fig)
    print("Saved", name)


# ---------------- 1. KIEN TRUC ----------------
def d1_kientruc():
    fig, ax = canvas(860, 440)
    box(ax, 210, 20, 440, 70, "Tầng Giao diện (Compose)\nMoneyScreen · MenuScreen · Settings...",
        "blue", 11, bold=True)
    box(ax, 210, 150, 440, 70, "Tầng ViewModel (MVVM)\nMoneyViewModel · MenuViewModel · ...",
        "green", 11, bold=True)
    box(ax, 170, 300, 180, 110, "Mô-đun ML\n\nYOLOv10n (on-device)\nGroq VLM (cloud)", "orange", 10)
    box(ax, 390, 300, 160, 110, "TtsManager\n\nFPT + Android", "yellow", 10)
    box(ax, 580, 300, 180, 110, "Lưu trữ\n\nRoom / DataStore", "red", 10)
    arrow(ax, (430, 90), (430, 150), "sự kiện / StateFlow", fs=9)
    arrow(ax, (380, 220), (260, 300))
    arrow(ax, (430, 220), (470, 300))
    arrow(ax, (500, 220), (670, 300))
    save(fig, "01_kientruc.png")


# ---------------- 2. LUONG DEM TIEN ----------------
def d2_luong_tien():
    fig, ax = canvas(720, 1070)
    box(ax, 160, 20, 260, 50, "Bắt đầu: mở chế độ đếm tiền", "green", 10, rounded=True)
    box(ax, 160, 100, 260, 50, "Bật camera + đọc hướng dẫn (TTS)", "blue", 10, rounded=False)
    box(ax, 160, 180, 260, 50, "Lấy khung hình (CameraX)", "blue", 10, rounded=False)
    diamond(ax, 290, 300, 220, 90, "Đủ sáng & nét?")
    box(ax, 160, 390, 260, 50, "YOLOv10n nhận diện mệnh giá", "blue", 10, rounded=False)
    diamond(ax, 290, 500, 220, 90, "Độ tin cậy ≥ 0,70?")
    box(ax, 160, 590, 260, 50, "Đọc mệnh giá tờ đang thấy (TTS)", "blue", 10, rounded=False)
    diamond(ax, 290, 700, 220, 90, "Người dùng vuốt xuống?")
    box(ax, 160, 790, 260, 50, "Cộng tờ vào tổng + đọc tổng", "green", 10, rounded=False)
    box(ax, 160, 870, 260, 55, "Chờ nhấc tờ ra\n(chống đếm trùng)", "yellow", 10, rounded=False)
    box(ax, 160, 985, 260, 50, "Kết thúc / chuyển chế độ", "red", 10, rounded=True)
    arrow(ax, (290, 70), (290, 100))
    arrow(ax, (290, 150), (290, 180))
    arrow(ax, (290, 230), (290, 255))
    arrow(ax, (290, 345), (290, 390), "Có")
    arrow(ax, (180, 300), (160, 205), "Không", rad=-0.45)
    arrow(ax, (290, 440), (290, 455))
    arrow(ax, (290, 545), (290, 590), "Có")
    arrow(ax, (180, 500), (160, 205), "Không", rad=-0.6)
    arrow(ax, (290, 640), (290, 655))
    arrow(ax, (290, 745), (290, 790), "Có (chọn)")
    arrow(ax, (180, 700), (160, 205), "Không", rad=-0.7)
    arrow(ax, (290, 840), (290, 870))
    arrow(ax, (290, 925), (290, 985), "vuốt lên (kết thúc)", fs=8)
    # loop p6 -> p2: bracket ben phai x=560
    ax.add_line(Line2D([420, 560], [897, 897], lw=1.2, color="#333333"))
    ax.add_line(Line2D([560, 560], [897, 205], lw=1.2, color="#333333"))
    arrow(ax, (560, 205), (420, 205), "tờ tiếp theo", fs=8)
    save(fig, "02_luong_tien.png")


# ---------------- 3. LUONG DOC MENU ----------------
def d3_luong_menu():
    fig, ax = canvas(780, 1020)
    box(ax, 160, 20, 260, 50, "Bắt đầu: mở chế độ đọc menu", "green", 10, rounded=True)
    box(ax, 160, 100, 260, 50, "Camera bật + đếm lùi 3-2-1", "blue", 10, rounded=False)
    box(ax, 160, 180, 260, 50, "Tự chụp ảnh menu", "blue", 10, rounded=False)
    box(ax, 160, 260, 260, 50, "Thu nhỏ ≤1024px, nén JPEG, base64", "blue", 9, rounded=False)
    box(ax, 160, 340, 260, 50, "Gửi Groq VLM (Llama 4 Scout)", "orange", 10, rounded=False)
    diamond(ax, 290, 460, 220, 90, "Lỗi mạng / API?")
    box(ax, 500, 435, 220, 50, "Thông báo lỗi, cho thử lại", "red", 9, rounded=False)
    box(ax, 160, 560, 260, 50, "Nhận JSON {món, giá} + chuẩn hóa", "blue", 9, rounded=False)
    diamond(ax, 290, 680, 220, 90, "Có món?")
    box(ax, 500, 655, 220, 50, "Thông báo, chụp lại", "red", 9, rounded=False)
    box(ax, 160, 780, 260, 50, "TTS đọc danh sách món", "green", 10, rounded=False)
    box(ax, 160, 860, 260, 50, "Vuốt duyệt / chọn món → đọc tổng", "green", 9, rounded=False)
    box(ax, 195, 940, 190, 50, "Kết thúc", "red", 10, rounded=True)
    arrow(ax, (290, 70), (290, 100))
    arrow(ax, (290, 150), (290, 180))
    arrow(ax, (290, 230), (290, 260))
    arrow(ax, (290, 310), (290, 340))
    arrow(ax, (290, 390), (290, 415))
    arrow(ax, (400, 460), (500, 460), "Có")
    arrow(ax, (610, 435), (610, 205), "thử lại", dashed=True, fs=8)
    arrow(ax, (610, 205), (420, 205), dashed=True)
    arrow(ax, (290, 505), (290, 560), "Không")
    arrow(ax, (290, 610), (290, 635))
    arrow(ax, (400, 680), (500, 680), "Không")
    arrow(ax, (610, 655), (650, 205), "chụp lại", dashed=True, fs=8)
    arrow(ax, (650, 205), (420, 205), dashed=True)
    arrow(ax, (290, 725), (290, 780), "Có")
    arrow(ax, (290, 830), (290, 860))
    arrow(ax, (290, 910), (290, 940))
    save(fig, "03_luong_menu.png")


# ---------------- 4. USE CASE ----------------
def d4_usecase():
    fig, ax = canvas(700, 500)
    # actor stick figure
    cx, cy = 70, 250
    ax.add_patch(Circle((cx, cy - 60), 14, fill=False, lw=1.6, edgecolor="#333"))
    ax.add_line(Line2D([cx, cx], [cy - 46, cy + 10], lw=1.6, color="#333"))
    ax.add_line(Line2D([cx - 28, cx + 28], [cy - 30, cy - 30], lw=1.6, color="#333"))
    ax.add_line(Line2D([cx, cx - 24], [cy + 10, cy + 55], lw=1.6, color="#333"))
    ax.add_line(Line2D([cx, cx + 24], [cy + 10, cy + 55], lw=1.6, color="#333"))
    ax.text(cx, cy + 80, "Người dùng\nkhiếm thị", ha="center", va="center", fontsize=10)
    # system boundary
    ax.add_patch(FancyBboxPatch((240, 30), 400, 440, boxstyle="square,pad=0",
                                linewidth=1.4, edgecolor="#666", facecolor="none"))
    ax.text(440, 50, "Ứng dụng Mắt AI", ha="center", va="center", fontsize=12, fontweight="bold")
    ucs = [
        (80, "Đếm tiền", "blue"),
        (160, "Đọc thực đơn", "blue"),
        (240, "Ra lệnh giọng nói", "green"),
        (320, "Xem lịch sử", "yellow"),
        (400, "Cấu hình (cài đặt)", "yellow"),
    ]
    for y, t, col in ucs:
        ellipse(ax, 300, y, 280, 55, t, col, 10)
        arrow(ax, (98, cy), (300, y + 27))
    save(fig, "04_usecase.png")


# ---------------- 5. TUAN TU ----------------
def d5_tuantu():
    fig, ax = canvas(1180, 590)
    lifelines = [
        (40, 140, "Người dùng", "blue"),
        (220, 140, "MoneyScreen", "blue"),
        (400, 140, "CameraX", "blue"),
        (580, 180, "Yolov10MoneyDetector", "orange"),
        (800, 160, "MoneyViewModel", "green"),
        (1000, 140, "TtsManager", "yellow"),
    ]
    centers = []
    for x, w, name, col in lifelines:
        box(ax, x, 20, w, 40, name, col, 9, rounded=False)
        cxx = x + w / 2
        centers.append(cxx)
        ax.add_line(Line2D([cxx, cxx], [60, 560], lw=1.0, ls="--", color="#999"))
    U, S, CA, Y, V, T = centers
    msgs = [
        (U, S, 100, "đưa tờ tiền vào khung", False),
        (S, CA, 145, "lấy khung hình", False),
        (CA, Y, 190, "khung hình", False),
        (Y, V, 235, "MoneyResult (mệnh giá, độ tin cậy)", False),
        (V, T, 280, "đọc mệnh giá", False),
        (T, U, 325, "phát giọng nói", True),
        (U, S, 390, "vuốt xuống (chọn tờ)", False),
        (S, V, 435, "selectCurrent()", False),
        (V, T, 480, "đọc tổng", False),
        (T, U, 525, "phát giọng nói", True),
    ]
    for a, b, y, lbl, dash in msgs:
        arrow(ax, (a, y), (b, y), lbl, dashed=dash, fs=8)
    save(fig, "05_tuantu.png")


# ---------------- 6. TRIEN KHAI ----------------
def d6_trienkhai():
    fig, ax = canvas(880, 500)
    box(ax, 40, 30, 340, 370, "", "gray", 10)
    ax.text(210, 48, "Điện thoại Android (on-device)", ha="center", va="center",
            fontsize=12, fontweight="bold")
    box(ax, 70, 80, 280, 55, "App Mắt AI\n(Compose UI + ViewModel)", "blue", 10, rounded=False)
    box(ax, 70, 155, 280, 60, "YOLOv10n — TFLite\n(nhận diện tiền, chạy trên máy)", "orange", 9, rounded=False)
    box(ax, 70, 240, 280, 55, "Room / DataStore\n(lịch sử, cài đặt)", "red", 9, rounded=False)
    box(ax, 70, 320, 280, 55, "Android TTS\n(giọng dự phòng, ngoại tuyến)", "yellow", 9, rounded=False)
    box(ax, 520, 80, 330, 250, "", "purple", 10)
    ax.text(685, 98, "Dịch vụ đám mây (cloud)", ha="center", va="center",
            fontsize=12, fontweight="bold")
    box(ax, 550, 135, 270, 70, "Groq Cloud — Llama 4 Scout\n(VLM đọc menu → JSON món + giá)", "green", 9, rounded=False)
    box(ax, 550, 235, 270, 60, "FPT.AI TTS\n(giọng tiếng Việt tự nhiên)", "yellow", 9, rounded=False)
    arrow(ax, (350, 110), (550, 170), "HTTPS: ảnh menu → JSON", both=True, fs=8)
    arrow(ax, (350, 345), (550, 265), "HTTPS: văn bản → âm thanh", both=True, fs=8)
    ax.text(40, 430, "Lưu ý: nhận diện tiền chạy hoàn toàn trên máy (không cần mạng); "
            "chỉ đọc menu & giọng FPT mới gọi đám mây.",
            ha="left", va="center", fontsize=9, style="italic")
    save(fig, "06_trienkhai.png")


d1_kientruc()
d2_luong_tien()
d3_luong_menu()
d4_usecase()
d5_tuantu()
d6_trienkhai()
print("DONE")
