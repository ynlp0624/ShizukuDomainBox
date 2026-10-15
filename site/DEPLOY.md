# 雫的域名盒 · 官网部署说明

## 线上地址

| 域名 | 大陆可达 | 实测延迟 | 说明 |
|---|---|---|---|
| **https://dnshe.shizuku.plus** | ✅ 直连 | 0.26s | **主域名**（纯 ASCII，最好记） |
| **https://dnshe.猫羽雫.icu** | ✅ 直连 | 0.30s | |
| **https://dnshe.猫羽雫.site** | ✅ 直连 | 0.49s | |
| **https://dnshe.猫羽雫.top** | ✅ 直连 | 0.36s | |
| https://dnshe.nekoha.cc.cd | ❌ | — | `cc.cd` 整根被 SNI 阻断，仅境外可用 |

> punycode 形式：`dnshe.xn--z7x21yw6r.icu` / `.site` / `.top`

### 关于 `cc.cd`

实测结论：**整个 `cc.cd` 根域在大陆被 SNI 阻断**，不是个别子域的问题。

排查过程：CF 边缘 IP（172.67.212.191 / 104.21.58.154）本身能握手成功（返回 530），
但任何 `*.cc.cd` 的 TLS 握手都会失败 —— 包括 `nekoha.cc.cd`、`nekoshizuku.cc.cd`、
`api.nekoha.cc.cd`、`猫羽雫.cc.cd`。所以**开 Cloudflare 代理也救不回来**，
因为 SNI 在到达 CF 之前就被拦了。

曾把 `dnshe.nekoha.cc.cd` 临时开过 CF 代理，确认无效后已还原为 DNS only。

## 部署位置

| 项 | 值 |
|---|---|
| 服务器 | srv2 · 156.239.230.56 |
| 站根 | `/www/wwwroot/dnshe.nekoha.cc.cd/`（目录名沿用，不影响访问） |
| nginx | `/etc/nginx/sites-enabled/newzone-dnshe.conf` |
| 证书 | `/etc/letsencrypt/live/newzones/`（29 SAN 共用一证，2027-01-08 到期） |
| 日志 | `/www/wwwlogs/newzone-dnshe.log` · `.error.log` |
| CF Token | 2026-10-10 老婆给的新 token（见下） |

### Cloudflare Token

当前使用的 token 可见 **7 个 zone**：

```
nekoha.cc.cd · nekoshizuku.cc.cd · shizuku.plus
猫羽雫.cc.cd · 猫羽雫.icu · 猫羽雫.site · 猫羽雫.top
```

权限为 Zone:DNS:Edit + Zone:Zone:Read（无账号级权限，`/user` 与 `/accounts` 均 403）。
DNS 操作统一走 `tools/cf_dns.py`（默认 dry-run，加 `--apply` 才写入）。

## 设计说明（HyperOS / Miuix 视觉语言）

2026-10-12 改版：官网视觉与安卓版、PC 版**完全统一**。

### 配色来源

**不是拍脑袋定的**，而是从 Miuix 0.9.4 的 `Colors.kt` 里提取的真实 HyperOS 原值：

| 角色 | 浅色 | 深色 |
|---|---|---|
| 页面底（surface） | `#F7F7F7` | `#242424` |
| 卡片底（surfaceContainer 系） | `#EBEBEB` | `#2D2D2D` |
| 文字（onSurface） | `#191C22` | `#F2F2F2` |
| 次要文字（onBackgroundVariant） | `#8C93B0` | `#787E96` |
| 描边（outline） | `#D9D9D9` | `#404040` |
| 分割线（dividerLine） | `#E0E0E0` | `#393939` |
| 错误（error） | `#E94634` | `#F12522` |

主色沿用品牌蓝 `#3E6FD9`（Miuix 默认是 `#3482FF`，保留产品识别度）。

提取脚本：`tools/extract_miuix_colors.py`（读 `D:\miuix-trial\api-ref\Colors.kt`）

### 关键视觉特征（对照 HyperOS）

