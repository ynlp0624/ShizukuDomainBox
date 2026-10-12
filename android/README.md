# 雫的域名盒 · Shizuku Domain Box

给 DNSHE 免费域名做的多账号管理 App。Kotlin + Jetpack Compose 手搓，无 WebView，7.3 MB。

![图标](icon_preview.png)

---

## 一、成品在哪

| 文件 | 说明 |
|---|---|
| `dist/雫的域名盒-v1.1.0-release.apk` | **可直接安装的 APK**（已用正式 keystore 签名） |
| `keystore/dnshe-release.jks` | 签名密钥库，**以后升级必须用它，务必自己备份** |
| `keystore/README.md` | 密钥库口令等信息 |
| `app/` | 完整 Kotlin 源码工程 |
| `screens/` | 模拟器实测截图 |

安装：把 APK 传到手机上点安装（首次要允许「安装未知来源应用」）。
用 adb：`adb install -r 雫的域名盒-v1.1.0-release.apk`

- 包名：`io.nekoshizuku.dnshe`
- 版本：1.1.0 (versionCode 2)
- 支持：Android 8.0 (API 26) 及以上
- SHA256：`F839A436EACF50338480E328512504D9E6916ADAFA6A6F456D5E7500D6CC6965`

> 从 1.0.0 升级：同一个签名，直接覆盖安装即可，账号数据不会丢。

---

## 二、功能

### 多账号管理（核心）
- 添加 / 编辑 / 删除账号，点一下就能切换当前账号
- 每个账号可存：备注名、**常用根域名**、备注
- **API Key / Secret 用 Android Keystore 加密**（AES-256-GCM）存在本机，密钥不出安全硬件
- 从剪贴板自动识别 `cfsd_...` 密钥一键添加
- 备份导出 / 导入（JSON），支持用**口令加密**备份文件（PBKDF2-HMAC-SHA256 + AES-256-GCM）
- 内置「测试连接」按钮，填完密钥能立刻验证对不对

### 域名管理
- 子域名列表：搜索、下拉加载更多、状态标签、到期/创建时间
- 注册子域名、续期、删除
- 配额查询（环形进度：已用 / 总额度 / 基础额度 / 邀请奖励）
- 点进域名看详情 + 该域名下全部 DNS 记录

### 根域名选择 & 外部域名标注（v1.1.0 新增）
官网的免费根域名分两类，**第三方（外部）根域名的稳定性、可用性、安全性、续期政策都没保障**，
所以 App 里处处标注清楚：

| 类型 | 根域名 | 标记 |
|---|---|---|
| 官方 | `l.cd` `us.ci` `cc.cd` `bot.cd` `de5.net` `ccwu.cc` `ddns.ge` `bbroot.com` | 蓝色「官方」标签 |
| 外部（第三方） | `kz.ci` `xyz.ci` `pc.ci` | 红色「外部」标签 |

- 注册页把根域名输入框换成了**分组选择器**：官方一组、外部一组，外部整体标红并附风险提示
- 选到外部根域名时，输入框变红 + 弹出整块警告卡，说明是第三方提供、随时可能失效
- **域名列表**里，根域名属于外部的域名会带红色「外部」小标签（官方域名不加标签，保持清爽）
- **域名详情页**同样显示外部域名警告
- 根域名清单来自官网注册页；官网以后新上线的域名，可以用注册页的「手动输入根域名」直接填，
  输入后会提示「不在已知清单里，请先确认」，不会硬拦

代码在 `app/src/main/java/io/nekoshizuku/dnshe/data/RootDomains.kt`（清单）和
`ui/RootDomainUi.kt`（标签 / 警告卡 / 选择器）。要加新根域名，改 `RootDomains.kt` 里的两个列表即可。

### DNS 记录
- 支持 **A / AAAA / CNAME / MX / TXT / NS / SRV / CAA** 全部类型
- 增 / 删 / 改（MX 带优先级，SRV 带权重/端口/目标，CAA 带 flag/tag/value）
- 每条记录显示 TTL、优先级、解析线路、状态

### 工具箱
- 配额查询
- **API 密钥管理**：列出全部密钥、创建（可设 IP 白名单）、重置 Secret、删除；新建的 Secret 支持「存为新账号」
- **WHOIS 查询**：查任意域名的注册状态、注册商邮箱、NS
- **永久升级中心**：助力进度、可升级域名、我的申请、助力记录、实时升级播报，支持提交申请 / 邀请码助力 / 取消申请

### 设置
- 主题：浅色 / 深色 / 跟随系统 + Android 12 动态取色
- 界面语言：简体中文 / English / 跟随系统（**即时切换，不用重启**）
- API 地址可自定义（默认 `https://api005.dnshe.com/index.php`）
- 超时时间、每页条数、打开时自动刷新
- 默认根域名、默认 TTL
- 剪贴板识别开关
- 请求日志（最近 300 条，**不含密钥**）、清空日志、清空全部数据、恢复默认设置
- 关于页：版本、包名、一键打开官网 / 注册页 / API 文档

> 注册账号是刻意不做在 App 里的：点首页的「打开注册页面」会跳到
> <https://my.dnshe.com/register.php?language=chinese>，注册完拿 API 密钥回来添加账号。

---

## 三、怎么构建

工具链都在 `D:\Android`（详见 `D:\Android\模拟器使用说明.md`）：

```bat
set JAVA_HOME=D:\Android\jdk-17
set ANDROID_HOME=D:\Android\Sdk
D:\Android\gradle-8.7\bin\gradle.bat -p D:\dnshe assembleRelease
```

