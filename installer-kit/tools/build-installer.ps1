# InstallerKit 一键打包
#
#   1) 构建产品 app-image        gradle <buildTask>
#   2) 构建安装器壳 app-image    gradle :app:createDistributable
#   3) 组装发行目录 stage\
#        · 复制产品 app-image
#        · Setup.exe = 主程序 exe 的副本
#        · 把 kit 需要、产品没有的 jar 注入 app\
#        · app\Setup.cfg = 原 cfg → 换 mainclass 成 kit 向导 → 补 classpath → 追加 --install
#        · app\installer.config.json = 配置（含内联的许可协议正文 + 向导入口信息）
#   4) 可选：IExpress 封成单个 Setup.exe
#   5) 产物 SHA256 写进 dist\release.json
#
# 用法:
#   powershell -File tools\build-installer.ps1
#   powershell -File tools\build-installer.ps1 -SkipBuild -SkipIExpress
#
# ⚠️ 这个脚本自己必须存成 **UTF-8 with BOM**：PowerShell 5.1 读无 BOM 的 .ps1
#    会按 ANSI 解，中文注释直接导致解析失败（HANDOFF 第 3 节第 5 条）。

[CmdletBinding()]
param(
    # 产品配置文件；缺省 = 本仓库根目录的 installer.config.json
    [string]$Config,
    # 跳过「构建产品 app-image」（直接用现成的）
    [switch]$SkipBuild,
    # 跳过「构建安装器壳」（直接用现成的）
    [switch]$SkipKitBuild,
    # 只产出目录，不封单文件 exe
    [switch]$SkipIExpress,
    # Gradle 与 JDK 位置
    [string]$Gradle = "D:\Android\gradle-8.7\bin\gradle.bat",
    [string]$JavaHome = "D:\Android\jdk-17"
)

$ErrorActionPreference = "Stop"
$OutputEncoding = [System.Text.Encoding]::UTF8
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}

# ---------------------------------------------------------------- 小工具

function Write-Utf8NoBom($path, $text) {
    $enc = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($path, $text, $enc)
}

function Read-Utf8($path) {
    return [System.IO.File]::ReadAllText($path, (New-Object System.Text.UTF8Encoding($false)))
}

function Say($text) { Write-Host $text }
function Step($text) { Write-Host ""; Write-Host "=== $text ===" -ForegroundColor Cyan }

<#
  删目录，删不掉就重试。
  ⚠️ 不能直接 Remove-Item：jpackage 生成的文件（启动器 exe）和从它复制过来的文件
  都带**只读**属性，而且上一轮失败时可能留下半套文件占着位。表现是 Copy-Item 报
  「对 ...ucrtbase.dll 的访问被拒绝」，看着像权限问题，其实是残渣。
  所以：先把只读全摘掉 → 删 → 没删干净就等一会儿再来。
#>
function Remove-DirectoryForce([string]$path, [int]$attempts = 4) {
    if (-not (Test-Path -LiteralPath $path)) { return $true }
    for ($i = 1; $i -le $attempts; $i++) {
        Get-ChildItem -LiteralPath $path -Recurse -Force -ErrorAction SilentlyContinue |
            ForEach-Object { try { if ($_.IsReadOnly) { $_.IsReadOnly = $false } } catch { } }
        Remove-Item -LiteralPath $path -Recurse -Force -ErrorAction SilentlyContinue
        if (-not (Test-Path -LiteralPath $path)) { return $true }
        Start-Sleep -Milliseconds 500
    }
    return (-not (Test-Path -LiteralPath $path))
}

<#
  把 $source 目录里的**内容**复制到 $dest（不是把 $source 整个塞进 $dest），失败就重试。

  为什么用 robocopy 而不是 Copy-Item：
    · Copy-Item 的语义有坑 —— 如果 $dest 已经存在，`Copy-Item src dst -Recurse` 会
      变成 `dst\src名\...`，凭空多一层目录（踩过：装出来的程序里多套了一层同名文件夹）。
      robocopy 永远是「把 src 的内容铺进 dst」，语义稳定。
    · 它自带按文件的 /R 重试；Gradle 刚生成的 DLL 会被杀毒软件短暂锁住，
      Copy-Item 会直接抛 UnauthorizedAccessException。
    · 几千个文件快得多。
  退出码 0-7 都算成功（>=8 才是真错误）。
#>
function Copy-TreeRetry([string]$source, [string]$dest, [int]$attempts = 3) {
    for ($i = 1; $i -le $attempts; $i++) {
        & robocopy.exe $source $dest /E /NFL /NDL /NJH /NJS /NP /R:3 /W:2 | Out-Null
        $rc = $LASTEXITCODE
        if ($rc -lt 8) { return }
        if ($i -ge $attempts) { throw "复制失败（robocopy 退出码 $rc）：$source -> $dest" }
        Write-Warning ("复制出错（robocopy 退出码 {0}），{1} 秒后重试" -f $rc, ($i * 2))
        Start-Sleep -Seconds ($i * 2)
        [void](Remove-DirectoryForce $dest)
    }
}

function Resolve-CfgPath([string]$p) {
    if ([string]::IsNullOrWhiteSpace($p)) { return $null }
    if ([System.IO.Path]::IsPathRooted($p)) { return [System.IO.Path]::GetFullPath($p) }
    return [System.IO.Path]::GetFullPath((Join-Path $configDir $p))
}

# ---------------------------------------------------------------- 数字签名

