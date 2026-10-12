# 踩过的坑

[HANDOFF.md 第 3 节](../HANDOFF.md) 那 9 条（jpackage 中文、窗口图标、JVM 锁 jar、
`${'$'}` 转义、PS 5.1 的 BOM、`Select-Object -First`、ToolbarButton 参数顺序、
`implementation` vs `api`、跨模块智能转换）**依然全部有效，先看那份**。

这里是抽工具的过程中**新踩出来的**。

---

## 1. `.cfg` 里的 `app.mainclass` 才是通用化的钥匙

原方案里安装程序的界面是**长在产品自己 jar 里**的（老工程的 `InstallView.kt` 就是产品
desktop 模块的一部分），所以 `Uninstall.cfg` 只要追加 `arguments=--uninstall` 就够了 ——
mainclass 不用动，产品自己的 `MainKt` 会分发。

抽成通用工具后产品不认识 kit，必须把 mainclass 一起换掉：

```ini
[Application]
app.mainclass=io.nekoshizuku.kit.app.MainKt     ← 换掉
app.classpath=$APPDIR\kit-installer-xxx.jar     ← 补上 kit 的 jar
app.classpath=...                               ← 产品原有的 classpath 一条都不能少

[ArgOptions]
arguments=--install
```

**产品自己那份 `MyApp.cfg` 一个字都不要动**，否则产品就启动不了了。

顺带一个坑：`Uninstall.cfg` 是**安装时**在目标目录里生成的，所以生成它需要的
「kit 的 mainclass」和「kit 的 classpath 列表」必须**随配置一起注入**
（就是 `installer.config.json` 里的 `wizardMainClass` / `wizardClasspath`）——
不能等运行期再去猜。

## 2. PowerShell 执行策略默认是 Restricted

`powershell -File tools\build-installer.ps1` 会直接报：

```
无法加载文件 ...\build-installer.ps1，因为在此系统上禁止运行脚本。
```

机器上 `Get-ExecutionPolicy -List` 全是 `Undefined`（= Restricted）。
解法：`-ExecutionPolicy Bypass`，或者用 `tools\*.cmd` 包装器。
**别去改系统策略**，包装器更干净。

## 3. 编辑过的 `.ps1` 会丢 BOM

HANDOFF 第 3 节第 5 条说了「含中文的 .ps1 必须带 BOM」，但没说的是：
**任何编辑工具改完都会把 BOM 抹掉**，然后下一次运行就炸。
所以有了 `tools\fix-bom.cmd` —— 用 `.cmd` 写它（cmd 不挑编码），避免
「修 BOM 的脚本自己需要 BOM」的死循环。

## 4. `.cmd` / `.bat` 必须纯 ASCII 注释

cmd.exe 按 ANSI（中文机器 = GBK）读 `.cmd`。用 UTF-8 写的中文 `rem` 注释会变成乱码，
而且**乱码里的词会被当成命令去执行**，刷一屏
`'很多编辑器' is not recognized as an internal or external command`。
测试逻辑不会错，但输出会脏得没法看，还可能在特殊字符处真的出错。
→ `.cmd` 里只写英文注释。

## 5. Python 脚本的 stdout 在 Windows 是 GBK

`print("✓ ...")` 直接 `UnicodeEncodeError: 'gbk' codec can't encode character '\u2713'`，
而且**脚本会在写文件写了一半的地方崩掉**（前面的循环已经执行了）。
`tools/make-icons.py` 开头就做了：

```python
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass
```

## 6. 同族 jar 的「内容哈希」不能当版本号用

jpackage 给 app-image 里的 jar 起名是 `名字-版本-<32 位内容哈希>.jar`。
**同一个版本、两次不同构建，哈希也不同**（比如 `skiko-awt-runtime-windows-x64-0.8.15`
在两边哈希就差一个字符）。如果按文件名判重，就会把同族 jar 重复注入一份，
还会误报「版本冲突」。

正确做法：剥掉末尾的 32 位哈希得到「家族 + 版本」，按家族判重；
版本真的不一样才报警。`tools/build-installer.ps1` 里的 `Get-JarIdentity` 就是干这个的。

## 7. Skiko 的原生 DLL 不在 jar 里

