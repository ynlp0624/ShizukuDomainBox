# 交接文档 · 把「安装程序」抽成独立工具（InstallerKit）

> 写给下一个小雫：这份文档记录了「雫的域名盒 PC 版安装程序」是怎么做的，
> 以及把它抽成**通用安装包制作工具**需要注意什么。
> 原始工程在 `D:\dnshe-pc`，参考实现在 `D:\installer-kit\reference\`。

---

## 🐾 下一个小雫的第一步（照着做就行）

1. **先读本文第 1 节和第 2 节**，把「三种身份 / 自我复制 / 卸载 exe 怎么来的」这三件事搞懂，
   不然会走很多弯路。
2. **读 `reference/Windows.kt`**——注册表、快捷方式、卸载/修复的完整实现，几乎可以直接搬。
3. **搭骨架**（第 4 节的目录结构），建议顺序：
   `kit-core` → `app`（壳 + jpackage 配置）→ `samples/demo-app` → `kit-ui`。
   ⚠️ 一定先做 `demo-app`，不然改坏了没有回归手段。
4. **把 `reference/` 里的代码改写进新结构**，同时做第 5 节的 P0 功能。
5. **每做完一块就跑第 10 节的验收清单**（第 10 节每一条都是之前踩出来的）。
6. 老工程的现状（可随时去 `D:\dnshe-pc` 对照）：
   - `core/.../Windows.kt` 208 行：Windows 集成
   - `desktop/.../ui/InstallView.kt` 约 560 行：安装向导
   - `desktop/.../ui/UninstallView.kt` 约 200 行：卸载向导
   - `tools/build-installer.ps1`：打包流水线（含 BOM 坑，见第 3 节第 5 条）

**用户明确的定制需求**（一定要做成配置项，不要写死）：
> 「可以自定义版本号、版本，可以自定义配置图标，自定义导入主 exe，
> 自动生成卸载 exe，等一些常规功能」

→ 也就是第 6 节 `installer.config.json` 里那些字段，**换产品只改配置不改代码**。

---

## 0. 一句话概括

这个工具要做的事：**把任意一个 jpackage app-image（或任意目录）打包成一个
带向导界面、能自定义安装路径、写注册表、自带卸载器的单文件安装程序。**

技术栈：Kotlin + Compose Desktop（向导 UI）+ jpackage（生成自带运行时的 app-image）
+ IExpress（封单文件 exe）。

---

## 1. 核心原理（必读，不知道这个会白写很多代码）

### 1.1 三种身份，共用一份运行时

jpackage 生成的 app-image 长这样：

```
MyApp/
├── MyApp.exe              ← 启动器（原生 exe，内嵌图标）
├── app/
│   ├── MyApp.cfg          ← ★ 启动器按「和自己同名的 .cfg」读配置
│   ├── MyApp-1.0.0.jar
│   ├── kotlin-stdlib.jar …
│   └── resources/
└── runtime/               ← jlink 裁出来的 JRE（约 66 MB）
```

**关键发现**：把 `MyApp.exe` 复制成 `Setup.exe`，再写一个 `app\Setup.cfg`，
启动器就会去读 `Setup.cfg`！在 cfg 末尾追加：

```ini
[ArgOptions]
arguments=--install
```

于是同一个 exe 就有了「安装模式」。卸载器同理（`Uninstall.cfg` + `--uninstall`）。

> 好处：**不用为安装程序单独打包一份运行时**（省 60~100 MB），
> 也不会出现「安装程序 160MB + 主程序 160MB」的荒唐局面。

### 1.2 安装 = 自我复制

```kotlin
val selfDir = System.getProperty("jpackage.app-path")?.let { File(it).parentFile }!!
// selfDir 就是整个 app-image 根目录
copyRecursively(selfDir, installDir)
```

**不需要额外塞 payload.zip**。安装程序自己就是货物。

### 1.3 卸载 exe 是安装时"生成"的

```kotlin
Files.copy(appExe.toPath(), File(installDir, "Uninstall.exe").toPath())
// 再写 app\Uninstall.cfg，内容 = 原 cfg + [ArgOptions] arguments=--uninstall
```

真正的「生成 exe」在 JVM 里做不到（没有编译器），但**复制 exe + 改同名 cfg**
效果完全一样，而且是真 exe、有图标、能双击。

---

## 2. 各部件实现要点

### 2.1 模式判定（主程序入口）

```kotlin
val exeName = System.getProperty("jpackage.app-path")?.let { File(it).name } ?: ""
val uninstallMode = args.any { it.equals("--uninstall", true) } || exeName.startsWith("Uninstall", true)
val installMode   = args.any { it.equals("--install", true) }   || exeName.startsWith("Setup", true)
```
双重判定（参数 + 文件名）更稳：命令行直接传参、和双击 exe 都能进对模式。

### 2.2 注册表（「程序和功能」里那一项）

路径：
- 仅当前用户：`HKCU\Software\Microsoft\Windows\CurrentVersion\Uninstall\<AppId>`
- 所有用户：`HKLM\...`（需要管理员）

要写的值（少一个在控制面板里就会显示不全）：

| 名称 | 类型 | 说明 |
|---|---|---|
| DisplayName | REG_SZ | 产品名 |
| DisplayVersion | REG_SZ | 版本号 |
| Publisher | REG_SZ | 发布者 |
| InstallLocation | REG_SZ | 安装目录 |
| InstallDate | REG_SZ | `yyyyMMdd` |
| UninstallString | REG_SZ | `"<目录>\Uninstall.exe"`（**带引号**，路径有空格时必需） |
| QuietUninstallString | REG_SZ | `"<目录>\Uninstall.exe" /S /KEEPDATA` |
| DisplayIcon | REG_SZ | `<目录>\主程序.exe`（决定控制面板里显示哪个图标） |
| URLInfoAbout / HelpLink | REG_SZ | 官网 / 帮助链接 |
| NoModify / NoRepair | REG_DWORD | 1（禁用「修改」「修复」按钮） |
| Language | REG_DWORD | 2052（简体中文） |
| EstimatedSize | REG_DWORD | **KB 单位**，显示"占用空间" |

另外建议在 `HKCU/HKLM\Software\<AppId>` 写一份 `InstallPath` / `Version` / `PerUser`，
方便卸载器定位和以后升级判断。

实现方式：直接调 `reg.exe`（`ProcessBuilder("reg","add",...)`），别引入额外依赖。

### 2.3 快捷方式

用 PowerShell 的 WScript.Shell COM，中文路径也稳：

```kotlin
val ps = """
  ${'$'}ws = New-Object -ComObject WScript.Shell
  ${'$'}s = ${'$'}ws.CreateShortcut('$linkPath')
  ${'$'}s.TargetPath = '$exePath'
  ${'$'}s.Arguments = '$args'
  ${'$'}s.WorkingDirectory = '$workDir'
  ${'$'}s.IconLocation = '$iconExe,0'
  ${'$'}s.Save()
"""
ProcessBuilder("powershell","-NoProfile","-WindowStyle","Hidden","-Command",ps).start()
```
> ⚠️ 在 Kotlin 字符串里 `${'$'}ws` 必须这样转义，直接写 `$ws` 会被当模板变量。

位置：桌面 = `%USERPROFILE%\Desktop`（所有用户 = `%PUBLIC%\Desktop`）；
开始菜单 = `%APPDATA%\Microsoft\Windows\Start Menu\Programs`（所有用户 = `%ProgramData%\...`）。

### 2.4 自我删除（卸载的最后一步）

运行中的 exe 删不掉自己，而且**正在跑的 JVM 会锁住 `app\*.jar`**。
所以要：先删能删的，剩下的交给一个延迟批处理**循环重试**：

```bat
@echo off
setlocal enabledelayedexpansion
ping -n 4 127.0.0.1 > nul
set /a n=0
:retry
rmdir /s /q "<安装目录>" 2>nul
if not exist "<安装目录>" goto done
set /a n+=1
if !n! geq 30 goto done
ping -n 2 127.0.0.1 > nul
goto retry
:done
rmdir /q "<安装目录>" 2>nul
del /f /q "%~f0"
```

启动方式：`ProcessBuilder("cmd","/c","start","\"\"","/min","cmd","/c",batPath).start()`

> 千万别只写一次 `rmdir` 就完事——进程退出需要时间，一次必然失败（雫踩过）。

### 2.5 UAC 提权

用户在向导里选了「为所有用户安装」但当前不是管理员时，重新以管理员启动自己：

```kotlin
val exe = System.getProperty("jpackage.app-path")
ProcessBuilder("powershell","-NoProfile","-WindowStyle","Hidden","-Command",
  "Start-Process -FilePath '$exe' -Verb RunAs -ArgumentList '/SCOPE=all','/DIR=$dir'").start()