<#
  签名顺序是这件事的关键：**先签里面的，再签外面的**。

  单文件安装包是这么来的：发行目录 → payload.zip → IExpress 封 → Setup.exe。
  所以：
      给发行目录里的 exe 签名   →   压 payload.zip   →   封 Setup.exe   →   给 Setup.exe 签名
  反过来的话，外层那一步会重新生成文件，把先前的签名作废
  （而且 payload 里嵌的还是没签名的版本）。

  装到目标目录之后，Uninstall.exe 是主程序 exe 的**副本**，所以它自动继承签名，不用单独签。
#>

function Initialize-CodeSign {
    <# 读配置 + 导入 ShizukuCodeSign + 定位证书档案。没启用就返回 $null。#>
    $cs = $cfg.codeSign
    if (-not $cs -or -not $cs.enabled) { return $null }

    $toolsDir = Resolve-CfgPath $cs.toolsDir
    if (-not $toolsDir) { $toolsDir = 'D:\sing\codesign' }
    $modulePath = Join-Path $toolsDir 'ShizukuCodeSign.psm1'
    if (-not (Test-Path $modulePath)) {
        throw "找不到签名模块：$modulePath（检查 installer.config.json 里的 codeSign.toolsDir）"
    }
    Import-Module $modulePath -Force -DisableNameChecking

    $profileName = if ($cs.profile) { [string]$cs.profile } else { 'demo' }
    $pkiDir = Get-ShizukuProfileDir -Profile $profileName
    if (-not (Test-Path (Join-Path $pkiDir 'pki.json'))) {
        throw ("证书档案不存在：{0}（{1}）。先造一份：codesign.ps1 init -CommonName '署名' -Profile {0} -InstallTrust" -f $profileName, $pkiDir)
    }

    $report = Get-ShizukuPkiReport -PkiDir $pkiDir
    if (-not $report.Exists -or -not $report.CodeSigning) { throw "档案 $profileName 不完整，读不到签名证书。" }
    if (-not $report.LeafInStore -or -not $report.LeafHasKey) {
        throw "证书档案 $profileName 的签名证书不在证书库里（或没有私钥）。可能是在别的 Windows 账户下生成的。"
    }

    Say ("· 签名档案：{0}" -f $profileName)
    Say ("    签名者：{0}" -f $report.CodeSigning.Subject)
    Say ("    指纹  ：{0}   剩 {1} 天" -f $report.CodeSigning.Thumbprint, $report.CodeSigning.DaysLeft)
    Say ("    签名工具：{0}" -f $(if ($report.Signtool) { 'signtool' } else { 'Set-AuthenticodeSignature（没找到 signtool）' }))
    if (-not $report.TrustedRootCurrentUser) {
        Write-Warning "根证书还没被信任，验签会显示「不受信任的根」。跑：codesign.ps1 trust -Profile $profileName"
    }
    if (-not $report.PublisherCurrentUser) {
        Write-Warning "签名证书还没进「受信任的发布者」。跑：codesign.ps1 trust -Profile $profileName"
    }

    return [pscustomobject]@{
        PkiDir    = $pkiDir
        Profile   = $profileName
        Timestamp = [bool]$cs.timestamp
    }
}

function Invoke-SignFiles($sign, [string[]]$files, [string]$what) {
    if (-not $sign) { return }
    $files = @($files | Where-Object { $_ } | Sort-Object -Unique)
    if ($files.Count -eq 0) { return }

    Say ("· 给{0}的 {1} 个文件签名" -f $what, $files.Count)
    foreach ($f in $files) { Say ("    · {0}" -f (Split-Path -Leaf $f)) }

    # ⚠️ jpackage 生成的主程序 exe 是**只读**的（Mode 是 -ar---），
    # 而且 Copy-Item 会把只读属性一起带过来 —— signtool 写不进去只会报一句
    # "SignTool Error: Access is denied."，很容易误以为是没有管理员权限。
    # 签名前先把只读摘掉。
    foreach ($f in $files) {
        try {
            $item = Get-Item -LiteralPath $f -Force
            if ($item.IsReadOnly) { $item.IsReadOnly = $false }
        } catch { }
    }

    $p = @{ Path = $files; PkiDir = $sign.PkiDir; PassThru = $true }
    if ($sign.Timestamp) { $p['Timestamp'] = $true }
    $results = @(Invoke-ShizukuSign @p)
    $bad = @($results | Where-Object { -not $_.Ok })
    if ($bad.Count -gt 0) {
        foreach ($b in $bad) { Write-Warning ("签名失败：{0} — {1}" -f (Split-Path -Leaf $b.Path), $b.Error) }
        throw ("{0} 里有 {1} 个文件签名失败" -f $what, $bad.Count)
    }
}

function Test-SignedArtifacts($sign, [string[]]$files) {
    <# 签完立刻验一遍：状态必须是 Valid（也就是「有效」）#>
    if (-not $sign) { return }
    $files = @($files | Where-Object { $_ -and (Test-Path -LiteralPath $_) } | Sort-Object -Unique)
    if ($files.Count -eq 0) { return }

    Say "· 验签"
    $rows = @(Test-ShizukuSignature -Path $files -PassThru)
    $bad = @()
    foreach ($r in $rows) {
        $trusted = if ($r.Trusted) { '可信' } else { '未受信任' }
        Say ("    {0,-34} {1,-10} {2}" -f (Split-Path -Leaf $r.Path), $r.Status, $trusted)
        if ($r.Status -ne 'Valid') { $bad += $r }
    }
    if ($bad.Count -gt 0) {
        $names = ($bad | ForEach-Object { Split-Path -Leaf $_.Path }) -join ', '
        throw "验签没通过：$names"
    }
}

