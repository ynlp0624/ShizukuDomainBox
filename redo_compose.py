"""重启 App，重新填一份干净的写邮件示例。"""
import re
import subprocess
import time

ADB = r"D:\Android\Sdk\platform-tools\adb.exe"
TMP = r"C:\Users\mynlp\AppData\Local\Temp\_cmp.xml"


def sh(*a):
    return subprocess.run([ADB, "shell"] + list(a), capture_output=True, text=True,
                          encoding="utf-8", errors="replace")


def dump():
    sh("uiautomator", "dump", "/sdcard/_cmp.xml")
    subprocess.run([ADB, "pull", "/sdcard/_cmp.xml", TMP], capture_output=True)
    sh("rm", "/sdcard/_cmp.xml")
    try:
        return open(TMP, encoding="utf-8").read()
    except Exception:
        return ""


def edits(xml):
    out = []
    for m in re.finditer(r'<node[^>]*class="android.widget.EditText"[^>]*>', xml):
        v = m.group(0)
        t = re.search(r'text="([^"]*)"', v)
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', v)
        if b:
            x1, y1, x2, y2 = map(int, b.groups())
            out.append(((x1 + x2) // 2, (y1 + y2) // 2))
    return out


print("=== 重启 App ===")
sh("am", "force-stop", "io.nekoshizuku.dnshe")
time.sleep(1)
sh("am", "start", "-n", "io.nekoshizuku.dnshe/.MainActivity")
time.sleep(16)

print("=== 进邮箱 → 写邮件 ===")
sh("input", "tap", "756", "2253")
time.sleep(4)
sh("input", "tap", "986", "197")
time.sleep(3)

xml = dump()
es = edits(xml)
print(f"  {len(es)} 个输入框")
if len(es) < 3:
    print("  写邮件页没打开")
    raise SystemExit(1)

# 收件人
sh("input", "tap", str(es[0][0]), str(es[0][1]))
time.sleep(0.7)
sh("input", "text", "hello")
sh("input", "keyevent", "KEYCODE_AT")
sh("input", "text", "example")
sh("input", "keyevent", "KEYCODE_PERIOD")
sh("input", "text", "com")
time.sleep(0.6)

# 主题
xml = dump()
es = edits(xml)
sh("input", "tap", str(es[1][0]), str(es[1][1]))
time.sleep(0.7)
sh("input", "text", "Hello%sfrom%sShizuku%sDomain%sBox")
time.sleep(0.6)

# 正文 —— 只填一句短的，避免中文输入法干扰
xml = dump()
es = edits(xml)
sh("input", "tap", str(es[2][0]), str(es[2][1]))
time.sleep(0.7)
sh("input", "text", "Trying%sout%sthe%snew%scompose%sscreen")
time.sleep(0.6)

# 收键盘
sh("input", "keyevent", "KEYCODE_BACK")
time.sleep(2)

xml = dump()
print("\n=== 填入结果 ===")
for m in re.finditer(r'<node[^>]*class="android.widget.EditText"[^>]*>', xml):
    t = re.search(r'text="([^"]*)"', m.group(0))
    print(f"  '{t.group(1) if t else ''}'")

sh("screencap", "-p", "/sdcard/_cm.png")
subprocess.run([ADB, "pull", "/sdcard/_cm.png",
                r"D:\dnshe\screens\shots\android-mail-compose.png"], capture_output=True)
sh("rm", "/sdcard/_cm.png")
print("\n已截图")
