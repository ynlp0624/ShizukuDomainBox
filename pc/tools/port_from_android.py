"""把安卓版的纯逻辑代码机械移植到 PC 工程的 core 模块。

只改 package 声明和少数 import —— 这几个文件本身不含任何 Android API，
在 JVM 上可以直接复用（OkHttp / kotlinx.serialization / Compose runtime 都是跨平台的）。
"""
import os
import re
import io

AND = r"D:\dnshe\app\src\main\java\io\nekoshizuku\dnshe"
PC = r"D:\dnshe-pc"


def read(p):
    with io.open(p, "r", encoding="utf-8") as f:
        return f.read()


def write(p, s):
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with io.open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(s)
    print("  wrote", p, len(s), "bytes")


def repackage(text: str, new_pkg: str, import_fixes: dict) -> str:
    text = re.sub(r"^package .*$", "package " + new_pkg, text, count=1, flags=re.M)
    for a, b in import_fixes.items():
        text = text.replace(a, b)
    return text


JOBS = [
    # 源文件, 目标文件, 新包名, import 修正
    (
        rf"{AND}\data\Models.kt",
        rf"{PC}\core\src\main\kotlin\io\nekoshizuku\dnshe\core\Models.kt",
        "io.nekoshizuku.dnshe.core",
        {},
    ),
    (
        rf"{AND}\data\DnsheApi.kt",
        rf"{PC}\core\src\main\kotlin\io\nekoshizuku\dnshe\core\DnsheApi.kt",
        "io.nekoshizuku.dnshe.core",
        {},
    ),
    (
        rf"{AND}\data\RootDomains.kt",
        rf"{PC}\core\src\main\kotlin\io\nekoshizuku\dnshe\core\RootDomains.kt",
        "io.nekoshizuku.dnshe.core",
        {},
    ),
    (
        rf"{AND}\i18n\Strings.kt",
        rf"{PC}\desktop\src\main\kotlin\io\nekoshizuku\dnshe\desktop\i18n\Strings.kt",
        "io.nekoshizuku.dnshe.desktop.i18n",
        {"import io.nekoshizuku.dnshe.data.Lang": "import io.nekoshizuku.dnshe.core.Lang"},
    ),
]

for src, dst, pkg, fixes in JOBS:
    print("porting", os.path.basename(src))
    text = read(src)
    write(dst, repackage(text, pkg, fixes))

print("done")