# ---------------------------------------------------------------- 读配置

$kitRoot = Split-Path -Parent $PSScriptRoot
if (-not $Config) { $Config = Join-Path $kitRoot "installer.config.json" }
if (-not (Test-Path $Config)) { throw "找不到配置文件：$Config" }
$configPath = (Resolve-Path $Config).Path
$configDir = Split-Path -Parent $configPath

$cfg = (Read-Utf8 $configPath) | ConvertFrom-Json

$productName     = if ($cfg.productName) { $cfg.productName } else { "MyApp" }
$appId           = if ($cfg.appId) { $cfg.appId } else { "MyApp" }
$version         = if ($cfg.version) { $cfg.version } else { "1.0.0" }
$mainExeName     = if ($cfg.mainExeName) { $cfg.mainExeName } else { "$appId.exe" }
$installDirName  = if ($cfg.installDirName) { $cfg.installDirName } else { $appId }
$baseCfgName     = [System.IO.Path]::GetFileNameWithoutExtension($mainExeName) + ".cfg"

$buildRoot   = if ($cfg.buildRoot) { Resolve-CfgPath $cfg.buildRoot } else { $configDir }
$mainAppDir  = Resolve-CfgPath $cfg.mainAppDir
$iconFile    = Resolve-CfgPath $cfg.iconFile
$licenseFile = Resolve-CfgPath $cfg.licenseFile

$stage     = Join-Path $kitRoot "build\stage"
$dist      = Join-Path $kitRoot "dist"
$kitImage  = Join-Path $kitRoot "app\build\compose\binaries\main\app\InstallerKit"
$kitAppDir = Join-Path $kitImage "app"

Say "产品      : $productName  ($appId v$version)"
Say "配置文件  : $configPath"
Say "主程序    : $mainAppDir"
Say "发行目录  : $stage"

if (-not $mainAppDir) { throw "配置里没有 mainAppDir" }

# ---------------------------------------------------------------- 打包模式
#
# jpackage 模式：产品是 jpackage 生成的 app-image（自带 runtime\）→ 把安装器塞进它自带的运行时，
#               **安装包体积 ≈ 产品体积**。界面是那套 Compose 向导。
# portable 模式：产品就是普通文件夹（一个 exe + 一堆文件，也就是绿色软件）→ 用系统自带的
#               .NET/WinForms 写个轻量向导，**安装包只比原程序大几十 KB**。
#               代价：界面朴素些；卸载走安装目录里的 Uninstall.ps1。
#
# 不写 installMode 就自动判断：有 app\ 也有 runtime\ 才算 jpackage，否则一律按绿色软件处理。
$installMode = if ($cfg.installMode) { [string]$cfg.installMode } else { "" }
if (-not $installMode) {
    $looksJpackage = (Test-Path (Join-Path $mainAppDir "app")) -and (Test-Path (Join-Path $mainAppDir "runtime"))
    $installMode = if ($looksJpackage) { "jpackage" } else { "portable" }
}
$portable = ($installMode -eq "portable")
# 便携模式也想要那套一模一样的 Compose 向导？把 portableWizard 设成 "full"。
# 代价：安装包要多背约 57MB 运行时（绿色软件本身可能才 0.5MB）。
$portableFull = $portable -and ([string]$cfg.portableWizard -eq 'full')
$needKitImage = (-not $portable) -or $portableFull
$modeText = if (-not $portable) { "jpackage 模式（共用产品自带运行时）" }
            elseif ($portableFull) { "便携模式 + 完整向导（Compose 界面，安装包大 ~57MB）" }
            else { "便携模式 + 轻量向导（绿色软件，安装包很小）" }
Say ("打包模式  : {0}" -f $modeText)

# 数字签名（没启用就是 $null，后面所有签名步骤都会自动跳过）
$codeSign = Initialize-CodeSign
$stageTargets = @()

# 开关类字段一律「缺省为真」：配置里没写 ≠ 不要。
# （$null 在 PowerShell 里是 falsy，直接读会静默跳过签名 —— 这个坑真踩过。）
$signStageFiles = if ($null -eq $cfg.codeSign.signStageFiles) { $true } else { [bool]$cfg.codeSign.signStageFiles }
$signInstaller = if ($null -eq $cfg.codeSign.signInstaller) { $true } else { [bool]$cfg.codeSign.signInstaller }
$signVerify = if ($null -eq $cfg.codeSign.verify) { $true } else { [bool]$cfg.codeSign.verify }

# ---------------------------------------------------------------- 1. 构建产品

$env:JAVA_HOME = $JavaHome

if (-not $SkipBuild) {
    if (-not $cfg.buildTask) {
        Say "· 配置里没写 buildTask，跳过构建产品（直接使用现成的 app-image）"
    } else {
        Step "1/6 构建产品 app-image（$($cfg.buildTask)）"
        & $Gradle -p $buildRoot $cfg.buildTask --console=plain
        if ($LASTEXITCODE -ne 0) { throw "构建产品失败：$($cfg.buildTask)（退出码 $LASTEXITCODE）" }
    }
}

if (-not (Test-Path $mainAppDir)) { throw "找不到程序文件夹：$mainAppDir" }
$productExe = Join-Path $mainAppDir $mainExeName
if (-not (Test-Path $productExe)) { throw "程序文件夹里没有主程序：$productExe" }

