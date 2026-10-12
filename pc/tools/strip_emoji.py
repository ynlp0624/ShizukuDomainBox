"""把界面里的 emoji 参数机械摘掉，并列出剩余 emoji（写报告文件，避免控制台编码问题）。"""
import io
import os
import re

ROOTS = [
    r"D:\dnshe\app\src\main\java\io\nekoshizuku\dnshe",
    r"D:\dnshe-pc\desktop\src\main\kotlin",
    r"D:\dnshe-pc\core\src\main\kotlin",
    r"D:\dnshe-pc\installer\src\main\kotlin",
]

# emoji = "xxx"  连同前面的逗号/空白一起去掉（支持同一行或独立一行）
PATTERN = re.compile(r'\s*emoji\s*=\s*"[^"]*"\s*,?')

EMOJI = re.compile('[\U0001F000-\U0001FAFF\u2190-\u21FF\u2300-\u27BF\u2B00-\u2BFF\uFE0F\u2705\u274C\u26A0\u2764]')

report = []

for root in ROOTS:
    for dp, dn, fn in os.walk(root):
        for f in fn:
            if not f.endswith(".kt"):
                continue
            path = os.path.join(dp, f)
            text = io.open(path, "r", encoding="utf-8").read()
            new, n = PATTERN.subn("", text)
            if n:
                io.open(path, "w", encoding="utf-8", newline="\n").write(new)
                report.append(f"[strip {n}] {os.path.relpath(path, root)}")

report.append("--- 剩余 emoji ---")
for root in ROOTS:
    for dp, dn, fn in os.walk(root):
        for f in fn:
            if not f.endswith(".kt"):
                continue
            path = os.path.join(dp, f)
            for i, line in enumerate(io.open(path, "r", encoding="utf-8"), 1):
                if EMOJI.search(line):
                    report.append(f"{os.path.relpath(path, root)}:{i}: {line.strip()[:120]}")

io.open(r"D:\dnshe-pc\emoji-report.txt", "w", encoding="utf-8").write("\n".join(report))
print("done, report written")
