"""生成窗口/任务栏用的多尺寸图标 + exe 用的 .ico + 界面里的大 logo。

为什么要「多尺寸」：jpackage 的 `--icon` 只决定 **exe 文件本身**的图标；
窗口标题栏和任务栏的图标必须由程序在运行时自己设置（否则就是 Java 默认的咖啡杯），
而 Windows 会按场景挑不同尺寸，所以常用尺寸各出一份。

用法：
    python tools/make-icons.py --res app/src/main/resources --ico app/icons \\
        --src C:\\path\\art.png --crop-center 396,402 --crop-side 700

不给 --src 时会合成一张备用图（蓝色圆角方块 + 猫耳），保证没有美术素材也能跑通流水线。
"""
from __future__ import annotations

import argparse
import os
import sys

from PIL import Image, ImageDraw

# Windows 控制台默认是 GBK，直接 print 符号会 UnicodeEncodeError
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

DEFAULT_SIZES = [16, 20, 24, 32, 40, 48, 64, 96, 128, 256]

BRAND = (62, 111, 217, 255)
BRAND_DARK = (30, 62, 140, 255)
PINK = (255, 176, 196, 255)
INK = (24, 32, 56, 255)


def synth_source(size: int = 1024) -> Image.Image:
    """合成备用图：蓝色圆角方块 + 猫耳 + 眼睛。没有素材时的兜底。"""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    s = size

    d.rounded_rectangle([0, 0, s - 1, s - 1], radius=int(s * 0.22), fill=BRAND)

    # 猫耳（外 + 内）
    def ear(cx: int) -> None:
        outer = [(cx - int(s * 0.13), int(s * 0.38)),
                 (cx - int(s * 0.02), int(s * 0.08)),
                 (cx + int(s * 0.15), int(s * 0.30))]
        inner = [(cx - int(s * 0.08), int(s * 0.34)),
                 (cx - int(s * 0.02), int(s * 0.17)),
                 (cx + int(s * 0.09), int(s * 0.28))]
        d.polygon(outer, fill=BRAND_DARK)
        d.polygon(inner, fill=PINK)

    ear(int(s * 0.31))
    ear(int(s * 0.69))

    # 脸
    d.ellipse([int(s * 0.20), int(s * 0.30), int(s * 0.80), int(s * 0.86)], fill=(255, 255, 255, 255))

    # 眼睛
    for cx in (int(s * 0.37), int(s * 0.63)):
        d.ellipse([cx - int(s * 0.055), int(s * 0.48), cx + int(s * 0.055), int(s * 0.60)], fill=INK)
        d.ellipse([cx - int(s * 0.018), int(s * 0.50), cx + int(s * 0.012), int(s * 0.53)],
                  fill=(255, 255, 255, 255))

    # 腮红
    for cx in (int(s * 0.29), int(s * 0.71)):
        d.ellipse([cx - int(s * 0.05), int(s * 0.62), cx + int(s * 0.05), int(s * 0.69)], fill=PINK)

    # 嘴（一个「w」形的小弧线，用两条线凑）
    d.arc([int(s * 0.44), int(s * 0.60), int(s * 0.56), int(s * 0.70)], 20, 160, fill=INK, width=max(2, int(s * 0.012)))
    return img


def load_square(src: str | None, crop_center: tuple[int, int] | None, crop_side: int | None) -> Image.Image:
    if not src:
        print("· 没有 --src，使用合成的备用图")
        return synth_source()

    img = Image.open(src).convert("RGBA")
    w, h = img.size
    side = crop_side or min(w, h)
    side = max(16, min(side, min(w, h)))

    if crop_center:
        cx, cy = crop_center
    else:
        cx, cy = w // 2, h // 2

    half = side // 2
    left = max(0, min(w - side, cx - half))
    top = max(0, min(h - side, cy - half))
    return img.crop((left, top, left + side, top + side))


def rounded(img: Image.Image, size: int, radius_ratio: float = 0.26) -> Image.Image:
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    art = img.resize((size, size), Image.LANCZOS)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        [0, 0, size - 1, size - 1], radius=max(1, int(size * radius_ratio)), fill=255
    )
    out.paste(art, (0, 0), mask)
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description="生成 InstallerKit 用的图标")
    ap.add_argument("--src", help="源图（png）。不给就合成一张备用图")
    ap.add_argument("--res", required=True, help="窗口图标输出的 resources 目录")
    ap.add_argument("--ico", action="append", default=[], help="exe 图标输出目录，可重复")
    ap.add_argument("--crop-center", help="裁剪中心 x,y（默认取正中）")
    ap.add_argument("--crop-side", type=int, help="裁剪边长（默认取短边）")
    ap.add_argument("--sizes", default=",".join(str(s) for s in DEFAULT_SIZES))
    args = ap.parse_args()

    sizes = [int(x) for x in args.sizes.split(",") if x.strip()]
    center = None
    if args.crop_center:
        parts = args.crop_center.split(",")
        if len(parts) != 2:
            print(f"✗ --crop-center 要写成 x,y，收到：{args.crop_center}", file=sys.stderr)
            return 2
        center = (int(parts[0]), int(parts[1]))

    base = load_square(args.src, center, args.crop_side)

    os.makedirs(args.res, exist_ok=True)
    for s in sizes:
        rounded(base, s).save(os.path.join(args.res, f"app-icon-{s}.png"))
    print(f"✓ 窗口图标 {len(sizes)} 个尺寸 -> {args.res}")

    # 界面里用的大 logo
    for size, name in ((256, "app-logo.png"), (128, "app-logo-small.png"), (96, "app-logo-96.png")):
        rounded(base, size).save(os.path.join(args.res, name))
    print(f"✓ logo 已刷新 -> {args.res}")

    # exe 用的 ico（多尺寸）
    ico_sizes = [(s, s) for s in (16, 24, 32, 48, 64, 128, 256)]
    for d in args.ico:
        os.makedirs(d, exist_ok=True)
        p = os.path.join(d, "app.ico")
        base.save(p, format="ICO", sizes=ico_sizes)
        print(f"✓ ico -> {p}（{os.path.getsize(p)} 字节）")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
