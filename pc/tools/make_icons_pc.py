"""生成 Windows 用的 .ico 应用图标（jpackage 需要）。"""
import os
from PIL import Image

SRC = r"C:\Users\mynlp\Downloads\1790688205101.png"
OUT_DIRS = [
    r"D:\dnshe-pc\desktop\icons",
    r"D:\dnshe-pc\installer\icons",
]

# 和安卓版用同一个裁剪参数
CROP_CENTER = (396, 402)
CROP_SIDE = 700

img = Image.open(SRC).convert("RGBA")
cx, cy = CROP_CENTER
half = CROP_SIDE // 2
img = img.crop((cx - half, cy - half, cx + half, cy + half))

sizes = [16, 24, 32, 48, 64, 128, 256]
for d in OUT_DIRS:
    os.makedirs(d, exist_ok=True)
    target = os.path.join(d, "app.ico")
    img.save(target, format="ICO", sizes=[(s, s) for s in sizes])
    print("wrote", target, os.path.getsize(target), "bytes")

# 顺便给 Compose 窗口用一张 png
png_target = r"D:\dnshe-pc\desktop\src\main\resources\app-icon.png"
os.makedirs(os.path.dirname(png_target), exist_ok=True)
img.resize((256, 256), Image.LANCZOS).save(png_target)
print("wrote", png_target)