if ($portable) {
    Say "· 便携模式：跳过 jpackage 的 app\ 与运行时检查"
    if (Test-Path (Join-Path $mainAppDir "app")) {
        Write-Warning "这个文件夹里有 app\ 但没有 runtime\，所以按绿色软件打了。"
        Write-Warning "  要按 jpackage 打就补上 runtime\，或者在配置里写 `"installMode`": `"jpackage`"。"
    }
} else {
$productCfg = Join-Path $mainAppDir "app\$baseCfgName"
if (-not (Test-Path $productCfg)) { throw "app-image 里没有启动配置：$productCfg" }

# 运行时模块自检：kit 的 Compose 界面需要 java.desktop
# 注意：jpackage 的 app-image 会把 runtime\bin\java.exe 去掉（只留 java.dll），
# 所以不能用 `java --list-modules`，改成读 jlink 留下的 runtime\release（里面有 MODULES=）。
$productRelease = Join-Path $mainAppDir "runtime\release"
if (Test-Path $productRelease) {
    $releaseText = Read-Utf8 $productRelease
    if (($releaseText -match "java\.desktop") -eq $false) {
        Write-Warning "产品的运行时**没有 java.desktop**，kit 的向导界面起不来！"
        Write-Warning "  修法：在产品模块的 jpackage 配置里加 modules(\"java.desktop\") 后重新构建。"
        Write-Warning "  （安装程序本身还是能生成，但双击 Setup.exe 会直接报错。）"
    } else {
        Say "· 运行时自检：java.desktop 在 ✓"
    }
} else {
    Say "· 没有 runtime\release，跳过 java.desktop 自检"
}
}

# ---------------------------------------------------------------- 2. 构建安装器壳
#
# 只有「轻量向导」的便携模式不需要这套 Compose 壳子，跳过能省一分多钟。

if ($needKitImage) {
if (-not $SkipKitBuild) {
    Step "2/6 构建安装器壳 app-image（:app:createDistributable）"
    & $Gradle -p $kitRoot ":app:createDistributable" --console=plain
    if ($LASTEXITCODE -ne 0) { throw "构建安装器壳失败（退出码 $LASTEXITCODE）" }
}

if (-not (Test-Path $kitAppDir)) { throw "找不到安装器壳的 app-image：$kitImage" }

$kitCfgFile = Get-ChildItem (Join-Path $kitAppDir "*.cfg") | Select-Object -First 1
if (-not $kitCfgFile) { throw "安装器壳里没有 .cfg：$kitAppDir" }
$kitCfgText = Read-Utf8 $kitCfgFile.FullName
$mMain = [regex]::Match($kitCfgText, "(?m)^\s*app\.mainclass\s*=\s*(.+?)\s*$")
if (-not $mMain.Success) { throw "安装器壳的 cfg 里没有 app.mainclass：$($kitCfgFile.FullName)" }
$wizardMainClass = $mMain.Groups[1].Value.Trim()
Say "· 向导入口类：$wizardMainClass"
}

# ---------------------------------------------------------------- 3. 组装发行目录

Step "3/6 组装发行目录"

if (-not (Remove-DirectoryForce $stage)) {
    throw "清理旧的发行目录失败：$stage（多半是某个进程还占着它 —— 先关掉打包器/安装向导再试）"
}
New-Item -ItemType Directory -Force -Path (Split-Path $stage) | Out-Null

