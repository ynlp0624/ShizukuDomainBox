"""从老婆给的插画生成 Android 自适应图标资源（各密度 mipmap）。"""
import os
from PIL import Image, ImageDraw, ImageFilter

SRC = r"C:\Users\mynlp\Downloads\1790688205101.png"
RES = r"D:\dnshe\app\src\main\res"

# 裁剪：以脸部+猫猫玩偶为中心的正方形
CROP_CENTER = (396, 402)
CROP_SIDE = 700

DENSITIES = {
    "mdpi": 1.0,
    "hdpi": 1.5,
    "xhdpi": 2.0,
    "xxhdpi": 3.0,
    "xxxhdpi": 4.0,
}

LEGACY_SIZE_DP = 48
ADAPTIVE_DP = 108
FOREGROUND_SCALE = 1.0  # 插画本身是满幅的，铺满整个画布，边缘交给系统遮罩裁


def load_base() -> Image.Image:
    img = Image.open(SRC).convert("RGBA")
    cx, cy = CROP_CENTER
    half = CROP_SIDE // 2
    box = (cx - half, cy - half, cx + half, cy + half)
    return img.crop(box)


def sample_bg(img: Image.Image) -> tuple:
    """前景是满幅插画，底板只在极端遮罩下露一点边，用白色最干净。"""
    return (255, 255, 255, 255)


def rounded_mask(size: int, radius_ratio: float) -> Image.Image:
    mask = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(mask)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * radius_ratio), fill=255)
    return mask


def circle_mask(size: int) -> Image.Image:
    mask = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(mask)
    d.ellipse([0, 0, size - 1, size - 1], fill=255)
    return mask


def main():
    base = load_base()
    bg = sample_bg(base)
    print("sampled background:", bg)

    # 背景色写进 colors.xml
    colors_path = os.path.join(RES, "values", "colors.xml")
    os.makedirs(os.path.dirname(colors_path), exist_ok=True)
    with open(colors_path, "w", encoding="utf-8") as f:
        f.write(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            "<resources>\n"
            f'    <color name="ic_launcher_background">#{bg[0]:02X}{bg[1]:02X}{bg[2]:02X}</color>\n'
            '    <color name="window_background">#F8F9FF</color>\n'
            '    <color name="window_background_dark">#11131A</color>\n'
            "</resources>\n"
        )

    previews = []

    for name, scale in DENSITIES.items():
        out_dir = os.path.join(RES, f"mipmap-{name}")
        os.makedirs(out_dir, exist_ok=True)

        # ---- 自适应图标前景（108dp 画布，内容居中 104/108）----
        canvas = int(round(ADAPTIVE_DP * scale))
        fg = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
        content = int(round(canvas * FOREGROUND_SCALE))
        art = base.resize((content, content), Image.LANCZOS)
        off = (canvas - content) // 2
        fg.paste(art, (off, off), art)
        fg.save(os.path.join(out_dir, "ic_launcher_foreground.png"))
        if name == "xxxhdpi":
            previews.append(("adaptive-canvas", fg.copy()))

        # ---- 传统方形图标 ----
        n = int(round(LEGACY_SIZE_DP * scale))
        sq = base.resize((n, n), Image.LANCZOS).convert("RGBA")
        sq.putalpha(rounded_mask(n, 0.22))
        sq.save(os.path.join(out_dir, "ic_launcher.png"))

        # ---- 传统圆形图标 ----
        rd = base.resize((n, n), Image.LANCZOS).convert("RGBA")
        rd.putalpha(circle_mask(n))
        rd.save(os.path.join(out_dir, "ic_launcher_round.png"))

        if name == "xxxhdpi":
            previews.append(("legacy-square", sq.copy()))
            previews.append(("legacy-round", rd.copy()))

    # ---- 预览图：模拟系统遮罩（圆 / 方圆 / 泪滴），检查裁切后好不好看 ----
    canvas = previews[0][1]
    size = canvas.width
    tiles = []
    masks = [
        ("circle", circle_mask(size)),
        ("squircle", rounded_mask(size, 0.30)),
        ("rounded", rounded_mask(size, 0.16)),
    ]
    for label, m in masks:
        plate = Image.new("RGBA", (size, size), bg)
        plate.alpha_composite(canvas, (0, 0))
        plate.putalpha(m)
        tiles.append(plate)

    gap = 26
    strip = Image.new("RGBA", (size * len(tiles) + gap * (len(tiles) + 1), size + gap * 2), (232, 234, 240, 255))
    for i, t in enumerate(tiles):
        strip.alpha_composite(t, (gap + i * (size + gap), gap))
    strip = strip.resize((strip.width // 3, strip.height // 3), Image.LANCZOS)
    strip.save(r"D:\dnshe\icon_preview.png")
    print("preview written: D:\\dnshe\\icon_preview.png")


if __name__ == "__main__":
    main()
