# PC 端 Miuix 迁移记录

## 进度总览

| 阶段 | 状态 |
|---|---|
| 工具链升级（Gradle 9.8.1 / JDK 21 / Kotlin 2.4.20 / Compose MP 1.12.0） | ✅ |
| `Theme.kt`（Miuix + Material3 双主题嵌套） | ✅ |
| `Common.kt`（22 个公共组件） | ✅ |
| `AppRoot.kt`（侧栏） | ✅ |
| 逐屏迁移（取色 / 字号 / 按钮 / 进度条 / Surface） | ✅ |
| 安装程序 `InstallView` / `UninstallView` | ✅ |

**Material3 引用：243 → 14 处**（其中 4 处是 `Theme.kt` 为双主题特意保留的）。

剩余 10 处是签名差异较大、性价比低的：`RadioButton` ×2、`Checkbox` ×1、
`OutlinedTextField` ×1、`DropdownMenu` ×2、`DropdownMenuItem` ×2、`Surface` ×1、`OutlinedButton` ×1。

---

## 迁移脚本（都在 `D:\dnshe-release\`）

| 脚本 | 作用 |
|---|---|
| `pc_swap.py` | 取色角色 + 字号（把 `style = ` 前缀一起吃掉） |
| `pc_fix1.py` | 还原 Theme.kt 的 import、去重复 fontSize |
| `pc_buttons.py` | import 替换 + `TextButton`/`OutlinedButton` 转 Miuix 形式 |
| `pc_progress.py` | `LinearProgressIndicator`/`CircularProgressIndicator` 参数适配 |

> 这些脚本都是照着安卓端那套改的，**把安卓端踩过的三个坑提前修掉了**：
> 词边界、切片下标、替换作用域。所以 PC 端一次就过，没出现安卓那种 222 条错误。

---

## 🐞 修掉的 Bug：安装程序「浏览…」弹不出文件夹选择框

### 现象

安装向导第 4 步「选择安装位置」，点「浏览…」没有任何反应。

### 定位过程

1. 原来的代码是：
   ```kotlin
   runCatching {
       val fc = javax.swing.JFileChooser().apply { ... }
       if (fc.showOpenDialog(null) == ...) { ... }
   }
   ```
   **`runCatching` 把异常全吞了**，所以点了没反应也看不到任何提示。

2. 去掉吞异常后，AWT 弹出了一个标题为 **「Error」** 的对话框，内容是：
   ```
   Cannot invoke "java.awt.Image.getWidth(java.awt.image.ImageObserver)" because "<local6>" is null
   ```
   —— `JFileChooser` 在 **jpackage 打出来的精简运行时**里去取系统图标时拿到了 null，直接崩在内部。

3. 因为这个对话框是**同一个 java 进程**的子窗口，用 `Get-Process | Where MainWindowTitle` 是看不到的，
   必须用 `EnumWindows` + `GetWindowThreadProcessId` 枚举。

### 解决

**不用 Swing 的 JFileChooser，改用 Windows 原生对话框**：

`ui/WinFolderPicker.kt` —— 通过 **JNA**（本来就随 `compose.desktop` 传递进来）
调 `shell32.SHBrowseForFolderW` + `SHGetPathFromIDListW`，带 `BIF_NEWDIALOGSTYLE`
（可调整大小的新版外观）和 `BIF_EDITBOX`（带路径输入框）。

`Sys.pickDirectory()` 的优先级：**原生 → 失败才退回 Swing（而且异常不再吞）**。

顺带把 `pickSaveFile` / `pickOpenFile` 的 owner 也修正了：
`Sys` 现在记住 `mainWindow`（在 `Main.kt` 建窗口时塞进去）。
**不传 owner 的时候 Swing 会临时造一个隐藏的共享 owner 窗口**，
对话框不受主窗管辖，可能弹到主窗后面 —— 这也是"点了没反应"的常见成因。

### 教训