Compose Desktop 的 `app\` 目录里躺着一个 **17 MB 的 `skiko-windows-x64.dll`**，
是 compose 插件在 `createDistributable` 时解出来的，靠 cfg 里的
`-Dskiko.library.path=$APPDIR` 找到它。
那两个只有几百字节的 `skiko-awt-runtime-windows-x64-*.jar` 只是标记，没有内容。
→ 所以「注入 kit 的 jar」不会影响原生库；产品的运行时里已经有 `java.desktop` 就够了。

## 8. `-match` 用在数组上不是布尔值

```powershell
$modules = & java --list-modules
if ($modules -notmatch "java\.desktop@") { ... }   # ✗ 永远不成立/逻辑错
```

`-match` / `-notmatch` 的左值是数组时，返回的是**匹配到的元素**（数组），
拿去当条件判断就成了「非空即真」。
要么 `($modules -join "`n") -match "..."`，要么 `-not ($modules -match "...")`。

## 9. 运行时的模块里没有 `java.desktop` 会怎样

kit 的向导是 Compose 写的，需要 `java.desktop`。
产品的 jlink 运行时如果裁掉了它，安装包照样能打出来，但**双击 Setup.exe 直接报错**。

检查办法本身就有一个坑：**jpackage 的 app-image 里没有 `runtime\bin\java.exe`**
（只有 `java.dll`），所以 `java --list-modules` 根本跑不起来 —— 第一版就是这么
**静默跳过**自检的，等于没检查。正确做法是读 jlink 留下的 `runtime\release`，
里面有 `MODULES="..."`。

## 10. `reg.exe` 遇到「空格 + 引号」的值会**静默失败**

`QuietUninstallString` 的值长这样：`"C:\Program Files\X\Uninstall.exe" /S /KEEPDATA`
—— **同时有空格和引号**。

从 JVM 里 `ProcessBuilder("reg","add",key,"/v",name,"/t","REG_SZ","/d",value,"/f")` 传过去时，
Windows 会先把整条命令行拼成一个字符串、再交给 CRT 解析，于是 `/d` 那个参数被切碎，
reg.exe 报：

```
错误: 无效语法。
```

然后**一个字节都不写**。而我们把 reg.exe 的输出丢掉了，所以外部表现是
「别的键值都在，偏偏少了 `QuietUninstallString`」—— 极难查。

更迷惑的是：安装路径**没有**空格时，引号反而会被 CRT 吃掉。
所以同一份代码「有时有引号、有时没有」。

**解决**：写注册表别用 reg.exe，改成

```
powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -EncodedCommand <base64>
```

`-EncodedCommand` 收的是 Base64（UTF-16LE），脚本内容完全不经过命令行解析，
空格 / 引号 / 中文 / `%` 全都安全；而且可以**一次性把十几个值写完**，
比原来十几次进程调用还快。（读注册表仍然用 `reg query`，没有这个问题。）

**教训**：凡是用 `ProcessBuilder` 调外部 exe 的地方，都要看退出码，
否则失败是完全静默的；这个 bug 只在「值里带引号」时才触发，能藏很久。

## 11. jpackage 给 jar 起名的那截哈希，长度不固定

`<名字>-<版本>-<内容哈希>.jar`，而哈希**不是**稳定的 32 位：
这次就碰到 `skiko-awt-runtime-windows-x64-0.8.15-50f7a3f46ff786ddc511c218ee7ab6d.jar`
只有 **31** 位，产品那边同一个依赖却是 32 位。
判重写成 `^[0-9a-f]{32}$` 就会漏剥哈希 → 同族判定失效 → 白注入一份重复 jar。
用 `^[0-9a-f]{16,}$`。

## 12. `uninstallKeepDataByDefault` 不该管静默模式

它是「卸载向导默认勾哪个单选框」的意思。
如果静默模式也拿它兜底，`/S`（不保留）和 `/S /KEEPDATA`（保留）就没区别了 ——
第一次验收就是这么挂的。静默模式下，命令行开关是唯一依据。

## 13. 单文件包留下的 `%TEMP%\<appId>Setup` 会让**下一次**解压解出「半套」

`install.cmd` 的逻辑是：

```bat
if exist "%DEST%" rmdir /s /q "%DEST%"
mkdir "%DEST%"
tar -xf "%~dp0payload.zip" -C "%DEST%"
```

如果上一轮留下的 `%DEST%` 里有**被进程占住的文件**（典型：上一个向导窗口还开着，
或者上一个静默安装还没退干净），`rmdir` 会失败 → `mkdir` 也跟着失败 →
`tar` 就解进了一个「新旧混在一起、而且不完整」的目录。

外部症状极具误导性：`app\` 里少了 `Setup.cfg`、静默安装退出码非 0，
但你去翻 `payload.zip` 会发现**文件一个不少**。为了这个假象查了半天。

所以测试脚本里必须：先 `taskkill` 掉残留的 `Setup.exe`、**确认目录真的删掉了**，再往下走
（`tools\verify-install.ps1` 里的 `Stop-SetupProcesses` 就是干这个的）。

顺带一个不那么重要的观察：`tar` 是**边解边写**的，`Setup.exe` 有可能比整个 `app\`
先落地，所以判「解压完成」不能只看某一个文件存不存在。
对用户没影响 —— `install.cmd` 是顺序执行，`start Setup.exe` 一定在 `tar` 结束之后。

## 14. 改完 `.ps1` 一定要重跑 `fix-bom.cmd`

补一句不算坑但极容易忘的：**任何编辑工具写完 `.ps1`，BOM 就没了**。
本次就因为「先跑 fix-bom、后改文件」，下一次运行时 PowerShell 把 UTF-8 当 GBK 解，
报出一堆牛头不对马嘴的 `Unexpected token '}'`（而且行号是错的）。
所以 `fix-bom.cmd` 现在**顺带做语法预检**：编码坏了会立刻以「字符乱码 + 语法错误」的形式
暴露出来，而不是等到运行到一半才炸。

## 16. 签名必须「先里后外」

单文件安装包不是「把文件装进盒子」，而是
`发行目录 → payload.zip → IExpress **重新生成**`。所以签名顺序错了就白签：

| 顺序 | 结果 |
|---|---|
| 先签 stage → 压 payload → 封 Setup.exe → 签 Setup.exe | ✅ 两层都有签名 |
| 先封 Setup.exe 再签，回头才去签 stage | ✗ payload 里那份没签名；外层也可能被后续步骤覆盖 |

一句话：**谁最后改这个文件，谁就最后签它**。

## 17. jpackage 生成的 exe 是**只读**的，signtool 只会报 "Access is denied"

jpackage app-image 里的启动器 exe 属性是 `-ar---`（只读 + 归档）。
`Copy-Item` 会把只读属性一起带过去，于是 `signtool sign` 写不进去，只报一句：

```
SignTool Error: Access is denied.
```

这句极容易误导成「没有管理员权限」，其实只要在签名前摘掉只读：

```powershell
$item = Get-Item -LiteralPath $f -Force
if ($item.IsReadOnly) { $item.IsReadOnly = $false }
```

（`tools\build-installer.ps1` 的 `Invoke-SignFiles` 里已经做了。）

## 18. 签名会改文件字节，所以哈希清单必须最后算

签名是**往文件里追加**数据，同一个文件签名前后的 SHA-256 不一样。
`release.json`、`manifest.json` 里的哈希都必须排在所有签名**之后** ——
顺序反了，发出去的清单对不上，验的人会以为文件被篡改过。

## 19. `Import-Module` 在内联命令里会被执行策略挡住

`tools\build.cmd` 用的是 `-ExecutionPolicy Bypass -File`，所以 `.ps1` 和
`.psm1` 都能正常加载。但在**自己的 PowerShell 窗口**里手敲
`Import-Module D:\sing\codesign\ShizukuCodeSign.psm1` 会直接报
「在此系统上禁止运行脚本」。

要么 `powershell -ExecutionPolicy Bypass`，要么执行一次
`Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`。

## 21. 用 `powershell -File` 传数组参数：绑不上**不会报错**，参数直接没了

给 `tools\grab-window.ps1` 传点击坐标时踩的：

```powershell
powershell -File grab-window.ps1 -Click 946,690      # ✗
```

`-File` 后面的参数一律按**字符串**处理，`946,690` 会当成一个字符串 `"946,690"`
传给 `[int[]]$Click` —— 绑定失败，而 PowerShell **不报错**，`$Click` 就是空的。
于是「点击循环」压根没执行，表现成「点了没反应」，白查了半天 DPI / 前台窗口 / 输入注入。

改法：参数收成 `[string]`，自己在脚本里拆：

```powershell
[string]$Click = ""
$coords = @($Click -split '[,;\s]+' | Where-Object { $_.Trim() } | ForEach-Object { [int]$_ })
```

**教训**：`.ps1` 的数组/复杂类型参数，别指望 `-File` 能绑上；要么收字符串自己解析，
要么用 `-Command` 传（那是另一套解析规则）。

## 22. Compose Desktop 的窗口不吃「投递」的鼠标消息

想给向导截图做视觉验证，试过三种点击方式：

| 方式 | 结果 |
|---|---|
| `PostMessage(WM_LBUTTONDOWN/UP)` 直接投递给窗口 | ✗ 无效（Skiko 的输入链路不吃投递消息） |
| `SetCursorPos` + `mouse_event`（真实鼠标） | ✓ 有效（但要点得准，且会挪用户的鼠标） |
| 点 TextButton（「取消」） | ✓ 有效 |
| 点 filled Button（「下一步」） | ✗ 在这个环境下没反应（原因未查明） |

结论：**别指望用合成点击驱动 Compose 界面做自动化验收**。
要验界面就两条路：
1. 用「状态对比」代替点击——比如本轮验证「覆盖安装检测」，就是对比
   「装过 / 没装过」两种状态下向导的**步骤数**（8 步 vs 7 步）+ 步骤条里有没有那一项；
2. 把要验的逻辑下沉成**纯函数**，放进无头自检（`verifyCore` / `verifyUi`）。

真正可靠的是第 2 条：界面上少写逻辑，逻辑都能被自检覆盖。

## 24. IExpress 的自解压壳**不转发命令行参数**

`Xxx-Setup.exe /S` 不会静默安装 —— `/S` 被自解压壳吃掉了，它 `start` 出来的
`Setup.exe` 是**无参数**的，于是弹出图形向导在那儿干等。

这不是我们的 bug，是 IExpress 的固有行为（`AppLaunched` 启动时不带参数），
而且**没法绕**（`install.cmd` 里的 `%*` 永远是空的）。

所以：
- **想静默安装就用目录版**：`build\stage\Setup.exe /S /DIR=...`
- 或者先把单文件包解压（`tar -xf` 里面的 `payload.zip`），再跑里面的 `Setup.exe /S`
- 端到端验收里对「单文件包」的测试就是走这条路的

## 25. `$PSScriptRoot` 在 `param()` 的默认值里是**空的**

```powershell
param([string]$KitRoot = (Split-Path -Parent $PSScriptRoot))   # ✗ 绑定阶段就报错
```

`param()` 绑定发生在 `$PSScriptRoot` 被赋值**之前**，那里拿到的是空串，
`Split-Path -Parent ''` 直接抛错。改成进函数体再算：

```powershell
param([string]$KitRoot)
if (-not $KitRoot) { $KitRoot = Split-Path -Parent $PSScriptRoot }   # ✓
```

## 26. 刚构建完的 app-image，`Copy-Item` 会莫名其妙「访问被拒绝」

打包自己的打包器时踩的：`Copy-Item $appImage $stage -Recurse` 报

```
Access to the path 'D:\...\stage\runtime\bin\ucrtbase.dll' is denied.
```

但手动删掉目标、再手动复制**完全正常**，源文件也不是只读、不是链接、不是重解析点。
猜测是杀毒软件的实时扫描短暂锁住了刚由 Gradle/jpackage 生成的 DLL。

两个改动一起用就稳了：

1. **删旧目录时先摘只读属性、失败重试**（jpackage 生成的文件带只读，残渣会挡住下一次复制）；
2. **复制改用 `robocopy`**（`/E /R:3 /W:2`）——它按文件重试，几千个文件也快得多，
   退出码 0-7 都算成功。

**教训**：构建流水线里「删目录 + 复制目录」这两步千万别裸写 `Remove-Item` / `Copy-Item`。

## 28. `encodeDefaults = false` 会**静默丢掉**「等于默认值」的字段

kotlinx.serialization 默认 `encodeDefaults = false`：序列化时，**值恰好等于默认值的字段不写出来**。

这在「自己写、自己读」时没事（读的时候默认值兜底），但一跨语言/跨进程就出事：

- `version` 的默认值就是 `"1.0.0"` → 一份 version 为 1.0.0 的配置存出来**没有 version 字段**；
- `tools\verify-install.ps1` 要按字面拼产物文件名 `dist\<appId>-Setup-v<version>.exe`，
  读到空版本号 → 找的是 `KitDemoBox-Setup-v.exe` → 白跳过一整段检查；
- **更狠的连锁反应**：`codeSign.signInstaller` 的默认值是 `true`，被丢掉之后
  PowerShell 读到 `$null` → `if ($null)` 为假 → **外层安装包压根没签名**，
  而且不报错，直到验收里那句「单文件安装包已签名」失败才暴露。

**记法**：`InstallerConfigs.json` 里必须写 `encodeDefaults = true`。配置是给人看、给脚本读的，
不是内部缓存。

## 29. PowerShell 里 `$null` 是 falsy —— 读「缺省为真」的开关要显式兜底

```powershell
if ($cfg.codeSign.signInstaller) { ... }        # ✗ 字段没写 = 不签名（静默！）
$signInstaller = if ($null -eq $cfg.codeSign.signInstaller) { $true } else { [bool]$cfg.codeSign.signInstaller }   # ✓
```

配置里**没写**和**写了 false** 是两件事，但在 PowerShell 里都会被当成「假」。
凡是「缺省为真」的开关（`signInstaller` / `signStageFiles` / `verify` 这类），
都要先把 `$null` 归一成默认值再判断。

## 28. `Copy-Item src dst -Recurse` 在 `dst` 已存在时会**多套一层目录**

做便携模式时踩的：产品文件装出来变成了

```
安装目录\Windows11轻松设置1.12正式版\Windows11轻松设置.exe     ← 白多了一层
```

原因就是 `Copy-Item` 的语义：**目标目录不存在**时，`dst` 变成 `src` 的副本（内容铺进 `dst`）；
**目标目录已存在**时，它变成 `dst\src名\…`（把整个 src 塞进去）。
两种行为都不报错，所以特别隐蔽。

修法：**统一用 robocopy**（它永远是「把 src 的内容铺进 dst」），顺手还得到
按文件重试 + 几千个文件快得多这两个好处：

```powershell
& robocopy.exe $source $dest /E /NFL /NDL /NJH /NJS /NP /R:3 /W:2 | Out-Null
if ($LASTEXITCODE -ge 8) { throw "复制失败" }   # 0-7 都算成功
```

## 29. 便携模式的 payload 目录**不能删**

单文件安装包会把 payload 解压到 `%TEMP%\<AppId>Payload`，装完当然该清掉。
但**目录版**的 payload 就躺在安装包旁边 —— 第一版向导不看场合一律清，
结果拿目录版装了一次之后，那个发行目录就变成空壳，再也装不了了。

修法：只在 payload 位于 `%TEMP%` 底下时才清。

## 30. 静默分支里别碰界面代码

便携版向导的 `Resolve-BrandColor()` 里用了 `[System.Drawing.Color]`。
这个类型要 `Add-Type -AssemblyName System.Drawing` 之后才有 —— 而那行在界面段里，
静默分支根本走不到。结果**静默安装直接崩**：

```
Unable to find type [System.Drawing.Color].
```

修法：取色挪到 `Add-Type` 之后；**脚本里「两种模式共用」的顶层代码，不能依赖只在某一支里加载的东西。**

## 31. `fix-bom.cmd` 必须**递归**

便携版向导放在 `tools\portable\` 里，而当时的 fix-bom 只扫 `tools\*.ps1` 顶层那一层。
于是那两个脚本没有 BOM，被 PowerShell 5.1 按 ANSI 读成乱码，
安装包做出来一跑就是「Missing ')' in method call」—— **一个目录漏扫 = 一个装不上的安装包**。

## 32. IExpress 的 SED 里，here-string 结束符必须顶格

```powershell
$sed = @"
[Options]
$sedStrings[SourceFiles]      # ✗ 既会被当成数组下标，也让 "@ 没法顶格
$sedList"@                    # ✗ 结束符前面有东西 —— 报 "The string is missing the terminator"
"@
```

`"@` 只能出现在行首、后面不能跟任何字符。拼 SED 时把变量各占一行、结束符单独一行。

## 27. `Start-Process -Wait` 会把「卡住」变成「查不出来」

验收脚本一开始用 `Start-Process -Wait` 等安装程序退出。
结果某个安装进程一旦卡住，整个验收脚本就**永远挂在那儿**，
日志停在中间某一行，连「哪一步挂的」都看不出来。

改成 `-PassThru` + `$p.WaitForExit(超时毫秒)`：超时就强杀并返回 `-999`，
失败信息里直接带出来。**凡是等外部进程的地方都要有超时。**
