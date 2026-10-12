# 「雫的域名盒」PC 版 · 构建与交付说明

## 交付物

| 文件 | 说明 |
|---|---|
| `dist/ShizukuDomainBox-Setup-v1.0.0.exe` | **单文件安装程序（96 MB）**，双击即装 |
| `core/` | 共享数据层（API / 模型 / DPAPI 加密 / 备份 / Windows 集成） |
| `desktop/` | Compose Desktop 主程序（同时也是安装器 / 卸载器） |
| `tools/build-installer.ps1` | 一键打包脚本 |
| `screens/` | 实测截图 |

SHA256（Setup.exe）：`2F8BA201991CDB67008C36977C7F227114223064A0798FE122AB4E77FE265BF5`

## 架构：一套程序，三种身份

关键设计——**安装程序就是主程序本身**，靠 exe 文件名 + 同名 `.cfg` 切换模式：

```
app-image/
├── ShizukuDomainBox.exe     正常启动 → 主界面
├── Setup.exe                = 主程序 exe 的副本
├── app/
│   ├── ShizukuDomainBox.cfg  正常配置
│   ├── Setup.cfg             + [ArgOptions] arguments=--install
│   └── Uninstall.cfg         + [ArgOptions] arguments=--uninstall
└── runtime/                 自带 JRE（jlink 裁剪）
```

jpackage 的启动器按「和自己同名的 .cfg」读配置，所以复制一个 exe 改名就得到
一个带参数的入口，**不重复打包任何运行时**。安装动作就是把这套目录整个复制到目标位置。

安装后目标目录里同时有 `ShizukuDomainBox.exe` 和 `Uninstall.exe`（+ 各自 cfg）。

## 功能

- 与安卓版完全一致的业务功能（账号 / 域名 / DNS 记录 / 配额 / 密钥 / WHOIS / 永久升级 / 设置 / 日志）
- 账号密钥用 **Windows DPAPI** 按当前用户加密，等价于安卓版的 Android Keystore
- **JSON 完全互通**：备份结构与安卓版逐字段一致，明文和口令加密（PBKDF2-HMAC-SHA256 + AES-256-GCM）都能双向导入
- 安装程序：许可协议 → 安装范围（仅我 / 所有用户，后者自动 UAC 提权）→ 自定义路径 → 附加任务 → 进度 → 完成
- 写注册表：控制面板「程序和功能」项（DisplayName / DisplayVersion / Publisher / InstallLocation / InstallDate / UninstallString / QuietUninstallString / DisplayIcon / URLInfoAbout / HelpLink / NoModify / NoRepair / EstimatedSize）
- 桌面 + 开始菜单快捷方式（可勾选）
- 卸载器支持 **保留数据卸载 / 彻底卸载 / 修复安装** 三种模式
- 静默安装：`Setup.exe /S [/DIR=路径] [/SCOPE=all]`
- 静默卸载：`Uninstall.exe /S [/KEEPDATA]`
- 界面无 emoji，全部使用矢量图标与 App 标志

## 常用命令

```bat
set JAVA_HOME=D:\Android\jdk-17
cd /d D:\dnshe-pc

D:\Android\gradle-8.7\bin\gradle.bat :desktop:run            :: 开发运行
D:\Android\gradle-8.7\bin\gradle.bat :desktop:verifyCore --args="check"   :: 无头自检

powershell -File tools\build-installer.ps1            :: 完整打包（含 IExpress）
powershell -File tools\build-installer.ps1 -SkipIExpress   :: 只出目录发行版
```

无头自检结果写在 `verify-report.txt`（UTF-8）。

## 打包流程

1. `:desktop:createDistributable` → app-image（自带运行时，约 160 MB）
2. 复制成发行目录，生成 `Setup.exe` / `Setup.cfg` / `Uninstall.cfg`
3. 用 `tar` 打成 `payload.zip`（160 MB → 96.5 MB）
4. IExpress 封成单个 `Setup.exe`；内置 `install.cmd` 负责解压到 `%TEMP%` 再启动向导

## 图标处理

| 位置 | 怎么来的 |
|---|---|
| exe 文件图标（资源管理器里看到的） | jpackage 的 `--icon icons/app.ico`（多尺寸 ico） |
| **窗口标题栏 / 任务栏图标** | **程序运行时自己设**：`Window(icon = painterResource("app-icon-64.png"))` 并且 `window.iconImages = [16,20,24,32,40,48,64,96,128,256]` |
| 安装向导左侧品牌区 / 界面 logo / 空状态 | `app-logo*.png`（圆角方形） |
| 界面里的功能图标 | 矢量图标（Material Icons / 自绘 XML），**不用 emoji** |

> ⚠️ jpackage 的 `--icon` **只管 exe 文件本身**。窗口和任务栏图标必须由程序
> 运行时设置，否则显示的是 Java 默认的 Duke 咖啡杯。
> 多尺寸一起塞进 `iconImages`，Windows 才能按场景（标题栏 16/20px、任务栏 32px、Alt+Tab 48px）挑清晰的那张。

## 踩过的坑（都已在代码里规避）

1. **窗口/任务栏图标是 Java 咖啡杯**：jpackage 的 `--icon` 不负责窗口图标，
   必须在 Compose 的 `Window(icon = ...)` 里设，并额外设置 `window.iconImages` 多尺寸。
2. **jpackage 读参数文件按系统编码**：`packageName` / `description` / `menuGroup` 写中文会
   直接报 `Input length = 1`，所以 jpackage 层全用 ASCII，中文名由安装程序写进快捷方式和注册表。
3. **备份加密信封缺字段**：`Json {}` 默认 `encodeDefaults=false`，会把
   `format/version/kdf/iterations` 省略掉，导致 `looksEncrypted()` 判断失败、
   加密备份被当明文解析成「0 个账号」。修复：`encodeDefaults = true`，
   且判定规则改为「有 accounts 就是明文，有 data+salt/kdf 就是加密」。
   这个 bug 安卓版 v1.1.0 也有，已在 v1.1.1 修掉。
4. **运行中的 JVM 锁住自己的 jar**：卸载时当场删不掉 `app/*.jar`，
   必须靠延迟批处理循环重试（最多 30 次）直到进程完全退出。
5. **jpackage 的 cfg 里 `$` 要转义**：PowerShell 脚本片段写在 Kotlin 字符串里时，
   `$ws` / `$s` 会被当成模板变量。
6. **PowerShell 5.1 读脚本按 ANSI**：`.ps1` 含中文必须带 BOM，否则解析报错。
7. **`Select-Object -First N` 会掐断上游管道**：用它过滤一个长时间运行的脚本输出，
   会把脚本本身一起杀掉（打包到一半失败就是这么来的），改成写日志文件再读尾部。
8. **`ToolbarButton` 参数顺序**：必须是 `(text, enabled, onClick)`，否则尾随 lambda 会绑到 `enabled`。
