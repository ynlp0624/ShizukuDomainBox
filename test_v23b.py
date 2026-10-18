"""v2.3.0 测试：关掉更新说明后测自动识别邮箱。"""
import re
import subprocess
import time

ADB = r"D:\Android\Sdk\platform-tools\adb.exe"
TMP = r"C:\Users\mynlp\AppData\Local\Temp\_t23.xml"


def sh(*a):
    return subprocess.run([ADB, "shell"] + list(a), capture_output=True, text=True,
                          encoding="utf-8", errors="replace")


def dump():
    sh("uiautomator", "dump", "/sdcard/_t23.xml")
    subprocess.run([ADB, "pull", "/sdcard/_t23.xml", TMP], capture_output=True)
    sh("rm", "/sdcard/_t23.xml")
    try:
        return open(TMP, encoding="utf-8").read()
    except Exception:
        return ""


def edits(xml):
    out = []
    for m in re.finditer(r'<node[^>]*class="android.widget.EditText"[^>]*>', xml):
        v = m.group(0)
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', v)
        t = re.search(r'text="([^"]*)"', v)
        if b:
            x1, y1, x2, y2 = map(int, b.groups())
            out.append((t.group(1) if t else "", (x1 + x2) // 2, (y1 + y2) // 2))
    return out


def texts(xml):
    return [t for t in re.findall(r'text="([^"]+)"', xml) if t.strip()]


def tap_label(xml, label):
    for m in re.finditer(r'<node[^>]*>', xml):
        v = m.group(0)
        if f'text="{label}"' in v:
            b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', v)
            if b:
                x1, y1, x2, y2 = map(int, b.groups())
                sh("input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
                return True
    return False


# 关掉更新说明
print("=== 关更新说明 ===")
sh("input", "tap", "769", "2182")
time.sleep(2)

xml = dump()
print("  当前:", texts(xml)[:6])

# 进邮箱
sh("input", "tap", "756", "2253")
time.sleep(3)
xml = dump()
print("  邮箱页:", texts(xml)[:6])

# 点添加邮箱
ok = tap_label(xml, "添加邮箱") or tap_label(xml, "Add now") or tap_label(xml, "Add mailbox")
print(f"  点添加: {ok}")
time.sleep(3)

xml = dump()
es = edits(xml)
print(f"\n=== 编辑框 {len(es)} 个 ===")
for i, (t, x, y) in enumerate(es):
    print(f"  [{i}] '{t[:20]}' ({x},{y})")

if len(es) < 4:
    print("  对话框没开，看看界面:", texts(xml)[:8])
    raise SystemExit(1)

# 只填令牌
print("\n=== 只填令牌（地址/ID 留空）===")
sh("input", "tap", str(es[1][0]), str(es[1][1]))
time.sleep(0.8)
TOKEN = "lq_lUyHVMgajh44X5ezaw1D0qMEaZYKQsqdeW4OwRAzH_4"
segs = TOKEN.split("_")
for i, seg in enumerate(segs):
    if seg:
        sh("input", "text", seg)
        time.sleep(0.1)
    if i < len(segs) - 1:
        sh("input", "keyevent", "KEYCODE_MINUS")
        time.sleep(0.1)
time.sleep(0.6)
sh("input", "keyevent", "KEYCODE_BACK")
time.sleep(1.5)

xml = dump()
es = edits(xml)
print("填入:")
for i, (t, x, y) in enumerate(es):
    print(f"  [{i}] '{t[:45]}'")

print("\n=== 点保存 ===")
saved = tap_label(xml, "保存") or tap_label(xml, "Save")
print(f"  点击: {saved}")
time.sleep(8)

xml = dump()
print("\n=== 结果 ===")
for t in texts(xml)[:12]:
    print(f"  {t[:70]}")

sh("screencap", "-p", "/sdcard/_r23.png")
subprocess.run([ADB, "pull", "/sdcard/_r23.png", r"D:\dnshe\screens\v23-autodetect.png"],
               capture_output=True)
sh("rm", "/sdcard/_r23.png")
print("\n截图完成")