if ($portableFull) {
    # 便携模式 + 完整向导：stage = 安装器本体（含它自带的运行时）+ 产品文件。
    # 产品没有 jpackage 启动器可以劫持，所以这次是**安装器自己**当安装器：
    # 把 InstallerKit.exe 改名成 Setup.exe，它按「同名 .cfg」读到的还是我们自己的向导。
    Copy-TreeRetry $kitImage $stage

    $kitExeFile = Join-Path $stage "InstallerKit.exe"
    $kitCfgInImage = Join-Path $stage "app\InstallerKit.cfg"
    if (-not (Test-Path $kitExeFile)) { throw "安装器壳里找不到 InstallerKit.exe：$kitExeFile" }
    if (-not (Test-Path $kitCfgInImage)) { throw "安装器壳里找不到 app\InstallerKit.cfg：$kitCfgInImage" }

    Move-Item $kitExeFile (Join-Path $stage "Setup.exe") -Force
    Move-Item $kitCfgInImage (Join-Path $stage "app\Setup.cfg") -Force
    Say "· Setup.exe = 安装器本体改名（同一个 exe、同一份运行时，靠名字分辨身份）"

    Copy-TreeRetry $mainAppDir (Join-Path $stage "app\payload")
    Copy-Item $configPath (Join-Path $stage "app\installer.config.json") -Force
    Copy-Item (Join-Path $kitRoot "tools\portable\uninstall.ps1") (Join-Path $stage "app\uninstall-template.ps1") -Force

    $stagePayload = Join-Path $stage "app\payload"
    $payloadFiles = Get-ChildItem $stagePayload -Recurse -File -Force
    $payloadSize = ($payloadFiles | Measure-Object -Property Length -Sum).Sum
    Say ("· app\payload\ = 产品文件：{0} 个文件 / {1:N1} MB" -f $payloadFiles.Count, ($payloadSize / 1MB))
    Say "· 卸载器：安装时生成 Uninstall.ps1（不带运行时，省 57MB）"

    if ($mainExeName -ieq "Setup.exe") { Write-Warning "主程序叫 Setup.exe，和安装器撞名，换个名字吧。" }

    $size = (Get-ChildItem $stage -Recurse -File | Measure-Object -Property Length -Sum).Sum
    Say ("· 发行目录：{0}  ({1:N1} MB)" -f $stage, ($size / 1MB))
} elseif ($portable) {
    # 便携模式：发行目录 = 「产品文件 + 轻量向导」，可以直接双击 install.cmd 装，
    # 也是 IExpress 要打包的那一堆东西。
    $stagePayload = Join-Path $stage "payload"
    Copy-TreeRetry $mainAppDir $stagePayload
    Copy-Item (Join-Path $kitRoot "tools\portable\wizard.ps1") $stage -Force
    Copy-Item (Join-Path $kitRoot "tools\portable\uninstall.ps1") $stage -Force
    Copy-Item $configPath (Join-Path $stage "installer.config.json") -Force

    $payloadFiles = Get-ChildItem $stagePayload -Recurse -File -Force
    $payloadSize = ($payloadFiles | Measure-Object -Property Length -Sum).Sum
    Say ("· payload\ = 产品文件：{0} 个文件 / {1:N1} MB" -f $payloadFiles.Count, ($payloadSize / 1MB))
    Say "· 安装向导：wizard.ps1（轻量，用系统自带的 .NET，不背运行时）"

    # 目录版也得能直接双击装（payload 就在旁边，不用解压）
    # /S 这类 Windows 风格的开关，在批处理里翻译成 PowerShell 参数再递进去
    $argShim = @(
        'set "PSARGS=%*"',
        'set "PSARGS=%PSARGS:/S=-Silent%"',
        'set "PSARGS=%PSARGS:/REMOVEDATA=-RemoveData%"',
        'set "PSARGS=%PSARGS:/KEEPDATA=-KeepData%"'
    ) -join "`r`n"
    $dirCmdText = "@echo off`r`n" + $argShim + "`r`n" +
        "powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"%~dp0wizard.ps1`" -Payload `"%~dp0payload`" %PSARGS%`r`n" +
        "exit /b %ERRORLEVEL%`r`n"
    Write-Utf8NoBom (Join-Path $stage "install.cmd") $dirCmdText

    if ($mainExeName -ieq "Setup.exe") { Write-Warning "主程序叫 Setup.exe，和安装器撞名，换个名字吧。" }
} else {

Copy-TreeRetry $mainAppDir $stage

$stageAppDir = Join-Path $stage "app"

# Setup.exe：主程序 exe 的副本。启动器按「和自己同名的 .cfg」读配置，
# 所以 Setup.exe 会去读 app\Setup.cfg —— 同一个 exe、同一份运行时，多出安装身份。
if ($mainExeName -ieq "Setup.exe") { Write-Warning "主程序本来就叫 Setup.exe，会和安装器撞名，请改 mainExeName。" }
Copy-Item (Join-Path $stage $mainExeName) (Join-Path $stage "Setup.exe") -Force
Say "· Setup.exe = $mainExeName 的副本"

# 产品原有 cfg 的文本 + 它已经在用的 classpath（下面判断「产品已有的 jar 在不在 classpath 上」要用）
$baseCfgText = Read-Utf8 (Join-Path $stageAppDir $baseCfgName)
$baseCfgJars = @{}
foreach ($line in ($baseCfgText -split "`r?`n")) {
    if ($line -match '^\s*app\.classpath\s*=\s*(.+)$') {
        $baseCfgJars[(Split-Path $Matches[1].Trim() -Leaf).ToLower()] = $true
    }
}

# jar 身份：`material3-desktop-1.7.0-<32位内容哈希>.jar` -> family=material3-desktop / version=1.7.0
# 内容哈希是 jpackage 按文件内容算的，同一次构建里稳定，但不同构建可能不同 —— 所以要用「家族」比对。
function Get-JarIdentity([string]$fileName) {
    $n = $fileName -replace '\.jar$', ''
    $parts = @($n -split '-')
    # 末尾那截「内容哈希」长度不固定（实测见过 31 位和 32 位的），所以别写死 32
    if ($parts.Count -gt 1 -and $parts[-1] -match '^[0-9a-f]{16,}$') {
        $parts = @($parts[0..($parts.Count - 2)])
    }
    $fam = @()
    $ver = ''
    foreach ($p in $parts) {
        if ($ver -eq '' -and $p -match '^\d') { $ver = $p } else { $fam += $p }
    }
    if ($ver -eq '') { return @{ family = ($parts -join '-'); version = '' } }
    return @{ family = ($fam -join '-'); version = $ver }
}

$existingByName = @{}
$existingByFamily = @{}
Get-ChildItem -Path (Join-Path $stageAppDir "*.jar") | ForEach-Object {
    $existingByName[$_.Name.ToLower()] = $true
    $id = Get-JarIdentity $_.Name
    if (-not $existingByFamily.ContainsKey($id.family)) {
        $existingByFamily[$id.family] = @{ name = $_.Name; version = $id.version }
    }
}

$copiedJars = New-Object System.Collections.Generic.List[string]
$extraClasspathJars = New-Object System.Collections.Generic.List[string]
$skippedJars = New-Object System.Collections.Generic.List[string]

foreach ($jar in (Get-ChildItem -Path (Join-Path $kitAppDir "*.jar"))) {
    if ($existingByName.ContainsKey($jar.Name.ToLower())) {
        $skippedJars.Add("$($jar.Name)  [产品已有同名]")
        continue
    }

    $id = Get-JarIdentity $jar.Name
    $hit = $null
    if ($existingByFamily.ContainsKey($id.family)) { $hit = $existingByFamily[$id.family] }

    if ($null -ne $hit) {
        if ($hit.version -ne $id.version) {
            Write-Warning (("同族依赖版本不一致：产品里是 {0}（v{1}），kit 需要 v{2}。" +
                "保留产品那份（classpath 顺序在前）；若运行时报 NoSuchMethodError，" +
                "就把两边的依赖版本对齐再打包。") -f $hit.name, $hit.version, $id.version)
        }
        if ($baseCfgJars.ContainsKey($hit.name.ToLower())) {
            $skippedJars.Add("$($jar.Name)  [产品已有同族 $($hit.name)，且已在 classpath 上]")
        } else {
            $extraClasspathJars.Add($hit.name)
            $skippedJars.Add("$($jar.Name)  [产品已有同族 $($hit.name)，只补 classpath]")
        }
        continue
    }

    Copy-Item $jar.FullName (Join-Path $stageAppDir $jar.Name) -Force
    $copiedJars.Add($jar.Name) | Out-Null
}

# 最终要挂进 Setup.cfg / Uninstall.cfg 的 classpath 条目
$classpathAdds = New-Object System.Collections.Generic.List[string]
foreach ($j in $copiedJars) { $classpathAdds.Add($j) }
foreach ($j in $extraClasspathJars) { if (-not $classpathAdds.Contains($j)) { $classpathAdds.Add($j) } }

Say ("· 注入 kit 的 jar：{0} 个" -f $copiedJars.Count)
foreach ($j in $copiedJars) { Say "    + $j" }
if ($skippedJars.Count -gt 0) {
    Say ("· 跳过 {0} 个（产品的 app-image 里已经有）" -f $skippedJars.Count)
    foreach ($j in $skippedJars) { Say "    = $j" }
}
if ($extraClasspathJars.Count -gt 0) {
    Say ("· 另外补进 classpath：{0} 个" -f $extraClasspathJars.Count)
    foreach ($j in $extraClasspathJars) { Say "    ~ $j" }
}

# app\Setup.cfg
$setupCfg = $baseCfgText
if ($setupCfg -match "(?m)^\s*app\.mainclass\s*=") {
    $setupCfg = [regex]::Replace($setupCfg, "(?m)^\s*app\.mainclass\s*=.*$", "app.mainclass=$wizardMainClass")
} else {
    $setupCfg = [regex]::Replace(
        $setupCfg,
        "(?m)^(\[Application\]\s*)$",
        "`$1`r`napp.mainclass=$wizardMainClass")
}

if ($classpathAdds.Count -gt 0) {
    $lines = New-Object System.Collections.Generic.List[string]
    $lines.AddRange([string[]]($setupCfg -split "`r?`n"))
    $lastCp = -1
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match "^\s*app\.classpath\s*=") { $lastCp = $i }
    }
    if ($lastCp -lt 0) { throw "产品 cfg 里没有 app.classpath，格式不对：$baseCfgName" }
    $newLines = @()
    foreach ($j in $classpathAdds) { $newLines += "app.classpath=`$APPDIR\$j" }
    $lines.InsertRange($lastCp + 1, [string[]]$newLines)
    $setupCfg = ($lines -join "`r`n")
}

