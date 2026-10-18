"""把 1080x2400 的截图缩放到官网用的 400x889，并做圆角。"""
import os
from PIL import Image, ImageDraw

SRC = r"D:\dnshe\screens\shots"
DST = r"D:\dnshe\site\assets\shots"

os.makedirs(DST, exist_ok=True)

# 需要处理的图（源文件名 -> 目标文件名）
MAP = {
    "new-domains.png": "android-domains.png",
    "android-recordstmp.png": "android-records.png",  # 兼容
}

files = [
    ("D:\\dnshe\\screens\\new-domains.png", "android-domains.png"),
    ("shots\\android-records.png", "android-records.png"),
    ("shots\\android-tools.png", "android-tools.png"),
    ("shots\\android-mail-inbox.png", "android-mail-inbox.png"),
    ("shots\\android-mail-detail.png", "android-mail-detail.png"),
    ("shots\\android-mail-compose.png", "android-mail-compose.png"),
    ("shots\\android-accounts.png", "android-accounts.png"),
    ("shots\\android-settings.png", "android-settings.png"),
]

# 域名页单独处理（之前命名为 new-domains.png）
src_map = {
    "android-domains.png": r"D:\dnshe\screens\new-domains.png",
    "android-records.png": os.path.join(SRC, "android-records.png"),
    "android-tools.png": os.path.join(SRC, "android-tools.png"),
    "android-mail-inbox.png": os.path.join(SRC, "android-mail-inbox.png"),
    "android-mail-detail.png": os.path.join(SRC, "android-mail-detail.png"),
    "android-mail-compose.png": os.path.join(SRC, "android-mail-compose.png"),
    "android-accounts.png": os.path.join(SRC, "android-accounts.png"),
    "android-settings.png": os.path.join(SRC, "android-settings.png"),
}

TARGET_W = 400
RADIUS = 18  # 圆角像素


def rounded(img, radius):
    mask = Image.new("L", img.size, 0)
    d = ImageDraw.Draw(mask)
    d.rounded_rectangle([0, 0, img.size[0] - 1, img.size[1] - 1],
                        radius=radius, fill=255)
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


for name, path in src_map.items():
    if not os.path.exists(path):
        print(f"  ✗ 缺文件: {path}")
        continue
    img = Image.open(path).convert("RGBA")
    w, h = img.size
    new_h = int(TARGET_W * h / w)
    img = img.resize((TARGET_W, new_h), Image.LANCZOS)
    img = rounded(img, RADIUS)
    dst = os.path.join(DST, name)
    img.save(dst, "PNG", optimize=True)
    print(f"  ✓ {name}  {TARGET_W}x{new_h}  {os.path.getsize(dst) // 1024} KB")

print("\n完成")