// 然后 exitProcess(0)
```
isAdmin 判断：`ProcessBuilder("net","session")` 退出码 == 0。

### 2.6 单文件封装（IExpress，Windows 自带）

IExpress 的 SED **要求逐条列出文件**，两千个文件不现实。所以：

1. 把整个发行目录打成**一个** `payload.zip`（`tar -a -c -f payload.zip -C 目录 .`）
2. 再配一个 `install.cmd`：解压到 `%TEMP%\<AppId>Setup` 然后 `start Setup.exe`
3. SED 里只列这两个文件，`AppLaunched=install.cmd`

```ini
[Options]
TargetName=D:\out\MyApp-Setup.exe
AppLaunched=install.cmd
[Strings]
FILE0="payload.zip"
FILE1="install.cmd"
[SourceFiles]
SourceFiles0=D:\stage\
[SourceFiles0]
%FILE0%=
%FILE1%=
```

运行：`iexpress.exe /N /Q <sed路径>`

体积参考：160 MB 的 app-image → payload.zip 96 MB → 最终 exe 96 MB。

---

## 3. 踩过的坑（血泪，务必照做）

1. **jpackage 的参数按系统编码读**：`packageName` / `description` / `menuGroup`
   写中文会直接报 `Input length = 1` 并失败。**jpackage 层全用 ASCII**，
   中文名由安装程序写进快捷方式和注册表。
2. **窗口图标 ≠ exe 图标**：`jpackage --icon` 只管 exe 文件本身。
   窗口标题栏和任务栏图标必须在程序里设：
   `Window(icon = painterResource("app-icon-64.png"))` +
   `window.iconImages = listOf(16,20,24,32,40,48,64,96,128,256)`。
   不设就是 Java 的 Duke 咖啡杯。
3. **运行中的 JVM 锁 jar**：卸载时别指望当场删干净，靠 2.4 的重试批处理。
4. **PowerShell 脚本片段里的 `$`**：写在 Kotlin 字符串里必须 `${'$'}` 转义。
5. **PowerShell 5.1 读脚本按 ANSI**：含中文的 `.ps1` 必须带 **BOM**，
   否则解析报错（用 `utf-8-sig` 写）。注意：用编辑工具改完 .ps1 会丢 BOM，要重新加。
6. **`Select-Object -First N` 会提前掐断管道**：用它过滤一个长时间运行脚本的输出，
   会把脚本本身也杀掉。改成 `> log.txt 2>&1` 再 `Get-Content -Tail`。
7. **Compose Desktop 的 `ToolbarButton` 参数顺序**：`(text, enabled, onClick)`，
   把 `enabled` 放最后会让尾随 lambda 绑错参数。
8. **jar 依赖可见性**：core 模块用 `implementation` 引入的库，desktop 模块用不到，
   要用 `api` 或者显式再引一次（`kotlinx-serialization-json` 就踩过）。
9. **跨模块智能转换**：`if (x.a != null && x.b != null)` 之后不能直接 `x.a`，
   要先取到局部变量。

---

## 4. 推荐目录结构（InstallerKit）

```
D:\installer-kit\
├── README.md                      一句话说明 + 快速开始
├── HANDOFF.md                     ← 本文档
├── installer.config.example.json  配置示例（见第 6 节）
│
├── kit-core\                      纯 Kotlin/JVM，无 UI，可被别的项目直接依赖
│   └── src\main\kotlin\...\kit\
│       ├── InstallerConfig.kt     配置数据类 + 读取 installer.config.json
│       ├── WinPaths.kt            路径约定（桌面/开始菜单/Program Files…）
│       ├── WinReg.kt              注册表增删查
│       ├── WinShortcut.kt         快捷方式
│       ├── InstallEngine.kt       安装：自我复制 + 生成卸载器 + 写注册表 + 建快捷方式
│       └── UninstallEngine.kt     卸载 / 修复 / 保留数据
│
├── kit-ui\                        Compose Desktop 向导（可复用组件）
│   └── src\main\kotlin\...\kit\ui\
│       ├── Wizard.kt              三步式向导外壳（左品牌区 + 右内容 + 底按钮条）
│       ├── InstallPages.kt        欢迎 / 许可 / 范围 / 路径 / 任务 / 进度 / 完成
│       ├── UninstallPages.kt      卸载 / 修复 / 保留数据
│       ├── BrandPanel.kt          左侧品牌区（logo + 产品名 + 步进条）
│       └── Theme.kt
│
├── app\                           把 kit-ui 组装成"安装程序本体"的壳工程
│   ├── build.gradle.kts           ← jpackage 配置在这里（ASCII！）
│   ├── icons\app.ico
│   ├── resources\app-icon-*.png   窗口/任务栏图标（多尺寸）
│   └── src\main\kotlin\...\Main.kt  模式分发（--install / --uninstall / 正常）
│
├── tools\
│   ├── build-installer.ps1        ★ 一键打包（见第 7 节）
│   ├── make-icons.py              PNG/ICO/多尺寸窗口图标生成
│   └── new-package.ps1            脚手架：按 config 生成一套发行目录
│
├── samples\
│   └── demo-app\                  一个最小 Compose Desktop 主程序，用来演示/回归测试
│
├── docs\
│   ├── CONFIG.md                  配置项完整说明
│   ├── PIPELINE.md                打包流水线详解
│   └── PITFALLS.md                第 3 节单独成文
│
└── build\                         中间产物（setup 目录、payload.zip、sfx 暂存）
```

**为什么这样分**：
- `kit-core` 不依赖 Compose，别人可以拿来只做命令行打包工具；
- `kit-ui` 只依赖核心 + Compose，方便换皮；
- `app` 是"安装程序自己"的壳，jpackage 配置集中在这里，**换产品只改 config 不改代码**；
- `samples/demo-app` 保证随时能端到端回归（不然改坏了没法验证）。

---

## 5. 功能清单（按优先级）

### P0 必须
- [ ] 配置文件驱动：产品名 / 版本号 / 发布者 / AppId / 图标 / 主页链接
- [ ] 导入主程序（指定 app-image 目录，自动识别其中的 .exe 和 .cfg）
- [ ] 安装向导：欢迎 → 许可协议 → 安装范围 → 自定义路径 → 附加任务 → 进度 → 完成
- [ ] 自定义安装路径（默认 `%LOCALAPPDATA%\Programs\<AppId>`）
- [ ] 安装范围二选一（仅当前用户 HKCU / 所有用户 HKLM + UAC 提权）
- [ ] 桌面快捷方式 / 开始菜单项（可勾选）
- [ ] 写注册表「程序和功能」项（第 2.2 节全部键值）
- [ ] **自动生成卸载 exe** + 独立卸载向导
- [ ] 卸载三选项：保留数据 / 彻底删除 / 修复安装

### P1 应该
- [ ] 静默安装 `/S [/DIR=] [/SCOPE=all]`、静默卸载 `/S [/KEEPDATA]`
- [ ] 覆盖安装检测（读注册表已有版本，提示升级 / 先卸载）
- [ ] 安装前检查磁盘空间、检查目标目录是否被占用
- [ ] 卸载时自动关掉正在运行的主程序
- [ ] 多语言（中/英），跟随系统
- [ ] 打包产物自动算 SHA256 并写进 `release.json`

### P2 锦上添花
- [ ] 命令行打包模式（无 UI，CI 友好）
- [ ] 安装包自签名 / 预留代码签名接口（`signtool` 调用）
- [ ] 把许可协议、图标、logo 都做成 config 里可指定的外部文件
- [ ] 支持"绿色版"输出（只出 zip 不出安装包）

---

## 6. 配置项设计（installer.config.json）

```json
{
  "productName": "雫的域名盒",
  "appId": "ShizukuDomainBox",
  "version": "1.0.0",
  "publisher": "Nekoshizuku",
  "copyright": "(c) 2026 Nekoshizuku",

  "iconFile": "icons/app.ico",
  "logoFile": "resources/app-logo.png",
  "windowIconSizes": [16, 20, 24, 32, 40, 48, 64, 96, 128, 256],

  "licenseFile": "assets/LICENSE.txt",
  "urlInfoAbout": "https://example.com/",
  "helpLink": "https://example.com/help",

  "mainAppDir": "../myapp/build/compose/binaries/main/app/MyApp",
  "mainExeName": "MyApp.exe",
  "installDirName": "MyApp",
  "dataDirName": "MyApp",
  "estimatedSizeMb": 160,

  "defaultPerUser": true,
  "allowScopeChoice": true,
  "defaultDesktopShortcut": true,
  "defaultStartMenu": true,
  "launchAfterInstall": true,

  "uninstallKeepDataByDefault": true,
  "killRunningAppOnUninstall": true
}
```

**要点**：
- `version` / `productName` 是唯一事实来源——注册表、快捷方式名、向导标题、
  「关于」页都从这里读，避免多处手改。
- `mainAppDir` 支持相对路径，便于放在工程里。
- 图标允许留空 → 用主程序自带的图标兜底。

---

## 7. 打包流水线（build-installer.ps1 的顺序）

```
1. 构建主程序 app-image          gradle :app:createDistributable
2. 复制成发行目录 stage\
3. 生成三种身份：
   - Setup.exe     = 主程序exe 的副本
   - app\Setup.cfg     = 原cfg + [ArgOptions] arguments=--install
   - app\Uninstall.cfg = 原cfg + [ArgOptions] arguments=--uninstall
   （Uninstall.exe 是安装时在目标目录生成的，不打进安装包）