| 特征 | 做法 |
|---|---|
| **扁平化** | 卡片**无边框、无阴影**，纯色填充 —— 这是 HyperOS 最明显的特征 |
| **灰卡白底** | 浅色模式是「近白页面 + 浅灰卡片」（不是 Material 的白卡灰底） |
| **胶囊控件** | 按钮、Tab 分段控件、标签全部 `border-radius: 999px` |
| **大圆角** | 卡片 18~22px，比 Material 的 12~16px 更大 |
| **克制的阴影** | 只在浮动卡片和手机模型上用，且透明度很低 |
| **小标题** | 对应 Miuix 的 `SmallTitle`：小字号 + 主题色 + 适当留白 |

### 界面 Tab 组件

对应 Miuix 的 `TabRow`：外层胶囊容器（浅灰底 + 内边距），
选中项是**白色胶囊 + 主题色文字**，未选中是灰色文字。

### 零 emoji

所有图标都是手写的内联 SVG（24×24 描边风格，`stroke-width:1.7`），
favicon 也是矢量图（盒子 + 经纬地球，呼应「域名盒」）。

## 目录结构

```
站点根/
├── index.html                单页官网
├── assets/
│   ├── style.css             样式（含亮/暗主题、响应式）
│   ├── app.js                交互（主题切换、Tab、滚动动画、FAQ 手风琴）
│   ├── favicon.svg           手绘 SVG 图标（盒子 + 经纬地球）
│   ├── logo-{64,128,256}.png 应用标志
│   └── shots/                界面截图
│       ├── android-*.png     安卓 6 张
│       └── pc-*.png          Windows 2 张
└── downloads/
    ├── ShizukuDomainBox-Android-v1.1.1.apk        7.47 MB
    └── ShizukuDomainBox-Setup-v1.0.0.exe         96.1 MB
```

## 设计说明

- **零 emoji**：所有图标都是手写的内联 SVG（24×24 描边风格，`stroke-width:1.7`，
  圆角端点），另有独立 favicon.svg
- **亮/暗双主题**：默认跟随系统，可手动切换并记住；支持 `?theme=dark` 强制指定
- **响应式**：1180px 内容宽，1000px / 760px 两个断点
- **色板**：`--brand:#3E6FD9`，与 App 界面同源

## 本地预览

```bat
cd D:\dnshe-site
python -m http.server 8080
rem 浏览器打开 http://127.0.0.1:8080
```

无头渲染截图（改完样式快速看效果）：
```bat
"C:\Program Files\Google\Chrome\Application\chrome.exe" --headless=new --disable-gpu ^
  --hide-scrollbars --window-size=1440,2700 --virtual-time-budget=9000 ^
  --screenshot=out.png "file:///D:/dnshe-site/index.html"
```

## 更新流程

```bat
rem 1. 重新生成素材（换版本时先改 prepare_assets.py 里的映射）
python tools\prepare_assets.py

rem 2. 上传网页本体
cd D:\dnshe-site && tar.exe -a -c -f site-web.tar.gz index.html assets downloads\*.apk
scp site-web.tar.gz srv2:/tmp/
ssh srv2 "cd /www/wwwroot/dnshe.nekoha.cc.cd && tar -xzf /tmp/site-web.tar.gz && chown -R www:www ."

rem 3. 大文件单独传
scp downloads\ShizukuDomainBox-Setup-v1.0.0.exe srv2:/www/wwwroot/dnshe.nekoha.cc.cd/downloads/

rem 4. 校验
ssh srv2 "cd /www/wwwroot/dnshe.nekoha.cc.cd/downloads && sha256sum *"
```

## DNS 记录

`tools/cf_dns.py`（dry-run 默认，加 `--apply` 才写入）。

本站启用 5 条 A 记录 → `156.239.230.56`，全部 **DNS only**（直连，不走 CF 代理，
避免 96 MB 安装包被 CF 免费版限制，也避免无谓的 CF 带宽消耗）：

