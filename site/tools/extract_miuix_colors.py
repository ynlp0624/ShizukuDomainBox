"""从 Miuix 的 Colors.kt 里精确提取 light/dark 两套配色原值。"""
import io
import re

SRC = r"D:\miuix-trial\api-ref\Colors.kt"
text = io.open(SRC, "r", encoding="utf-8").read()


def extract(fn_name):
    """抓出 fun xxx(...) : Colors { ... } 的函数体"""
    m = re.search(r"fun " + fn_name + r"\((.*?)\n\)\s*:\s*Colors", text, re.S)
    if not m:
        return {}
    body = m.group(1)
    out = {}
    for line in body.splitlines():
        mm = re.match(r"\s*(\w+)\s*:\s*Color\s*=\s*Color\(0x([0-9A-Fa-f]{8})\)", line)
        if mm:
            out[mm.group(1)] = "#" + mm.group(2)[2:].upper()
    return out


light = extract("lightColorScheme")
dark = extract("darkColorScheme")

KEYS = [
    "primary", "onPrimary", "primaryVariant", "primaryContainer", "onPrimaryContainer",
    "background", "onBackground", "onBackgroundVariant",
    "surface", "onSurface", "surfaceVariant", "onSurfaceSecondary",
    "onSurfaceVariantSummary", "onSurfaceVariantActions",
    "surfaceContainer", "onSurfaceContainer", "surfaceContainerHigh", "surfaceContainerHighest",
    "secondary", "secondaryContainer", "onSecondaryContainer",
    "error", "onError", "errorContainer", "onErrorContainer",
    "outline", "dividerLine", "disabledOnSurface",
]

print(f"{'角色':<28} {'浅色':<12} {'深色':<12}")
print("-" * 54)
for k in KEYS:
    lv = light.get(k, "—")
    dv = dark.get(k, "—")
    print(f"{k:<28} {lv:<12} {dv:<12}")

print(f"\n浅色字段数 {len(light)} / 深色字段数 {len(dark)}")

# 输出成 CSS 变量片段
css = ["/* 提取自 Miuix 0.9.4 的 Colors.kt（HyperOS 原生配色） */", ":root {"]
for k in KEYS:
    if k in light:
        var = re.sub(r"([A-Z])", r"-\1", k).lower()
        css.append(f"  --miuix-{var}: {light[k]};")
css.append("}")
css.append("")
css.append('[data-theme="dark"] {')
for k in KEYS:
    if k in dark:
        var = re.sub(r"([A-Z])", r"-\1", k).lower()
        css.append(f"  --miuix-{var}: {dark[k]};")
css.append("}")
io.open(r"D:\dnshe-site\tools\miuix-colors.css", "w", encoding="utf-8", newline="\n").write("\n".join(css))
print("\nCSS 变量已写入 D:\\dnshe-site\\tools\\miuix-colors.css")
