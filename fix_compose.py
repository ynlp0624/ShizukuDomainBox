"""清空写邮件页正文并填入干净示例文本。"""
import subprocess
import time

ADB = r"D:\Android\Sdk\platform-tools\adb.exe"


def sh(*a):
    return subprocess.run([ADB, "shell"] + list(a), capture_output=True, text=True,
                          encoding="utf-8", errors="replace")


# 点正文框
sh("input", "tap", "540", "979")
time.sleep(0.8)

# 全选删除：先移到末尾，然后长按选择全部太麻烦，直接按 200 次 DEL
sh("input", "keyevent", "KEYCODE_MOVE_END")
time.sleep(0.3)
for _ in range(200):
    sh("input", "keyevent", "KEYCODE_DEL")
time.sleep(0.8)

# 输入干净文本（用 %s 表示空格）
sh("input", "text", "Trying%sout%sthe%snew%scompose%sscreen")
time.sleep(0.5)

# 收起键盘
sh("input", "keyevent", "KEYCODE_BACK")
time.sleep(1.5)

# 读取当前内容确认
sh("uiautomator", "dump", "/sdcard/_cp.xml")
subprocess.run([ADB, "pull", "/sdcard/_cp.xml",
                r"C:\Users\mynlp\AppData\Local\Temp\_cp.xml"], capture_output=True)
sh("rm", "/sdcard/_cp.xml")

import re
try:
    c = open(r"C:\Users\mynlp\AppData\Local\Temp\_cp.xml", encoding="utf-8").read()
    for m in re.finditer(r'<node[^>]*class="android.widget.EditText"[^>]*>', c):
        t = re.search(r'text="([^"]*)"', m.group(0))
        print(f"  '{t.group(1) if t else ''}'")
except Exception as e:
    print(e)

# 截图
sh("screencap", "-p", "/sdcard/_c.png")
subprocess.run([ADB, "pull", "/sdcard/_c.png",
                r"D:\dnshe\screens\shots\android-mail-compose.png"], capture_output=True)
sh("rm", "/sdcard/_c.png")
print("已重截")
