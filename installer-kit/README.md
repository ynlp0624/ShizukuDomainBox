# InstallerKit · 通用 Windows 安装包制作工具

把任意一个 **jpackage app-image**（自带运行时的 Java 桌面程序）打包成：

- 带品牌向导界面的**单文件安装程序** `Xxx-Setup-v1.0.0.exe`
- 安装时可**自定义安装路径**、选择安装范围（仅我 / 所有用户）
- 自动写**注册表**（控制面板「程序和功能」里能正常卸载）
- 自动创建**桌面 / 开始菜单快捷方式**
- 安装时**自动生成卸载 exe**（支持 保留数据 / 彻底删除 / 修复安装）
- 支持**静默安装**与**静默卸载**
- **换产品只改 `installer.config.json`，代码一个字都不用动**

---

## 现在做到哪了

| 模块 | 状态 | 说明 |
|---|---|---|
| **`packer`** | ✅ | **图形界面打包器** —— 这个工具的「门面」。挑 app-image、填表、点「开始打包」、看实时日志。另有 `--headless` 命令行模式给 CI 用。`gradle :packer:verifyPacker` **36 项自检全绿** |
| `kit-core` | ✅ | 纯 Kotlin/JVM，无 UI。配置读写、路径推导、注册表、快捷方式、安装/卸载引擎、jpackage cfg 改写。`gradle :kit-core:verifyCore` **61 项自检全绿** |
| `kit-ui` | ✅ | Compose 向导：主题 / 品牌区 + 步骤条 / 8 页安装向导 / 卸载向导 / 诊断页 / 中英双语。`gradle :kit-ui:verifyUi` **40 项自检全绿**（含「中英 key 一一对应」「没有漏填的占位符」） |
| `app` | ✅ | 很薄的壳：三种身份分发 + 开窗，界面全在 kit-ui、逻辑全在 kit-core |
| `samples/demo-app` | ✅ | 回归样本「雫的演示盒」，用来端到端验整条链路 |
| `tools/` | ✅ | `build-installer.ps1`（打包+签名）、`verify-install.ps1`（验收）、`make-icons.py`（图标）、`grab-window.ps1`（给窗口截图做视觉验证） |
| **数字签名** | ✅ | 打包期自动签名（[ShizukuCodeSign](file:///D:/sing/codesign)）：安装包本身 + 发行目录里的 exe + 装出来的 `Uninstall.exe` 全都有有效签名，见下文 |
| 端到端验收 | ✅ | `tools\verify.cmd` **69 项全绿**：数字签名 → 静默安装 → 目录内容 → 注册表 22 项 → 快捷方式 → 窗口图标 → 保留数据卸载 → 彻底卸载 → 单文件包（自解压 + 静默安装 + 卸载） |
| P1 功能 | ✅ | **覆盖安装检测**（装上过就多一步「已经装过」，可选升级到原位或另装一份）+ **磁盘空间检查**（向导里实时显示可用空间，不够就不让往下走；静默模式也会拦） |
| `docs/` | ⏳ | 目前只有 [PITFALLS.md](docs/PITFALLS.md)（23 条坑） |

> 验收默认把程序装到**带空格的目录**（`…\Programs\KitDemoBox Test`）—— Windows 上一大半
> 注册表 / 快捷方式 / 批处理的坑只在路径有空格时才暴露。

---

## 快速开始

### 桌面版（推荐，双击就能开）

桌面上有两个图标，对应打包器的两种形态：

| 图标 | 是什么 |
|---|---|
| **InstallerKit 打包器** | **简单版**：选程序 → 填名字 → 点打包，就这三步 |
| **InstallerKit 打包器（专业版）** | 所有字段都能改（图标、品牌色、签名、卸载行为、向导样式…）|

两个版本**共用同一份配置**，点右上角随时对切，不会丢东西。

> 这两个快捷方式是用 `tools\shortcuts.cmd` 建的（打包器自己也是被本工具**打包安装**的：
> `tools\build.cmd -Config packer.config.json` → 安装 → 建快捷方式）。
> 想撤掉快捷方式：`tools\shortcuts.cmd -Remove`。

### 简单版怎么用

1. **第 1 步 · 选主程序** —— 点「选主程序 exe 文件…」或「选程序文件夹…」。
   选完会告诉你是谁、有几种可能、会打包多少东西。
2. **第 2 步 · 填几个字** —— 产品名（安装时显示的名字）、版本号、发布者。
3. **第 3 步 · 打包** —— 点一下，右侧能看到进度；跑完给出安装包路径。

### 它能打包两类东西

| 类型 | 长什么样 | 怎么认出来的 | 安装包大小 |
|---|---|---|---|
| **jpackage 程序** | 有 `app\xxx.cfg` + `app\*.jar` + `runtime\`（Java 程序自带运行时）| 自动 | **≈ 程序本身大小**（安装器塞进它自带的运行时里）|
| **绿色软件** | 一个 exe + 一堆文件/文件夹（没有运行时）| 自动 | 轻量向导：**≈ 程序本身 + 几十 KB**<br>完整向导：**≈ 57MB + 程序本身** |

比如把 0.6MB 的「Windows11轻松设置」打包：

```
轻量向导  →  Win11EasyConfig-Setup-v1.1.2.exe    0.5 MB   系统 .NET 画的朴素小窗口
完整向导  →  Win11EasyConfig-Setup-v1.1.2.exe   56.6 MB   和 jpackage 模式一模一样的那套 Compose 向导
```

选哪个在**专业版**里勾：`便携模式用完整向导`。
默认轻量（给 0.5MB 的小工具套 57MB 的壳子太亏），要外观一致就勾上。

### 卸载

- **jpackage 模式**：安装目录里有 `Uninstall.exe`（主程序 exe 的副本 + `app\Uninstall.cfg`），
  向导支持「保留数据 / 彻底删除 / 修复」。
- **便携模式**：安装目录里是 `Uninstall.ps1` + `uninstall.json`
  （省下一整套运行时），注册表的 `UninstallString` 直接指向它，
  「设置 → 应用」里点卸载一样能弹对话框。

### 命令行 / CI 用法

```bat
InstallerKitPacker.exe --headless ^
  --app-image="D:\myapp\build\compose\binaries\main\app\MyApp" ^
  --product="我的工具" --app-id=MyTool --version=1.0.0 ^
  --out=installer.config.json --build
```

参数：`--kit=<工程目录>` `--app-image=<目录>` `--product=` `--app-id=` `--version=` `--publisher=`
`--out=<配置路径>` `--build`（写完接着打包）`--no-product-build` `--no-iexpress`

界面参数：不带参数开**简单版**，加 `--pro` 开**专业版**。

也可以绕开 GUI 直接跑流水线：

```bat
tools\build.cmd                                          rem 打包当前配置
tools\build.cmd -Config packer.config.json               rem 打另一个产品的配置
tools\build.cmd -Config installer.config.win11-easyconfig.json      rem 绿色软件（轻量向导）
tools\build.cmd -Config installer.config.win11-easyconfig-full.json rem 绿色软件（完整向导）
tools\build.cmd -SkipBuild -SkipIExpress                 rem 只重组目录版
tools\verify.cmd                                         rem 端到端验收
tools\fix-bom.cmd                                        rem 改完 .ps1 必跑（补 BOM + 语法预检）
```

> ⚠️ `tools\*.ps1` 直接 `powershell -File` 会被执行策略挡住（默认 Restricted）。
> 用 `tools\*.cmd` 包装器，或者自己加 `-ExecutionPolicy Bypass`。

> ⚠️ **单文件安装包不支持命令行参数**（IExpress 的自解压壳不转发参数），
> 所以 `Xxx-Setup.exe /S` 不会静默安装，而是弹出图形向导。
> 要静默安装就用**目录版**的 `stage\Setup.exe /S`（便携轻量版是 `stage\install.cmd -Silent`），
> 或者先把单文件包解压再跑里面的入口。

---

## 一句话原理

> jpackage 的启动器按「和自己同名的 `.cfg`」读配置，而 **`.cfg` 里的 `app.mainclass` 决定入口**。
> 所以我们不改产品、不动产品自己的 cfg，只补三个同名文件就多出两种身份：

| 文件 | mainclass | 参数 | 双击它 |
|---|---|---|---|
| `MyApp.exe` + `app\MyApp.cfg` | 产品自己的 | — | 正常启动产品 |
| `Setup.exe` + `app\Setup.cfg` | kit 的向导 | `--install` | 进安装向导 |
| `Uninstall.exe` + `app\Uninstall.cfg` | kit 的向导 | `--uninstall` | 进卸载向导 |

安装动作 = **把正在运行的这套 app-image 整个复制到目标目录**（自己就是货物），
所以**一套运行时三种身份，不重复打包、不多吃一个字节**。

详细展开见 [HANDOFF.md](HANDOFF.md) 第 1 节。

---

## 数字签名（默认关，配置里一个开关）

打包出来的东西可以带上自己的 Authenticode 签名，用的是
[D:\sing\codesign](file:///D:/sing/codesign)（自建 PKI + signtool，纯 PowerShell 实现）。

```json
"codeSign": {
  "enabled": true,
  "toolsDir": "D:/sing/codesign",
  "profile": "studio",
  "timestamp": false,
  "signInstaller": true,
  "signStageFiles": true,
  "includeGlobs": ["*.exe"],
  "verify": true
}
```

本仓库用的是**自建的「Shizuku Studio」档案**（自己用，不需要买证书）。要重建这份身份：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\sing\codesign\codesign.ps1 init `
  -Profile studio -CommonName 'Shizuku Studio' -Organization 'Shizuku Studio' `
  -OrganizationalUnit 'Code Signing' -Country CN -Years 5 -CaYears 10 -InstallTrust
```

> 证书**本体在 Windows 证书库里**（`Cert:\CurrentUser\My`），不在文件夹里；
> 换账户 / 重装系统会没掉，提前 `codesign.ps1 export -Profile studio -IncludePfx` 备份。

**顺序是「先签里面的，再签外面的」** —— 单文件安装包并不是「把文件装进盒子」，
而是 `发行目录 → payload.zip → IExpress 重新生成`：

```
组装发行目录 → 签 stage 里的 exe → 压 payload.zip → 封 Setup.exe → 签 Setup.exe → 验签
```

| 位置 | 签名 |
|---|---|
| `dist\Xxx-Setup-v1.0.0.exe`（单文件安装包本身） | ✅ 打包最后一步签的 |
| 自解压出来的 `%TEMP%\XxxSetup\Setup.exe` | ✅ 就是 stage 里签好的那份 |
| 装完后的 `安装目录\Xxx.exe` | ✅ |
| 装完后的 `安装目录\Uninstall.exe` | ✅ 它是主程序 exe 的副本，签名自动跟过来，不用单独签 |

`tools\verify.cmd` 会把这四条都验一遍（用系统自带的 `Get-AuthenticodeSignature`，
不依赖签名工具本身）。

> ⚠️ 自签名只有**装了你的根证书**的机器才显示「有效」。自己电脑上自己用完全没问题；
> 发给别人当正式发布签名不够用 —— SmartScreen 看的是信誉，不是签名本身。
> 完整的边界说明见 `D:\sing\codesign\README.md`。

---

## 目录结构

```
D:\installer-kit\
├── README.md
├── HANDOFF.md                      最初的交接文档（原理 / 踩坑 / 清单）
├── installer.config.json           ★ 当前产品的配置
├── installer.config.example.json   配置示例（每个字段都有注释）
├── assets\                         许可协议等外部文件
│
├── packer\                         ★★ 图形界面打包器（你要的那个「工具」）
│   └── src\main\kotlin\io\nekoshizuku\pack\
│       ├── Main.kt                 入口：有窗口 / --headless 两种模式
│       ├── KitPaths.kt             找工程根 + 记住上次设置
│       ├── AppImageProbe.kt        ★ 导入 app-image：自动识别 exe / cfg / jar / 体积 / java.desktop
│       ├── BuildRunner.kt          跑 tools\build-installer.ps1，流式接日志
│       ├── PackerCli.kt            无界面模式（CI 友好）
│       ├── ui\PackerApp.kt         打包器界面
│       └── verify\VerifyPacker.kt  无头自检
│
├── kit-core\                       纯逻辑，可被别的项目直接依赖
│   └── src\main\kotlin\io\nekoshizuku\kit\
│       ├── InstallerConfig.kt      配置数据类 + 读写（含 codeSign / i18n / brandColor）
│       ├── KitSelf.kt              自我认知（我在哪）+ 模式判定 + 命令行解析
│       ├── WinEnv.kt / WinPaths.kt 系统路径 / 产品路径约定
│       ├── WinReg.kt               注册表（PowerShell -EncodedCommand 写）+ 命令执行
│       ├── WinShortcut.kt          快捷方式（WScript.Shell COM）
│       ├── JpackageCfg.kt          ★ cfg 改写（换 mainclass / 补 classpath / 加参数）
│       ├── InstallEngine.kt        安装：自我复制 + 生成卸载器 + 注册表 + 快捷方式
│       ├── UninstallEngine.kt      卸载 / 修复 / 保留数据 / 自删除重试
│       ├── Cleanup.kt              %TEMP% 里的自解压残留清理
│       └── verify\VerifyCore.kt    无头自检
│
├── kit-ui\                         Compose 向导（可复用组件 + 拿来就能用的向导）
│   └── src\main\kotlin\io\nekoshizuku\kit\ui\
│       ├── Theme.kt                KitTheme / KitBrand（品牌色解析）
│       ├── BrandPanel.kt           左侧品牌区（logo + 产品名 + 步骤条）
│       ├── Wizard.kt               WizardFrame / SimpleFrame / 通用小件 / WizardContext
│       ├── InstallPages.kt         7 个页面 + 安装向导状态机
│       ├── UninstallPages.kt       卸载 / 修复向导
│       ├── Diagnostics.kt          诊断页（普通模式）
│       ├── KitAssets.kt            产品 logo / 窗口图标（优先用注入的，缺了退回内置）
│       ├── i18n\Strings.kt         中英双语 + 配置覆盖
│       └── verify\VerifyUi.kt      无头自检
│
├── app\                            安装程序本体的壳（Compose + jpackage）
│   ├── build.gradle.kts            jpackage 配置（**ASCII**）
│   ├── icons\app.ico
│   ├── src\main\resources\         kit 自带的兜底图标 / logo
│   └── src\main\kotlin\io\nekoshizuku\kit\app\Main.kt   三种身份分发 + 开窗
│
├── samples\demo-app\               回归样本「雫的演示盒」，**刻意不依赖 kit-core**
│
├── tools\
│   ├── studio.cmd                  ★ 双击启动图形界面打包器
│   ├── build-installer.ps1         ★ 打包流水线（构建 → 组装 → 签名 → 封包 → 签名 → 验签）
│   ├── verify-install.ps1          ★ 端到端验收（69 项）
│   ├── make-icons.py               多尺寸 PNG / ICO / 圆角 logo
│   ├── grab-window.ps1             给某个进程的窗口截图（视觉验证用）
│   ├── build.cmd / verify.cmd      绕过执行策略的包装器
│   └── fix-bom.cmd                 给 .ps1 补回 UTF-8 BOM + 语法预检
│
├── docs\PITFALLS.md                23 条踩过的坑
└── build\ / dist\                  中间产物 / 产物
```

---

## 环境要求

| 组件 | 本机现成的 |
|---|---|
| JDK 17 | `D:\Android\jdk-17` |
| Gradle 8.7 | `D:\Android\gradle-8.7\bin\gradle.bat` |
| Python + Pillow | `C:\Users\mynlp\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe` |
| IExpress | `%SystemRoot%\System32\iexpress.exe`（Windows 自带） |

依赖版本刻意和 `D:\dnshe-pc` 对齐（Kotlin 2.0.20 / Compose 1.7.0），本机缓存能直接命中，尽量不联网。

---

## reference/ 里有什么

从「雫的域名盒」项目里抄出来的可用参考实现：

| 文件 | 说明 |
|---|---|
| [Windows.kt](reference/Windows.kt) | 路径约定 + 注册表 + 快捷方式 + 卸载/修复 |
| [InstallView.kt](reference/InstallView.kt) | 安装向导 7 个页面 + 自我复制安装 + cfg 生成 |
| [UninstallView.kt](reference/UninstallView.kt) | 卸载向导（保留数据 / 彻底删除 / 修复） |
| [Main.kt](reference/Main.kt) | 模式分发（`--install` / `--uninstall` / 正常）+ 窗口图标设置 |
| [build-installer.ps1](reference/build-installer.ps1) | 完整打包脚本（含 IExpress 封单文件） |
| [make_window_icons.py](reference/make_window_icons.py) | 图标生成 |

正式代码已经把这些重写进 `kit-core` / `app` / `tools` 了，`reference/` 留作对照。