$setupCfg = $setupCfg.TrimEnd() + "`r`n`r`n[ArgOptions]`r`narguments=--install`r`n"
Write-Utf8NoBom (Join-Path $stageAppDir "Setup.cfg") $setupCfg
Say "· app\Setup.cfg 已生成（mainclass → kit 向导，arguments=--install）"

# app\installer.config.json —— 打包期注入，运行期读取
$licenseText = ""
if ($licenseFile) {
    if (Test-Path $licenseFile) {
        $licenseText = (Read-Utf8 $licenseFile).TrimEnd()
        Say "· 许可协议已内联（$([System.IO.Path]::GetFileName($licenseFile))，$($licenseText.Length) 字）"
    } else {
        Write-Warning "配置里的 licenseFile 不存在：$licenseFile（向导会跳过许可页）"
    }
}

$injected = [ordered]@{}
foreach ($p in $cfg.PSObject.Properties) {
    if ($p.Name.StartsWith("_")) { continue }   # 丢掉 _说明 这类注释键
    $injected[$p.Name] = $p.Value
}
$injected["licenseText"] = $licenseText
$injected["wizardMainClass"] = $wizardMainClass
$injected["wizardClasspath"] = @($classpathAdds | ForEach-Object { "`$APPDIR\$_" })

$injectedJson = $injected | ConvertTo-Json -Depth 10
Write-Utf8NoBom (Join-Path $stageAppDir "installer.config.json") $injectedJson
Say "· app\installer.config.json 已注入"

# 产品外观：把 logo 和窗口图标复制进 app\，向导优先用产品自己的（找不到才退回 kit 内置的）
if ($logoFile) {
    if (Test-Path $logoFile) {
        Copy-Item $logoFile (Join-Path $stageAppDir "kit-logo.png") -Force
        Say "· 产品 logo 已注入：app\kit-logo.png"
    } else {
        Write-Warning "配置里的 logoFile 不存在：$logoFile（向导会用 kit 内置 logo）"
    }
}

$windowIconDir = Resolve-CfgPath $cfg.windowIconDir
if ($windowIconDir) {
    if (Test-Path $windowIconDir) {
        $sizes = if ($cfg.windowIconSizes) { $cfg.windowIconSizes } else { @(16, 20, 24, 32, 40, 48, 64, 96, 128, 256) }
        $iconCopied = 0
        foreach ($s in $sizes) {
            $src = Join-Path $windowIconDir "app-icon-$s.png"
            if (Test-Path $src) {
                Copy-Item $src (Join-Path $stageAppDir "kit-icon-$s.png") -Force
                $iconCopied++
            }
        }
        if ($iconCopied -gt 0) {
            Say "· 产品窗口图标已注入：$iconCopied 个尺寸 -> app\kit-icon-*.png"
        } else {
            Write-Warning "windowIconDir 里没找到 app-icon-<尺寸>.png：$windowIconDir（向导会用 kit 内置图标）"
        }
    } else {
        Write-Warning "配置里的 windowIconDir 不存在：$windowIconDir（向导会用 kit 内置图标）"
    }
}