1. **`runCatching` 包住 UI 交互代码要慎重** —— 它会让你查不出问题。
   至少要 `onFailure` 里 log 一下。
2. **jpackage 的精简运行时会暴露平时遇不到的 AWT/Swing 问题**。
   弹出式 UI（文件框、消息框）优先用系统原生实现。
3. **同进程的子窗口**用 `Get-Process` 看不到，得 `EnumWindows`。

---

## 🐞 修掉的 Bug 2：设置里「关于」一直显示 1.0.0

### 现象

发版到 2.0.0 之后，侧栏显示的是 `PC v2.0.0`，但**设置 → 滑到最下面 → 关于**里还是 `1.0.0`。

### 原因

`SettingsView.kt` 第 44 行有一个**独立写死的常量**：

```kotlin
private const val APP_VERSION = "1.0.0"
```

它被三处用到（「关于」入口的副标题、关于详情页顶部、详情页的「版本」行），
而侧栏用的是另一套（`AppRoot.kt` 里的字符串字面量）。
改版本号时只改了侧栏那处，这个常量被漏掉了 —— 于是出现"同一页面两个版本号"。

### 解决

让版本号**只有一个数据源**：

```kotlin
/**
 * 版本号只从 WinPaths.VERSION 取，不要再在这里写死。
 * 之前这里是个 const val，发版时忘了改，结果「关于」页一直显示旧版本。
 */
private val APP_VERSION: String get() = WinPaths.VERSION
```

同时把 `AppRoot.kt` 侧栏那处也确认过（它读的是同一个来源）。

### 教训

**版本号只能有一个数据源**。散落的字符串字面量迟早对不上。
发版时用脚本全仓扫一遍 `"\d+\.\d+\.\d+"` 形式的字面量是最省事的兜底。

---

## 验证

| 项 | 结果 |
|---|---|
| `:desktop:compileKotlin` | ✅ BUILD SUCCESSFUL |
| `:desktop:createDistributable` | ✅ |
| IExpress 打包 | ✅ `dist\ShizukuDomainBox-Setup-v2.0.0.exe`（103.6 MB） |
| 安装到本机 | ✅ 版本 2.0.0，注册表 / 开始菜单 / 桌面快捷方式齐全 |
| 运行 | ✅ 无崩溃，Miuix 界面正常（`D:\miuix-trial\screens\pc-miuix-01.png`） |
| 「浏览…」按钮 | ✅ 原生文件夹选择框正常弹出 |
| 关于页版本号 | ✅ 侧栏 / 关于入口 / 关于详情 三处都是 2.0.0 |

## 发布产物校验（三源一致）

| 源 | EXE SHA-256 前缀 | 校验方式 |
|---|---|---|
| 官网 srv2 | `2F6427D7…` | 服务器端 `sha256sum` + 页面哈希 |
| GitHub | `2F6427D7…` | API `assets[].digest`（服务端计算） |
| GitCode | `2F6427D7…` | 实际下载后本地校验（5.8 MB/s） |



> 工程 `D:\dnshe-pc` · 备份 `D:\dnshe-pc-backup-material3`
> 与安卓端使用**同一套工具链和同一套迁移策略**

---

## 一、工具链（与安卓端一致）

| 组件 | 旧 | 新 |
|---|---|---|
| Gradle | 8.7 | **9.8.1** |
| JDK | 17 | **21.0.2** |
| Kotlin | 2.0.20 | **2.4.20** |
| Compose Multiplatform | 1.7.0 | **1.12.0** |
| kotlinx-serialization | 1.7.3 | 1.9.0 |
| kotlinx-coroutines | 1.8.1 | 1.10.2 |
| Miuix | — | **0.9.4** |

工具位置：
```
D:\Android\gradle-9.8.1\
D:\Android\jdk-21.0.2\
```

构建命令：
```bat
set JAVA_HOME=D:\Android\jdk-21.0.2
D:\Android\gradle-9.8.1\bin\gradle.bat -p D:\dnshe-pc :desktop:run
```

