"""测试 v2.3.0：只填令牌自动识别邮箱 + 多邮箱选择。"""
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


def find_btn(xml, label):
    for m in re.finditer(r'<node[^>]*>', xml):
        v = m.group(0)
        if label in v and 'clickable="true"' in v:
            b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', v)
            if b:
                x1, y1, x2, y2 = map(int, b.groups())
                return ((x1 + x2) // 2, (y1 + y2) // 2)
    return None


print("=== 1. 清数据并启动 ===")
sh("pm", "clear", "io.nekoshizuku.dnshe")
time.sleep(1)
sh("am", "start", "-n", "io.nekoshizuku.dnshe/.MainActivity")
time.sleep(17)

xml = dump()
# 关更新说明弹窗（如果有「好的」/「确定」）
b = find_btn(xml, "好的") or find_btn(xml, "确定") or find_btn(xml, "OK")
if b:
    sh("input", "tap", str(b[0]), str(b[1]))
    time.sleep(1.5)

print("=== 2. 进邮箱 → 添加 ===")
sh("input", "tap", "756", "2253")
time.sleep(3)
xml = dump()
b = find_btn(xml, "添加邮箱") or find_btn(xml, "去添加")
if not b:
    t = texts(xml)
    print("  当前:", t[:5])
else:
    sh("input", "tap", str(b[0]), str(b[1]))
    time.sleep(2.5)

xml = dump()
es = edits(xml)
print(f"=== 3. 编辑框 {len(es)} 个 ===")
for i, (t, x, y) in enumerate(es):
    print(f"  [{i}] '{t[:20]}' ({x},{y})")

if len(es) < 4:
    raise SystemExit("对话框没开")

# 只填令牌，地址和 mailboxId 都留空
print("=== 4. 只填令牌 ===")
sh("input", "tap", str(es[1][0]), str(es[1][1]))   # token 是第 2 个框
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
print("\n=== 填入结果 ===")
for i, (t, x, y) in enumerate(es):
    print(f"  [{i}] '{t[:50]}'")

print("\n=== 5. 点保存（应自动拉邮箱列表）===")
b = find_btn(xml, "保存") or find_btn(xml, "Save")
if b:
    print(f"  保存 ({b[0]},{b[1]})")
    sh("input", "tap", str(b[0]), str(b[1]))
    time.sleep(7)
else:
    print("  没找到保存")

xml = dump()
print("\n=== 6. 结果界面 ===")
for t in texts(xml)[:12]:
    print(f"  {t[:70]}")

sh("screencap", "-p", "/sdcard/_r23.png")
subprocess.run([ADB, "pull", "/sdcard/_r23.png", r"D:\dnshe\screens\v23-autodetect.png"],
               capture_output=True)
sh("rm", "/sdcard/_r23.png")
print("\n截图: v23-autodetect.png")
