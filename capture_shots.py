"""批量截取当前 App 界面，用于官网预览图。"""
import re
import subprocess
import time

ADB = r"D:\Android\Sdk\platform-tools\adb.exe"
OUT = r"D:\dnshe\screens\shots"
TMP = r"C:\Users\mynlp\AppData\Local\Temp\_shot.xml"

import os
os.makedirs(OUT, exist_ok=True)


def sh(*a):
    return subprocess.run([ADB, "shell"] + list(a), capture_output=True, text=True,
                          encoding="utf-8", errors="replace")


def dump():
    sh("uiautomator", "dump", "/sdcard/_shot.xml")
    subprocess.run([ADB, "pull", "/sdcard/_shot.xml", TMP], capture_output=True)
    sh("rm", "/sdcard/_shot.xml")
    try:
        return open(TMP, encoding="utf-8").read()
    except Exception:
        return ""


def shot(name):
    sh("screencap", "-p", "/sdcard/_s.png")
    dst = os.path.join(OUT, name)
    subprocess.run([ADB, "pull", "/sdcard/_s.png", dst], capture_output=True)
    sh("rm", "/sdcard/_s.png")
    size = os.path.getsize(dst) if os.path.exists(dst) else 0
    print(f"  ✓ {name}  {size // 1024} KB")


def tap(x, y, w=1.5):
    sh("input", "tap", str(x), str(y))
    time.sleep(w)


def find_text(xml, keyword, min_y=0):
    for m in re.finditer(r'<node[^>]*>', xml):
        v = m.group(0)
        if keyword in v:
            b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', v)
            if b:
                x1, y1, x2, y2 = map(int, b.groups())
                cy = (y1 + y2) // 2
                if cy > min_y:
                    return ((x1 + x2) // 2, cy)
    return None


print("=== 2. DNS 记录 ===")
xml = dump()
p = find_text(xml, "shizuku.kz.ci")
if p:
    tap(p[0], p[1], 4)
    shot("android-records.png")
    xml = dump()
    txt = re.findall(r'text="([^"]+)"', xml)
    print("   ", " | ".join(t for t in txt[:8] if t.strip()))
    # 返回
    sh("input", "keyevent", "KEYCODE_BACK")
    time.sleep(2)
else:
    print("   没找到域名")

print("\n=== 3. 工具页 ===")
tap(535, 2215, 3)
shot("android-tools.png")
xml = dump()
txt = re.findall(r'text="([^"]+)"', xml)
print("   ", " | ".join(t for t in txt[:8] if t.strip()))

print("\n=== 4. 邮箱收件箱 ===")
tap(756, 2215, 6)
shot("android-mail-inbox.png")
xml = dump()
txt = re.findall(r'text="([^"]+)"', xml)
print("   ", " | ".join(t for t in txt[:6] if t.strip()))

print("\n=== 5. 邮件详情 ===")
xml = dump()
# 点第一封邮件（y 在 600-800 之间）
p = None
for m in re.finditer(r'<node[^>]*clickable="true"[^>]*>', xml):
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', m.group(0))
    if b:
        x1, y1, x2, y2 = map(int, b.groups())
        cy = (y1 + y2) // 2
        if 500 < cy < 800:
            p = ((x1 + x2) // 2, cy)
            break
if p:
    tap(p[0], p[1], 5)
    shot("android-mail-detail.png")
    sh("input", "keyevent", "KEYCODE_BACK")
    time.sleep(2)
else:
    print("   没找到邮件")

print("\n=== 6. 写邮件 ===")
xml = dump()
# 右上角写邮件按钮
p = None
for m in re.finditer(r'<node[^>]*clickable="true"[^>]*>', xml):
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', m.group(0))
    if b:
        x1, y1, x2, y2 = map(int, b.groups())
        cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
        if cy < 350 and cx > 900:
            p = (cx, cy)
            break
if p:
    tap(p[0], p[1], 3)
    shot("android-mail-compose.png")
    sh("input", "keyevent", "KEYCODE_BACK")
    time.sleep(2)
else:
    print("   没找到写邮件按钮")

print("\n=== 7. 设置页 ===")
tap(967, 2215, 3)
shot("android-settings.png")

print("\n完成")