# 自检：三样东西必须都在
$checkCfg = Read-Utf8 (Join-Path $stageAppDir "Setup.cfg")
if ($checkCfg -notmatch [regex]::Escape("app.mainclass=$wizardMainClass")) { throw "Setup.cfg 的 mainclass 没换成功" }
if ($checkCfg -notmatch "(?m)^arguments=--install\s*$") { throw "Setup.cfg 没有 --install" }
if (-not (Test-Path (Join-Path $stageAppDir "installer.config.json"))) { throw "installer.config.json 没写进去" }
foreach ($j in $classpathAdds) {
    if ($checkCfg -notmatch [regex]::Escape($j)) { throw "Setup.cfg 里少了 classpath：$j" }
}

$size = (Get-ChildItem $stage -Recurse -File | Measure-Object -Property Length -Sum).Sum
Say ("· 发行目录：{0}  ({1:N1} MB)" -f $stage, ($size / 1MB))
Get-ChildItem $stage | ForEach-Object { Say "    $($_.Name)" }

}   # ← jpackage 模式组装结束

New-Item -ItemType Directory -Force -Path $dist | Out-Null

# ---------------------------------------------------------------- 4. 数字签名（内层）

if ($portable -and (-not $portableFull)) {
    Step "4/6 便携模式：产品文件原样发布"
    Say "· 绿色软件是人家的东西，里面的 exe 不去动它 —— 只签我们自己做出来的安装包"
} elseif ($codeSign) {
    Step "4/6 给发行目录里的可执行文件签名"
    if (-not $signStageFiles) {
        Say "· codeSign.signStageFiles = false，跳过发行目录的签名"
    } else {
    $globs = @($cfg.codeSign.includeGlobs)
    if ($globs.Count -eq 0) { $globs = @('*.exe') }
    foreach ($g in $globs) {
        $stageTargets += Get-ChildItem -Path (Join-Path $stage $g) -File -ErrorAction SilentlyContinue
    }
    $stageTargets = @($stageTargets | ForEach-Object { $_.FullName })
    if ($stageTargets.Count -eq 0) {
        Write-Warning "发行目录里没有匹配到要签名的文件（codeSign.includeGlobs = $($globs -join ', ')）"
    } else {
        Invoke-SignFiles $codeSign $stageTargets "发行目录（解压出来的那份）"
    }
    }
} else {
    Step "4/6 跳过数字签名（codeSign.enabled = false）"
}

# ---------------------------------------------------------------- 5. 封单文件

