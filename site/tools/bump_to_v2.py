"""把官网更新到 v2.0.0：版本号 / 文件名 / 大小 / SHA-256 / 镜像链接。"""
import hashlib
import io
import os
import shutil

SITE = r"D:\dnshe-site"
HTML = os.path.join(SITE, "index.html")
DL = os.path.join(SITE, "downloads")

NEW_APK = r"D:\dnshe\app\build\outputs\apk\release\app-release.apk"
NEW_EXE = r"D:\dnshe-pc\dist\ShizukuDomainBox-Setup-v2.0.0.exe"

APK_NAME = "ShizukuDomainBox-Android-v2.0.0.apk"
EXE_NAME = "ShizukuDomainBox-Setup-v2.0.0.exe"


def sha256(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest().upper()


# ---------- 1. 更新 downloads 目录 ----------
os.makedirs(DL, exist_ok=True)
for f in os.listdir(DL):
    os.remove(os.path.join(DL, f))

shutil.copy2(NEW_APK, os.path.join(DL, APK_NAME))
shutil.copy2(NEW_EXE, os.path.join(DL, EXE_NAME))

apk_mb = os.path.getsize(NEW_APK) / 1024 / 1024
exe_mb = os.path.getsize(NEW_EXE) / 1024 / 1024
apk_hash = sha256(NEW_APK)
exe_hash = sha256(NEW_EXE)

print(f"APK  {apk_mb:.2f} MB  {apk_hash}")
print(f"EXE  {exe_mb:.2f} MB  {exe_hash}")

# ---------- 2. 改 index.html ----------
s = io.open(HTML, "r", encoding="utf-8").read()
before = s

REPL = [
    # 文件名
    ("ShizukuDomainBox-Android-v1.1.1.apk", APK_NAME),
    ("ShizukuDomainBox-Setup-v1.0.0.exe", EXE_NAME),
    # 版本号
    ("v1.1.1", "v2.0.0"),
    ('<span class="tag">v1.0.0</span>', '<span class="tag">v2.0.0</span>'),
    # 大小
    ("APK · 7.5 MB", f"APK · {apk_mb:.1f} MB"),
    ("安装程序 · 96 MB", f"安装程序 · {exe_mb:.0f} MB"),
    ("<dd>7.47 MB</dd>", f"<dd>{apk_mb:.2f} MB</dd>"),
    ("<dd>96.1 MB（自带运行时）</dd>", f"<dd>{exe_mb:.1f} MB（自带运行时）</dd>"),
    # 哈希
    ("DBE57E61EADA82B7B79BAE2C241ACCAF13FDDF1CBDF5595C97FAB3BC44D08D7D", apk_hash),
    ("2F8BA201991CDB67008C36977C7F227114223064A0798FE122AB4E77FE265BF5", exe_hash),
]
for a, b in REPL:
    n = s.count(a)
    if n:
        s = s.replace(a, b)
        print(f"  替换 {n} 处: {a[:46]} → {b[:46]}")

# ---------- 3. 每个下载卡加镜像行 ----------
MIRROR_APK = """        <div class="mirrors">
          <span>镜像：</span>
          <a href="https://gitcode.com/gcw_YiSvwnI0/ShizukuDomainBox/releases/download/v2.0.0/%s" target="_blank" rel="noopener noreferrer">GitCode（国内快）</a>
          <a href="https://github.com/ynlp0624/ShizukuDomainBox/releases/download/v2.0.0/%s" target="_blank" rel="noopener noreferrer">GitHub</a>
        </div>
""" % (APK_NAME, APK_NAME)

MIRROR_EXE = """        <div class="mirrors">
          <span>镜像：</span>
          <a href="https://gitcode.com/gcw_YiSvwnI0/ShizukuDomainBox/releases/download/v2.0.0/%s" target="_blank" rel="noopener noreferrer">GitCode（国内快）</a>
          <a href="https://github.com/ynlp0624/ShizukuDomainBox/releases/download/v2.0.0/%s" target="_blank" rel="noopener noreferrer">GitHub</a>
        </div>
""" % (EXE_NAME, EXE_NAME)

anchor_apk = f'          从官网下载 APK\n        </a>\n'
anchor_exe = f'          从官网下载安装程序\n        </a>\n'
if "class=\"mirrors\"" not in s:
    s = s.replace(anchor_apk, anchor_apk + MIRROR_APK, 1)
    s = s.replace(anchor_exe, anchor_exe + MIRROR_EXE, 1)
    print("  已插入镜像链接行")
else:
    print("  镜像行已存在，跳过")

io.open(HTML, "w", encoding="utf-8", newline="\n").write(s)
print(f"\nindex.html {'已更新' if s != before else '未变化'}  ({len(s)} 字节)")
