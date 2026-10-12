"""生成正式版用的应用标志（用于界面里的 logo 位，替代 emoji）。"""
import os
from PIL import Image, ImageDraw

SRC = r"C:\Users\mynlp\Downloads\1790688205101.png"
CROP_CENTER = (396, 402)
CROP_SIDE = 700


def rounded(img, size, radius_ratio=0.28):
    """缩放并套圆角，做界面里的 logo 方块"""
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    art = img.resize((size, size), Image.LANCZOS)
    mask = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(mask)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * radius_ratio), fill=255)
    out.paste(art, (0, 0), mask)
    return out


base = Image.open(SRC).convert("RGBA")
cx, cy = CROP_CENTER
half = CROP_SIDE // 2
base = base.crop((cx - half, cy - half, cx + half, cy + half))

# 安卓：drawable-nodpi 单张（256/128/96 三个尺寸够用了）
and_dir = r"D:\dnshe\app\src\main\res\drawable-nodpi"
os.makedirs(and_dir, exist_ok=True)
for size, name in ((256, "ic_logo.png"), (128, "ic_logo_small.png"), (96, "ic_logo_tiny.png")):
    p = os.path.join(and_dir, name)
    rounded(base, size).save(p)
    print("android:", p, os.path.getsize(p), "bytes")

# PC：Compose Desktop 的 resources
pc_dir = r"D:\dnshe-pc\desktop\src\main\resources"
os.makedirs(pc_dir, exist_ok=True)
for size, name in ((256, "app-logo.png"), (128, "app-logo-small.png")):
    p = os.path.join(pc_dir, name)
    rounded(base, size).save(p)
    print("pc:", p, os.path.getsize(p), "bytes")

# 安装程序的品牌面板用的（也是 PC 的 resources）
p = os.path.join(pc_dir, "app-logo-96.png")
rounded(base, 96).save(p)
print("pc:", p, os.path.getsize(p), "bytes")
