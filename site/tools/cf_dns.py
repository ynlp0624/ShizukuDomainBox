"""官网 DNS 管理（使用 Cloudflare API Token，可见 7 个 zone）。

用法:
  # Token 从环境变量读取，不要写进文件
  export CF_T=<your-cloudflare-api-token>       # Windows: $env:CF_T="..."
  python cf_dns.py                # dry-run
  python cf_dns.py --apply        # 真正写入
"""
import json
import os
import sys
import urllib.request

# Token 只能从环境变量读；硬编码会被 GitHub 的机密扫描拦截，也不安全
TOKEN = os.environ.get("CF_T")
if not TOKEN:
    print("请先设置环境变量 CF_T（Cloudflare API Token）")
    sys.exit(1)

ORIGIN_IP = "156.239.230.56"
PREFIX = "dnshe"
APPLY = "--apply" in sys.argv

API = "https://api.cloudflare.com/client/v4"

ZONES = {
    # 名字                     zone id                                   站点启用  代理
    "shizuku.plus":         ("ba1557c6c34812f3203640500443427b", True,  False),  # 大陆直连 OK，主域名
    "xn--z7x21yw6r.icu":    ("f3feaed38a51fe539ace4a1b3286bb62", True,  False),
    "xn--z7x21yw6r.site":   ("bb2db94d56b58a88171fc639c0a7a5b5", True,  False),
    "xn--z7x21yw6r.top":    ("10554399d78dd66b3b28379b23e2072b", True,  False),
    # cc.cd 整根在大陆被 SNI 阻断，CF 代理也救不回来，只保留解析（境外可访问）
    "nekoha.cc.cd":         ("ea4ef555d2bc3b251ba715cecf303f17", True,  False),
    # 以下两个 zone 只登记，不给本站用
    "nekoshizuku.cc.cd":    ("9f0435fa9ca8dc0ad5149b9c76fc9ac3", False, False),
    "xn--z7x21yw6r.cc.cd":  ("56e138e95464ca808764e09915c8907a", False, False),
}


def cf(path, method="GET", body=None):
    req = urllib.request.Request(
        API + path,
        data=json.dumps(body).encode() if body else None,
        method=method,
        headers={"Authorization": "Bearer " + TOKEN, "Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return json.loads(e.read().decode())


print("Token 校验:", cf("/user/tokens/verify").get("result", {}).get("status"))

for zone_name, (zone_id, enable, proxied) in ZONES.items():
    fqdn = f"{PREFIX}.{zone_name}"
    tag = "启用" if enable else "跳过"
    print(f"\n=== {fqdn}  [{tag}] ===")
    if not enable:
        print("  该 zone 不用于本站，跳过")
        continue

    existing = cf(f"/zones/{zone_id}/dns_records?type=A&name={fqdn}")
    recs = existing.get("result", []) if existing.get("success") else []

    if recs:
        for rec in recs:
            need = rec["content"] != ORIGIN_IP or rec["proxied"] != proxied
            print(f"  已存在 -> {rec['content']} proxied={rec['proxied']}"
                  + ("  → 需要更新" if need else "  → 无需改动"))
            if need and APPLY:
                res = cf(f"/zones/{zone_id}/dns_records/{rec['id']}", "PUT", {
                    "type": "A", "name": fqdn, "content": ORIGIN_IP,
                    "ttl": 1, "proxied": proxied,
                })
                print("     ", "OK" if res.get("success") else res.get("errors"))
    else:
        print(f"  → 新建 A -> {ORIGIN_IP}  proxied={proxied}")
        if APPLY:
            res = cf(f"/zones/{zone_id}/dns_records", "POST", {
                "type": "A", "name": fqdn, "content": ORIGIN_IP,
                "ttl": 1, "proxied": proxied,
                "comment": "ShizukuDomainBox official site",
            })
            print("     ", "OK" if res.get("success") else res.get("errors"))

print("\n" + ("已应用改动" if APPLY else "dry-run 结束，加 --apply 才真正写入"))
