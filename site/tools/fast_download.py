"""把官网的下载主按钮改成 GitCode 高速直链，官网直连降级成镜像。

原因：官网 srv2 直连实测只有 78~850 KB/s，GitCode 是 5.8 MB/s，差 7 倍以上。
"""
import io

P = r"D:\dnshe-site\index.html"

GC = "https://gitcode.com/gcw_YiSvwnI0/ShizukuDomainBox/releases/download/v2.0.0"
GH = "https://github.com/ynlp0624/ShizukuDomainBox/releases/download/v2.0.0"
APK = "ShizukuDomainBox-Android-v2.0.0.apk"
EXE = "ShizukuDomainBox-Setup-v2.0.0.exe"

s = io.open(P, "r", encoding="utf-8").read()
orig = s
stats = []


def rep(a, b, label, count=0):
    global s
    n = s.count(a)
    if n == 0:
        return
    s = s.replace(a, b)
    stats.append(f"{label} ×{n}")


# ---------------------------------------------------------------- 主按钮 → GitCode
rep(f'href="downloads/{APK}" download>',
    f'href="{GC}/{APK}" target="_blank" rel="noopener noreferrer">', "APK 主按钮→GitCode")
rep(f'href="downloads/{EXE}" download>',
    f'href="{GC}/{EXE}" target="_blank" rel="noopener noreferrer">', "EXE 主按钮→GitCode")

# ---------------------------------------------------------------- 文案
rep("<small>APK · 11.2 MB</small>", "<small>APK · 11.2 MB · 国内高速</small>", "Hero 安卓小字")
rep("<small>安装程序 · 104 MB</small>", "<small>安装程序 · 104 MB · 国内高速</small>", "Hero 小字")
rep("从官网下载 APK", "下载 APK", "卡片按钮文案")
rep("从官网下载安装程序", "下载安装程序", "卡片按钮文案")

# ---------------------------------------------------------------- 镜像行 → 官网直连 + GitHub
OLD_APK_MIRROR = (
    '        <div class="mirrors">\n'
    '          <span>镜像：</span>\n'
    f'          <a href="{GC}/{APK}" target="_blank" rel="noopener noreferrer">GitCode（国内快）</a>\n'
    f'          <a href="{GH}/{APK}" target="_blank" rel="noopener noreferrer">GitHub</a>\n'
    '        </div>\n')
NEW_APK_MIRROR = (
    '        <div class="mirrors">\n'
    '          <span>其它源：</span>\n'
    f'          <a href="downloads/{APK}" download>官网直连</a>\n'
    f'          <a href="{GH}/{APK}" target="_blank" rel="noopener noreferrer">GitHub</a>\n'
    '        </div>\n')
rep(OLD_APK_MIRROR, NEW_APK_MIRROR, "APK 镜像行")

OLD_EXE_MIRROR = (
    '        <div class="mirrors">\n'
    '          <span>镜像：</span>\n'
    f'          <a href="{GC}/{EXE}" target="_blank" rel="noopener noreferrer">GitCode（国内快）</a>\n'
    f'          <a href="{GH}/{EXE}" target="_blank" rel="noopener noreferrer">GitHub</a>\n'
    '        </div>\n')
NEW_EXE_MIRROR = (
    '        <div class="mirrors">\n'
    '          <span>其它源：</span>\n'
    f'          <a href="downloads/{EXE}" download>官网直连</a>\n'
    f'          <a href="{GH}/{EXE}" target="_blank" rel="noopener noreferrer">GitHub</a>\n'
    '        </div>\n')
rep(OLD_EXE_MIRROR, NEW_EXE_MIRROR, "EXE 镜像行")

if s != orig:
    io.open(P, "w", encoding="utf-8", newline="\n").write(s)

for x in stats:
    print("  ✓", x)
print("完成" if stats else "⚠ 没有替换")
