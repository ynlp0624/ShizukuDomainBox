"""生成窗口/任务栏用的多尺寸图标。

jpackage 的 --icon 只决定 exe 文件本身的图标；
窗口标题栏和任务栏图标必须由程序在运行时设置（否则就是 Java 默认的咖啡杯）。
Windows 会按需要挑不同尺寸，所以这里把所有常用尺寸都出一份。
"""
import os
from PIL import Image, ImageDraw

SRC = r"C:\Users\mynlp\Downloads\1790688205101.png"
CROP_CENTER = (396, 402)
CROP_SIDE = 700

base = Image.open(SRC).convert("RGBA")
cx, cy = CROP_CENTER
half = CROP_SIDE // 2
base = base.crop((cx - half, cy - half, cx + half, cy + half))

# Compose Desktop 的 resources 目录
RES = r"D:\dnshe-pc\desktop\src\main\resources"
os.makedirs(RES, exist_ok=True)

# 窗口图标：圆角方形，各尺寸一份
WINDOW_SIZES = [16, 20, 24, 32, 40, 48, 64, 96, 128, 256]


def rounded(img, size, radius_ratio=0.26):
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    art = img.resize((size, size), Image.LANCZOS)
    mask = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(mask)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=max(1, int(size * radius_ratio)), fill=255)
    out.paste(art, (0, 0), mask)
    return out


for s in WINDOW_SIZES:
    p = os.path.join(RES, f"app-icon-{s}.png")
    rounded(base, s).save(p)
print("window icons:", len(WINDOW_SIZES), "sizes ->", RES)

# 顺便刷新界面里用的大 logo
for size, name in ((256, "app-logo.png"), (128, "app-logo-small.png"), (96, "app-logo-96.png")):
    rounded(base, size).save(os.path.join(RES, name))
print("logos refreshed")

# exe 用的 ico 也重出一份（多尺寸）
for d in (r"D:\dnshe-pc\desktop\icons", r"D:\dnshe-pc\installer\icons"):
    if os.path.isdir(os.path.dirname(d)):
        os.makedirs(d, exist_ok=True)
        p = os.path.join(d, "app.ico")
        base.save(p, format="ICO", sizes=[(s, s) for s in (16, 24, 32, 48, 64, 128, 256)])
        print("ico:", p, os.path.getsize(p), "bytes")