function Invoke-IExpress {
    param(
        [string]$target,
        [string]$stageDir,           # jpackage 模式：压缩整个发行目录（整棵 app-image）
        [string]$payloadSourceDir,   # 便携模式：只压缩产品文件那个子目录
        [string]$appIdForTemp,
        [string[]]$extraFiles = @(), # 额外塞进自解压包的散文件（wizard.ps1 之类）
        [string]$launchLine = ""     # 解压之后要执行的那一行
    )
    $sfxStaging = Join-Path $kitRoot "build\sfx"
    if (Test-Path $sfxStaging) { [void](Remove-DirectoryForce $sfxStaging) }
    New-Item -ItemType Directory -Force -Path $sfxStaging | Out-Null

    # IExpress 的 SED 要逐条列文件，两千个文件不现实；
    # 所以把整套程序打成一个 payload.zip，再用一个 cmd 负责解压 + 启动。
    $zipFrom = if ($payloadSourceDir) { $payloadSourceDir } else { $stageDir }
    $payloadZip = Join-Path $sfxStaging "payload.zip"
    Say "    压缩 payload.zip …"
    & tar.exe -a -c -f $payloadZip -C $zipFrom .
    if ($LASTEXITCODE -ne 0) { throw "tar 打包失败（退出码 $LASTEXITCODE）" }
    Say ("    payload.zip: {0:N1} MB" -f ((Get-Item $payloadZip).Length / 1MB))

    # 散文件跟着一起进自解压包
    $sfxExtras = @()
    foreach ($f in $extraFiles) {
        Copy-Item -LiteralPath $f -Destination $sfxStaging -Force
        $sfxExtras += (Split-Path -Leaf $f)
    }

    $installCmd = Join-Path $sfxStaging "install.cmd"
    $cmdText = "@echo off`r`n" +
        "set `"DEST=%TEMP%\$appIdForTemp`"`r`n" +
        "if exist `"%DEST%`" rmdir /s /q `"%DEST%`"`r`n" +
        "mkdir `"%DEST%`"`r`n" +
        "tar -xf `"%~dp0payload.zip`" -C `"%DEST%`"`r`n" +
        $launchLine + "`r`n" +
        "exit`r`n"
    Write-Utf8NoBom $installCmd $cmdText

    if (Test-Path $target) { Remove-Item $target -Force }

    # SED 里文件要一个个列出来
    $sedFiles = @("payload.zip", "install.cmd") + $sfxExtras
    $sedStrings = ""
    $sedList = ""
    for ($i = 0; $i -lt $sedFiles.Count; $i++) {
        $sedStrings += "FILE$i=`"$($sedFiles[$i])`"`r`n"
        $sedList += "%FILE$i%=`r`n"
    }

    $sed = @"
[Version]
Class=IEXPRESS
SEDVersion=3
[Options]
PackagePurpose=InstallApp
ShowInstallProgramWindow=0
HideExtractAnimation=0
UseLongFileName=1
InsideCompressed=0
CAB_FixedSize=0
CAB_ResvCodeSigning=0
RebootMode=N
InstallPrompt=
DisplayLicense=
FinishMessage=
TargetName=$target
FriendlyName=$productName Setup
AppLaunched=install.cmd
PostInstallCmd=<None>
AdminQuietInstCmd=
UserQuietInstCmd=
SourceFiles=SourceFiles
[Strings]
$sedStrings
[SourceFiles]
SourceFiles0=$sfxStaging\
[SourceFiles0]
$sedList
"@

    $sedPath = Join-Path $kitRoot "build\setup.sed"
    Write-Utf8NoBom $sedPath $sed

    Say "    运行 IExpress（大包会比较慢，请耐心等）…"
    $p = Start-Process -FilePath "$env:SystemRoot\System32\iexpress.exe" `
        -ArgumentList "/N", "/Q", "$sedPath" -Wait -PassThru
    Say "    IExpress 退出码: $($p.ExitCode)"

    if (-not (Test-Path $target)) { throw "IExpress 没有产出目标文件：$target" }
    return $target
}

$singleExe = $null
if ($SkipIExpress) {
    Step "5/6 跳过 IExpress（只输出目录）"
} else {
    Step "5/6 用 IExpress 封成单个 Setup.exe"
    # 完整向导版加个 -full 后缀：不然和轻量版同名，先后打出来会互相覆盖
    $exeSuffix = if ($portable -and $portableFull) { "-full" } else { "" }

    if ($portableFull) {
        # 完整向导这条路：stage 整个就是安装器本体的 app-image，跟 jpackage 模式封法一样
        $singleExe = Invoke-IExpress `
            -target (Join-Path $dist "$appId-Setup-v$version$exeSuffix.exe") `
            -stageDir $stage `
            -appIdForTemp "${appId}Setup" `
            -launchLine 'start "" "%DEST%\Setup.exe"'
    } elseif ($portable) {
        # 便携模式：payload.zip 里只装产品文件，向导那几个小文件直接散着塞进自解压包
        $portableLaunch = @(
            'set "PSARGS=%*"',
            'set "PSARGS=%PSARGS:/S=-Silent%"',
            'set "PSARGS=%PSARGS:/REMOVEDATA=-RemoveData%"',
            'set "PSARGS=%PSARGS:/KEEPDATA=-KeepData%"',
            'powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "%~dp0wizard.ps1" -Payload "%DEST%" %PSARGS%'
        ) -join "`r`n"
        $singleExe = Invoke-IExpress `
            -target (Join-Path $dist "$appId-Setup-v$version.exe") `
            -payloadSourceDir (Join-Path $stage "payload") `
            -appIdForTemp "${appId}Payload" `
            -extraFiles @(
                (Join-Path $stage "wizard.ps1"),
                (Join-Path $stage "uninstall.ps1"),
                (Join-Path $stage "installer.config.json")
            ) `
            -launchLine $portableLaunch
    } else {
        $singleExe = Invoke-IExpress `
            -target (Join-Path $dist "$appId-Setup-v$version.exe") `
            -stageDir $stage `
            -appIdForTemp "${appId}Setup" `
            -launchLine 'start "" "%DEST%\Setup.exe"'
    }

    # 外层的签名必须放在最后：IExpress 是重新生成文件，之前签的会作废
    # ⚠️ 这里的 $signInstaller 是「缺省为真」算出来的，不能直接读 $cfg.codeSign.signInstaller ——
    # 手写的 / 老版本的配置里可能根本没有这个字段，$null 会被当成 false 而**静默跳过签名**（踩过）。
    if ($codeSign -and $signInstaller) {
        Invoke-SignFiles $codeSign @($singleExe) "安装包本身"
    }
}

# ---------------------------------------------------------------- 6. 产物信息

Step "6/6 产物"

$files = @()
if ($singleExe) {
    $files += Get-Item $singleExe
} else {
    $files += Get-Item $stage
}

foreach ($f in $files) {
    if ($f.PSIsContainer) { continue }
    $hash = (Get-FileHash $f.FullName -Algorithm SHA256).Hash
    Say ("    {0}" -f $f.FullName)
    Say ("      {0:N1} MB   SHA256 {1}" -f ($f.Length / 1MB), $hash)
}

$release = [ordered]@{
    productName = $productName
    appId       = $appId
    version     = $version
    builtAt     = (Get-Date).ToString("s")
    stageDir    = $stage
    installer   = if ($singleExe) { $singleExe } else { $null }
    sha256      = if ($singleExe) { (Get-FileHash $singleExe -Algorithm SHA256).Hash } else { $null }
}
Write-Utf8NoBom (Join-Path $dist "release.json") ($release | ConvertTo-Json -Depth 5)

# ---------------------------------------------------------------- 验签

if ($codeSign -and $signVerify) {
    $toVerify = @($stageTargets)
    if ($singleExe) { $toVerify += $singleExe }
    Test-SignedArtifacts $codeSign $toVerify
}

Say ""
Say "完成喵~"
Say "  目录版：$stage\Setup.exe"
if ($singleExe) { Say "  单文件：$singleExe" }
Say "  静默安装：Setup.exe /S /DIR=D:\test"
