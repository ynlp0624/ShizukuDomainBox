"""整理官网素材：截图缩放、logo 生成、下载文件就位。"""
import os
import shutil
from PIL import Image, ImageDraw

SITE = r"D:\dnshe-site"
SHOTS = os.path.join(SITE, "assets", "shots")
os.makedirs(SHOTS, exist_ok=True)

# ---------- 1. 安卓截图 ----------
AND_SRC = r"D:\dnshe\screens"
AND_MAP = {
    "60-domain-list-external.png": "android-domains.png",
    "14-dns-records.png":          "android-records.png",
    "26-tools.png":                "android-tools.png",
    "57-root-picker-external.png": "android-roots.png",
    "31-upgrade.png":              "android-upgrade.png",
    "28-keys.png":                 "android-keys.png",
}

for src, dst in AND_MAP.items():
    p = os.path.join(AND_SRC, src)
    if not os.path.exists(p):
        print("  [缺]", src)
        continue
    img = Image.open(p).convert("RGB")
    w, h = img.size
    target_w = 400
    img = img.resize((target_w, int(h * target_w / w)), Image.LANCZOS)
    out = os.path.join(SHOTS, dst)
    img.save(out, "PNG", optimize=True)
    print(f"  {dst:<28} {img.size}  {os.path.getsize(out)//1024} KB")

# ---------- 2. PC 窗口截图 ----------
PC_MAP = {
    os.path.join(SITE, "raw", "pc-app.png"):       "pc-app.png",
    os.path.join(SITE, "raw", "pc-installer.png"): "pc-installer.png",
}
for src, dst in PC_MAP.items():
    if not os.path.exists(src):
        print("  [缺]", src)
        continue
    img = Image.open(src).convert("RGB")
    w, h = img.size
    target_w = 1100
    img = img.resize((target_w, int(h * target_w / w)), Image.LANCZOS)
    out = os.path.join(SHOTS, dst)
    img.save(out, "PNG", optimize=True)
    print(f"  {dst:<28} {img.size}  {os.path.getsize(out)//1024} KB")

# ---------- 3. Logo（从原始图裁切） ----------
SRC = r"C:\Users\mynlp\Downloads\1790688205101.png"
base = Image.open(SRC).convert("RGBA")
cx, cy, half = 396, 402, 350
base = base.crop((cx - half, cy - half, cx + half, cy + half))


def rounded(img, size, ratio=0.26):
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    art = img.resize((size, size), Image.LANCZOS)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1],
                                           radius=max(1, int(size * ratio)), fill=255)
    out.paste(art, (0, 0), mask)
    return out


for size, name in ((64, "logo-64.png"), (128, "logo-128.png"), (256, "logo-256.png")):
    out = os.path.join(SITE, "assets", name)
    rounded(base, size).save(out, "PNG", optimize=True)
    print(f"  {name:<28} {size}x{size}  {os.path.getsize(out)//1024} KB")

# ---------- 4. 下载文件 ----------
DL = os.path.join(SITE, "downloads")
os.makedirs(DL, exist_ok=True)
DOWNLOADS = [
    (r"D:\dnshe\dist\雫的域名盒-Android-v1.1.1.apk",
     "ShizukuDomainBox-Android-v1.1.1.apk"),
    (r"D:\dnshe-pc\dist\ShizukuDomainBox-Setup-v1.0.0.exe",
     "ShizukuDomainBox-Setup-v1.0.0.exe"),
]
for src, dst in DOWNLOADS:
    if os.path.exists(src):
        shutil.copy2(src, os.path.join(DL, dst))
        print(f"  {dst:<44} {os.path.getsize(os.path.join(DL, dst))//1024//1024} MB")
    else:
        print("  [缺]", src)

print("\n素材整理完成")