产物：`app\build\outputs\apk\release\app-release.apk`

签名配置读根目录的 `keystore.properties`（已配好，口令在里面）。

### 工程结构

```
app/src/main/java/io/nekoshizuku/dnshe/
├── AppLinks.kt            官网/文档链接常量
├── DnsheApp.kt            Application 入口
├── MainActivity.kt        单 Activity
├── data/
│   ├── Models.kt          API V2.0 全部数据模型 + 错误结构
│   ├── DnsheApi.kt        OkHttp + kotlinx.serialization 客户端
│   └── Storage.kt         Keystore 加密、账号存储、设置、日志、备份
├── i18n/Strings.kt        中英双语文案表 + 运行时切换
└── ui/
    ├── Theme.kt           蓝白配色 Material 3
    ├── Common.kt          公共组件（卡片/表单/对话框…）
    ├── AppRoot.kt         导航骨架 + 底部四个 Tab
    ├── DomainsScreen.kt    域名列表 / 注册 / 详情 + DNS 记录增删改
    ├── AccountsScreen.kt   账号管理 + 备份导入导出 + 剪贴板识别
    ├── ToolsScreens.kt     配额 / API 密钥 / WHOIS / 永久升级
    └── SettingsScreen.kt   设置 / 请求日志 / 关于
```

---

## 四、实测记录（都是真机真账号跑的）

用老婆给的测试账号在 Android 14 模拟器上完整验证过：

| 项目 | 结果 |
|---|---|
| 启动 / 各页面切换 | 无崩溃 |
| 配额查询 | `available 2 / total 3` ✓ |
| 子域名列表 | `mynmail.bbroot.com`，状态「正常」✓ |
| 域名详情 | ID `7477265796`、4 条 DNS 记录 ✓ |
| **DNS 记录创建** | App 建的 `shizuku-app-test A 1.2.3.4` 服务器端确认存在 ✓ |
| **DNS 记录删除** | 删掉后服务器回到 4 条 ✓ |
| API 密钥列表 | 密钥「猫羽雫」，调用次数 35 ✓ |
| WHOIS | `ns1.dnshe.com` / `ns2.dnshe.com`、注册邮箱 ✓ |
| 永久升级中心 | 助力进度 0/5、可升级域名、实时播报 ✓ |
| 备份导出 | 文件写进 Downloads，JSON 结构正确 ✓ |
| 备份导入 | 识别并合并成功 ✓ |
| 语言切换 | 中↔英即时生效 ✓ |
| 账号持久化 | 覆盖安装后账号仍在（Keystore 加密有效）✓ |
| **外部域名标注**（v1.1.0） | 列表里 `shizuku.kz.ci` 带红色「外部」标签、`mynmail.bbroot.com` 无标签 ✓ |
| **根域名选择器**（v1.1.0） | 官方 8 个 / 外部 3 个分两组，外部标红 ✓ |
| **外部域名警告**（v1.1.0） | 选 `kz.ci` 弹出警告卡；选官方 `bot.cd` 显示「官方」标签且无警告 ✓ |

测试期间在服务器上建的临时 DNS 记录**已全部清理干净**，账号域名数量和配额与测试前一致。

---

## 五、实测中踩到并修掉的坑

文档和真实接口不一致的地方（**API 文档 V2.0 有几处写错了**）：

1. **`id` 超过 32 位整数**：真实子域名 id 是 `7477265796`，DNS 记录 id 是 `602416333961508`，
   文档示例里是小整数。模型必须用 `Long`，否则 JSON 解析直接失败。
2. **子域名 `status` 真实值是 `Registered`**，不是文档写的 `active`（DNS 记录才是 `active`）。
3. **WHOIS 实际需要密钥**：文档说「默认不需要 API Key」，实测不带密钥直接返回 401
   `auth_invalid_credentials`。App 里改成默认带上当前账号的密钥。
4. **`permanent_upgrade/list` 返回结构完全不同**：文档只写了 `requests / assist_logs /
   eligible_subdomains`，真实返回是 `assist_required`、`eligible_domains`、
   `recent_success_feed`、`pending_count` 等一大堆字段。改成按 key 动态渲染。
5. **`dns_records` 更新会换新的 `record_id`**：所以 App 用稳定的内部 `id` 做定位。
6. 限流文档一处写 30/min、一处写 60/min，以响应为准。

UI 上修的问题：

- 对话框窗口默认会吃掉 IME inset，导致键盘弹起时「保存」被挡住 →
  给 Dialog 加 `decorFitsSystemWindows = false` + `imePadding()`，按钮固定悬浮在键盘上方。
- 对话框是独立窗口，会把 Activity 的 Snackbar 挡在后面 →
  表单类对话框改成**行内显示错误信息**（服务器返回什么错一眼就能看到）。
- 空状态按钮文案、剪贴板按钮文案重复等小问题。

## 五之二、版本历史

- **v1.1.0**（versionCode 2）：新增根域名选择器与外部域名标注（本页「根域名选择 & 外部域名标注」一节）
- **v1.0.0**（versionCode 1）：首个版本

---

## 六、注意事项

- **`keystore/dnshe-release.jks` 和 `keystore.properties` 千万别丢**，丢了就没法覆盖升级，只能卸载重装（数据会丢）。也别传到公开仓库。
- APK 里**不含任何密钥**，密钥只存在手机本机（Keystore 加密）和你的备份文件里。
- 换手机请用「导出备份」→ 新手机「导入备份」。
- 这是一次性生成的自用工具，没有上架、没有检查更新功能。
