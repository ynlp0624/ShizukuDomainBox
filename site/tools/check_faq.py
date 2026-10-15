"""直接核对「是开源项目吗」这条 FAQ 的内容与位置。"""
import io
import re

s = io.open(r"D:\dnshe-site\index.html", "r", encoding="utf-8").read()

# 找出所有 FAQ 条目的标题，确认顺序与数量
titles = re.findall(r'<summary>([^<]+)<span class="chev">', s)
print(f"FAQ 条目共 {len(titles)} 条：")
for i, t in enumerate(titles, 1):
    print(f"  {i}. {t}")

# 找出开源那条的完整内容
m = re.search(r'<details[^>]*>\s*<summary>是开源项目吗.*?</details>', s, re.S)
if m:
    block = m.group(0)
    print(f"\n开源条目长度 {len(block)} 字符")
    for kw in ["MIT 许可", "github.com/ynlp0624", "gitcode.com/gcw_YiSvwnI0", "提 Issue", "暂未开源", "邮箱"]:
        print(f"  {'✓' if kw in block else '✗'} 含「{kw}」")
else:
    print("\n✗ 找不到开源条目！")

# 页脚
foot = re.search(r'<footer class="foot">.*?</footer>', s, re.S)
if foot:
    f = foot.group(0)
    print("\n页脚：")
    for kw in ["github.com/ynlp0624", "gitcode.com/gcw_YiSvwnI0", "MIT 许可"]:
        print(f"  {'✓' if kw in f else '✗'} 含「{kw}」")