```
dnshe.shizuku.plus          ← 主域名
dnshe.nekoha.cc.cd          ← 保留解析（境外可用）
dnshe.xn--z7x21yw6r.icu
dnshe.xn--z7x21yw6r.site
dnshe.xn--z7x21yw6r.top
```

`nekoshizuku.cc.cd` 与 `猫羽雫.cc.cd` 两个 zone 只登记在脚本里，本站不用
（同属 `cc.cd` 根，大陆同样不可达）。

## 验收记录（2026-10-10）

| 项 | 结果 |
|---|---|
| 5 个域名 HTTPS | 4 个 200（`shizuku.plus` 0.26s 最快），`nekoha.cc.cd` 因 SNI 阻断不通 |
| HTML/CSS/JS/SVG/PNG | 全部 200，Content-Type 正确 |
| 安装包 | 206 Partial Content，断点续传可用 |
| **APK 真实下载校验** | 7.47 MB / 8.6 秒 / 892 KB/s，**SHA-256 与本地逐字节一致** |
| **服务器文件校验** | Setup.exe SHA-256 与本地一致 |
| 证书 | 29 SAN，2027-01-08 到期 |

## 下载源策略

**主按钮全部走 GitCode，官网直连降级成镜像。**

原因：官网 srv2 是自建源站，实测 **78 ~ 850 KB/s**；GitCode 实测 **5000 KB/s 以上**，
差 7 倍以上。用户点主按钮就应该是最快的路径。

| 位置 | 指向 |
|---|---|
| Hero「下载安卓版」/「下载 Windows 版」 | **GitCode**（小字标注「国内高速」）|
| 下载卡片的主按钮 | **GitCode** |
| 底部 CTA 区的两个按钮 | **GitCode** |
| 下载卡片的「其它源」行 | 官网直连（本地文件）/ GitHub |

> 改这个的脚本：`tools/fast_download.py`
>
> 页面上的 `downloads/` 只剩 **2 处**（两个「官网直连」镜像链接），其余 6 处都是 GitCode 直链。

**保留官网直连的价值**：GitCode 挂了 / 被墙时还有个兜底，而且官网站点本身也是分发的信任锚点。

## 已知问题

- **`cc.cd` 整根在大陆被 SNI 阻断**，CF 代理无效（SNI 在到 CF 之前就被拦）。
  该域名只对境外用户可用，主域名请用 `dnshe.shizuku.plus`。
- 安装包 103 MB，**超过 CF 免费版 100 MB 缓存上限**。目前所有记录都是 DNS only，
  不经过 CF，所以不受影响；如果以后要开 CF 代理，记得大文件会被拒缓存。
- **官网 srv2 直连带宽有限**（实测 78~850 KB/s），所以下载主按钮不要指向它。

## 更新记录

### 2026-10-12 · v2.0.0 发布 + 开源

- 版本号全部更新到 **v2.0.0**（APK 11.24 MB / exe 103.6 MB），SHA-256 同步更新
- 每个下载卡加了**镜像行**：GitCode（国内快）/ GitHub
- 修正 FAQ 里**过时的「暂未开源」说法** —— 项目已开源，改成 MIT 许可 + 两个仓库链接
- 修正 FAQ 里提到的「下方邮箱」—— 页脚根本没有邮箱，改成「到仓库提 Issue」
- 页脚加了 GitHub / GitCode 入口，并标注「开源项目（MIT 许可）」

> 批量改版本号的脚本：`tools/bump_to_v2.py`
> 核对 FAQ 内容的脚本：`tools/check_faq.py`

## 发布新版本时改这些

1. `index.html`：版本号、文件名、大小、SHA-256（共 4 处版本 + 2 处哈希）
2. `downloads/`：替换两个安装包
3. `tools/prepare_assets.py`：如果截图有更新
4. `tools/cf_dns.py` 里的 `ZONES`：如果新增/停用域名

## 证书续期

certbot 的定时任务已存在，`newzones` 证书续期时 28 个域名会一起续，无需额外操作。
手动验证：
```bat
ssh srv2 "certbot renew --dry-run --cert-name newzones"
```