## 二、迁移策略：同样的「咽喉式迁移」

桌面端的所有页面也都通过 `desktop/ui/Common.kt` 取样式，所以**只改这一层**，
`DomainsView` / `AccountsView` / `ToolsView` / `SettingsView` 等 2500 行页面代码
不用动就整体换皮了。

### 已完成的文件

| 文件 | 状态 | 说明 |
|---|---|---|
| `ui/Theme.kt` | ✅ | Material3 + Miuix 双主题嵌套，品牌蓝作 Miuix primary |
| `ui/Common.kt` | ✅ | 22 个公共组件全部换成 Miuix 实现 |
| `ui/AppRoot.kt` | ✅ | 侧边栏图标换 `miuix-icons`，取色换 Miuix |

### Common.kt 组件映射

| 原组件 | 现实现 |
|---|---|
| `SectionTitle` | Miuix `Text` + `onSurfaceVariantSummary` 配色 |
| `AppCard` | Miuix `Card` + **显式 `insideMargin`**（默认是 0！） |
| `InfoRow` | Miuix `Text` / `IconButton` |
| `StatusPill` | 自绘 Box + Miuix 取色 |
| `AppTextField` | Miuix `TextField` + `useLabelAsPlaceholder` |
| `SettingSwitchRow` | Miuix `Switch` |
| `LoadingBox` / `ErrorBox` | Miuix `CircularProgressIndicator` / `TextButton` |
| `InlineMessage` | Miuix `Card` + `CardDefaults.defaultColors(color=...)` |
| `OverlayDialog`（自研） | 保留自定义浮层结构，取色与圆角改 Miuix |
| `DialogButtons` | Miuix `Button` / `TextButton` |
| `Divider` | Miuix `HorizontalDivider` |
| `ToolbarButton` | Miuix `TextButton` |

## 三、桌面端特有的注意点

### 1. 名字冲突

PC 端**自己实现过一个 `OverlayDialog`**（窗口内浮层，不是独立窗口），
而 Miuix 也有一个同名的 `OverlayDialog`。
处理方式：这个文件里**不 import Miuix 的那个**，继续用自研的，
只把取色和圆角改成 Miuix 的。已在代码注释里标明。

### 2. 桌面布局与手机不同

安卓是底部导航，PC 是**左侧竖栏**。Miuix 有 `NavigationRail` 可以用，
但它的内容区带 `scrollState`，`Spacer(Modifier.weight(1f))` 这种"底部固定区"的写法
在里面不一定生效。
**当前做法**：保留桌面化的自定义侧栏布局，只把**图标换成 `miuix-icons`、
取色换成 Miuix**，风险最小、观感也够。

### 3. Miuix Card 的两个坑（和安卓端一样）

- `InsideMargin` 默认 `PaddingValues(0.dp)` → 必须自己给内边距
- 内部 `propagateMinConstraints = true` 与 `Row.weight()` 组合会算小高度 → 避开

## 四、验证结果

| 项 | 结果 |
|---|---|
| `:desktop:compileKotlin` | ✅ BUILD SUCCESSFUL |
| `:desktop:run` 启动 | ✅ 窗口正常 |
| 实拍截图 | `screens/pc-miuix-01.png`（Common 换完）、`pc-miuix-02.png`（侧栏也换完） |
| 真实数据加载 | ✅ 6 个域名 + 配额 4/10 |

## 五、待完成

| 项 | 待办 |
|---|---|
| 顶部工具条 | 主界面顶部的「刷新 / 注册子域名」按钮区可换成 Miuix `TopAppBar`（带 actions） |
| 各页面内的 `MaterialTheme` 引用 | 还有约 200 处，按需逐步换 |
| 安装程序界面 | `InstallView.kt` / `UninstallView.kt` 也是 Compose，同样可以换 Miuix |
| 清理 | 全部迁完后可删 `compose.material3` 与 `compose.materialIconsExtended` 依赖 |