4. tar -a -c -f stage\..\payload.zip -C stage .
5. 写 install.cmd（解压 payload.zip 到 %TEMP% 再启动 Setup.exe）
6. 生成 .sed 并调用 iexpress.exe /N /Q
7. 输出 dist\<AppId>-Setup-v<version>.exe + SHA256
```

⚠️ 第 3 步生成 cfg 时，**原来的 `[Application]` / `[JavaOptions]` 段要原样保留**，
只在末尾追加 `[ArgOptions]`。

---

## 8. 环境（这台机器上现成的）

| 组件 | 路径 |
|---|---|
| JDK 17 | `D:\Android\jdk-17` |
| Gradle 8.7 | `D:\Android\gradle-8.7\bin\gradle.bat` |
| Python（带 Pillow） | `C:\Users\mynlp\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe` |
| IExpress | `%SystemRoot%\System32\iexpress.exe` |

构建命令：
```bat
set JAVA_HOME=D:\Android\jdk-17
D:\Android\gradle-8.7\bin\gradle.bat -p D:\installer-kit :app:createDistributable
powershell -File tools\build-installer.ps1
```

---

## 9. 参考实现（已复制到参考目录）

| 文件 | 内容 |
|---|---|
| `reference/Windows.kt` | 路径约定 + 注册表 + 快捷方式 + 卸载/修复（**最核心，可直接搬**） |
| `reference/InstallView.kt` | 安装向导 7 个页面 + 自我复制 + cfg 生成 |
| `reference/UninstallView.kt` | 卸载向导（保留数据 / 彻底删除 / 修复） |
| `reference/Main.kt` | 模式分发 + 窗口图标设置 |
| `reference/build-installer.ps1` | 完整打包脚本 |
| `reference/make_window_icons.py` | 图标生成（PNG 多尺寸 / ICO / 圆角 logo） |

---

## 10. 验收清单（做完了照着跑一遍）

- [ ] `Setup.exe /S /DIR=D:\test` 静默安装成功，退出码 0
- [ ] 目标目录里同时有 `主程序.exe` 和 `Uninstall.exe`
- [ ] 注册表项 12 个键值齐全（`reg query` 对照第 2.2 节的表）
- [ ] 桌面 / 开始菜单快捷方式能双击启动
- [ ] 主程序窗口标题栏 + 任务栏图标是自己的（不是 Java 咖啡杯）
- [ ] `Uninstall.exe /S /KEEPDATA` → 程序没了、数据还在、注册表项没了
- [ ] `Uninstall.exe /S` → 连数据一起删，安装目录最终**完全消失**（等 30 秒）
- [ ] 单文件 Setup.exe 双击 → 自解压 → 向导出现
- [ ] 换一个 `installer.config.json`（改产品名/版本/图标）重新打包，产物全部跟着变
