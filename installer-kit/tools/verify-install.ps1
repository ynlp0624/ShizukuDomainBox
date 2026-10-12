# InstallerKit 端到端验收
#
# 把 HANDOFF 第 10 节的验收清单跑成脚本：
#   静默安装 → 目录内容 → 注册表 → 快捷方式 → 静默卸载（保留数据）→ 静默卸载（彻底）
#
# 用法:
#   powershell -File tools\verify-install.ps1
#   powershell -File tools\verify-install.ps1 -InstallDir "D:\tmp\KitDemoBoxTest"
#
# ⚠️ 会在 HKCU 下写/删 <appId> 的注册表项，并创建桌面 / 开始菜单快捷方式，
#    跑完会自动清掉。别拿它去测你已经正式装过的产品。
#
# ⚠️ 这个脚本自己必须存成 UTF-8 with BOM（PowerShell 5.1 读无 BOM 的 .ps1 会按 ANSI 解）。

[CmdletBinding()]
param(
    [string]$Stage,
    [string]$InstallDir,
    [string]$Config,
    [int]$DeleteTimeoutSeconds = 75
)

$ErrorActionPreference = "Stop"
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}

$kitRoot = Split-Path -Parent $PSScriptRoot
if (-not $Stage)  { $Stage  = Join-Path $kitRoot "build\stage" }
if (-not $Config) { $Config = Join-Path $kitRoot "installer.config.json" }

function Read-Utf8($path) {
    return [System.IO.File]::ReadAllText($path, (New-Object System.Text.UTF8Encoding($false)))
}
function Say($t) { Write-Host $t }
function Step($t) { Write-Host ""; Write-Host "=== $t ===" -ForegroundColor Cyan }

$passed = 0
$failed = New-Object System.Collections.Generic.List[string]
function Check($name, $condition, $detail = "") {
    if ($condition) {
        $script:passed++
        Write-Host "  [OK]   $name"
    } else {
        $script:failed.Add($name)
        Write-Host "  [FAIL] $name   $detail" -ForegroundColor Red
    }
}
function Wait-ForGone([string]$path, [int]$timeoutSeconds) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    while ((Test-Path $path) -and $sw.Elapsed.TotalSeconds -lt $timeoutSeconds) {
        Start-Sleep -Milliseconds 500
    }
    return (-not (Test-Path $path))
}
function Run-Silent([string]$exe, [string[]]$extraArgs, [int]$TimeoutSeconds = 300) {
    # 别用 $args 这个名字：它是 PowerShell 的自动变量
    $argList = @("/S") + $extraArgs
    $p = Start-Process -FilePath $exe -ArgumentList $argList -PassThru
    # ⚠️ 一定要带超时。用 Start-Process -Wait 的话，只要安装程序卡住，
    # 整个验收脚本就会永远挂在那儿，连「哪一步挂了」都看不出来（踩过）。
    if (-not $p.WaitForExit($TimeoutSeconds * 1000)) {
        Write-Host "  [!!] $([System.IO.Path]::GetFileName($exe)) 超过 ${TimeoutSeconds}s 没退出，强制结束" -ForegroundColor Yellow
        try { $p.Kill() } catch {}
        return -999
    }
    return $p.ExitCode
}

function Stop-SetupProcesses([int]$TimeoutSeconds = 20) {
    # 等向导窗口真的起来再打；打空的话它会一直留在那儿
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while (-not (Get-Process -Name Setup -ErrorAction SilentlyContinue) -and (Get-Date) -lt $deadline) {
        Start-Sleep -Milliseconds 300
    }
    cmd /c "taskkill /IM Setup.exe /F >nul 2>nul"
    Start-Sleep -Milliseconds 800
}

# 注册表一律走 cmd /c：PowerShell 5.1 会把原生程序的 stderr 变成 ErrorRecord，
# 配合 $ErrorActionPreference = "Stop" 直接中断脚本 —— 「键不存在」这种正常情况也会炸。
function Get-RegQuery([string]$key) {
    $out = cmd /c "reg query `"$key`" 2>nul"
    if ($null -eq $out) { return "" }
    return ($out -join "`n")
}
function Test-RegKey([string]$key) {
    cmd /c "reg query `"$key`" >nul 2>nul"
    return ($LASTEXITCODE -eq 0)
}
function Remove-RegKey([string]$key) {
    cmd /c "reg delete `"$key`" /f >nul 2>nul"
}

if (-not (Test-Path $Config)) { throw "找不到配置文件：$Config" }
$cfg = (Read-Utf8 $Config) | ConvertFrom-Json

$productName  = $cfg.productName
$appId        = $cfg.appId
$mainExeName  = if ($cfg.mainExeName) { $cfg.mainExeName } else { "$appId.exe" }
$dataDirName  = if ($cfg.dataDirName) { $cfg.dataDirName } else { $appId }
$dataDir      = Join-Path $env:APPDATA $dataDirName
# 默认测试目录**故意带空格**：Windows 上一大半注册表 / 快捷方式 / 批处理的坑
# 只在路径有空格时才暴露（UninstallString 的引号就是典型）。
if (-not $InstallDir) { $InstallDir = Join-Path $env:LOCALAPPDATA "Programs\$appId Test" }

$setupExe     = Join-Path $Stage "Setup.exe"
$uninstallExe = Join-Path $InstallDir "Uninstall.exe"
$regKey       = "HKCU\Software\Microsoft\Windows\CurrentVersion\Uninstall\$appId"
$appRegKey    = "HKCU\Software\$appId"
$desktopLnk   = Join-Path $env:USERPROFILE "Desktop\$productName.lnk"
$menuLnk      = Join-Path $env:APPDATA "Microsoft\Windows\Start Menu\Programs\$productName.lnk"

Say "产品      : $productName ($appId)"
Say "发行目录  : $Stage"
Say "测试安装到: $InstallDir"
Say "数据目录  : $dataDir"

if (-not (Test-Path $setupExe)) { throw "找不到 Setup.exe：$setupExe（先跑 tools\build-installer.ps1 -SkipIExpress）" }

# ---------------------------------------------------------------- 0. 前置清理

Step "0. 前置清理（保证从干净状态开始）"
if (Test-Path $InstallDir) { Remove-Item $InstallDir -Recurse -Force }
if (Test-Path $dataDir) { Remove-Item $dataDir -Recurse -Force }
if (Test-Path $desktopLnk) { Remove-Item $desktopLnk -Force }
if (Test-Path $menuLnk) { Remove-Item $menuLnk -Force }
Remove-RegKey $regKey
Remove-RegKey $appRegKey
Say "  干净了"

# ---------------------------------------------------------------- 0.5 数字签名

# 只看 PowerShell 自带的 Get-AuthenticodeSignature，不依赖签名工具本身
function Get-SigStatus([string]$file) {
    if (-not (Test-Path -LiteralPath $file)) { return 'Missing' }
    return (Get-AuthenticodeSignature -LiteralPath $file).Status.ToString()
}

$codeSignEnabled = [bool]($cfg.codeSign -and $cfg.codeSign.enabled)

Step "0.5 打包产物的数字签名"
if (-not $codeSignEnabled) {
    Say "  codeSign.enabled = false，跳过"
} else {
    foreach ($e in @(Get-ChildItem (Join-Path $Stage "*.exe") -File -ErrorAction SilentlyContinue)) {
        $st = Get-SigStatus $e.FullName
        Check "发行目录里的 $($e.Name) 已签名且有效" ($st -eq 'Valid') "实际 $st"
    }
    $builtInstaller = Join-Path $kitRoot "dist\$appId-Setup-v$($cfg.version).exe"
    if (Test-Path $builtInstaller) {
        $st = Get-SigStatus $builtInstaller
        Check "单文件安装包已签名且有效" ($st -eq 'Valid') "实际 $st"
    } else {
        Say "  dist 里没有单文件安装包，跳过它的签名检查"
    }
}

# ---------------------------------------------------------------- 1. 静默安装

Step "1. 静默安装 Setup.exe /S /DIR=..."
$code = Run-Silent $setupExe @("/DIR=`"$InstallDir`"")
Check "静默安装退出码为 0" ($code -eq 0) "实际 $code"

# ---------------------------------------------------------------- 2. 目录内容

Step "2. 安装目录内容"
Check "主程序 $mainExeName 在" (Test-Path (Join-Path $InstallDir $mainExeName))
Check "Uninstall.exe 在" (Test-Path $uninstallExe)
Check "app\Uninstall.cfg 在" (Test-Path (Join-Path $InstallDir "app\Uninstall.cfg"))
Check "app\installer.config.json 在" (Test-Path (Join-Path $InstallDir "app\installer.config.json"))
Check "runtime\ 一起复制过来了" (Test-Path (Join-Path $InstallDir "runtime"))
Check "打包期的 Setup.cfg 没被装进来" (-not (Test-Path (Join-Path $InstallDir "app\Setup.cfg")))
Check "打包期的 Setup.exe 没被装进来" (-not (Test-Path (Join-Path $InstallDir "Setup.exe")))

if ($codeSignEnabled) {
    # Uninstall.exe 是主程序 exe 的副本 —— 签名应该跟着一起过来，不用单独签
    $stMain = Get-SigStatus (Join-Path $InstallDir $mainExeName)
    Check "装出来的 $mainExeName 带有效签名" ($stMain -eq 'Valid') "实际 $stMain"
    $stUninst = Get-SigStatus $uninstallExe
    Check "装出来的 Uninstall.exe 继承了签名（副本也有效）" ($stUninst -eq 'Valid') "实际 $stUninst"
}

if (Test-Path (Join-Path $InstallDir "app\Uninstall.cfg")) {
    $ucfg = Read-Utf8 (Join-Path $InstallDir "app\Uninstall.cfg")
    Check "Uninstall.cfg 里有 arguments=--uninstall" ($ucfg -match "(?m)^arguments=--uninstall\s*$")
    Check "Uninstall.cfg 把 mainclass 换成了 kit 向导" ($ucfg -match "(?m)^app\.mainclass=io\.nekoshizuku\.kit\.app\.MainKt\s*$") 
    Check "Uninstall.cfg 保留了产品原有的 classpath" ($ucfg -match [regex]::Escape($mainExeName.Replace(".exe", "")) -or $ucfg -match "app\.classpath=")
    Check "Uninstall.cfg 里补进了 kit 的 jar" ($ucfg -match "kit-installer")
}

# ---------------------------------------------------------------- 3. 注册表

Step "3. 「程序和功能」注册表项"
$regOut = Get-RegQuery $regKey
if (-not $regOut) { $regOut = "" }
$expected = @(
    "DisplayName", "DisplayVersion", "Publisher", "InstallLocation", "InstallDate",
    "UninstallString", "QuietUninstallString", "DisplayIcon", "URLInfoAbout", "HelpLink",
    "NoModify", "NoRepair", "Language", "EstimatedSize"
)
foreach ($v in $expected) {
    Check "注册表有 $v" ($regOut -match "\b$v\b")
}
Check "DisplayName 是中文产品名" ($regOut -match [regex]::Escape($productName))
Check "InstallLocation 指向测试目录" ($regOut -match [regex]::Escape($InstallDir))
Check "QuietUninstallString 带 /S /KEEPDATA" ($regOut -match "/S /KEEPDATA")
# 路径里有空格时 UninstallString **必须**带引号，否则「程序和功能」里点卸载会失败
if ($InstallDir -match " ") {
    Check "路径含空格时 UninstallString 带引号" ($regOut -match 'UninstallString\s+REG_SZ\s+"')
    Check "UninstallString 的值是带引号的完整路径" ($regOut -match [regex]::Escape("""$InstallDir\Uninstall.exe"""))
} else {
    Check "UninstallString 指向测试目录" ($regOut -match [regex]::Escape("$InstallDir\Uninstall.exe"))
}

$appRegOut = Get-RegQuery $appRegKey
Check "HKCU\Software\$appId 下有 InstallPath" ($appRegOut -match "InstallPath")
Check "HKCU\Software\$appId 下有 Version" ($appRegOut -match "Version")
Check "HKCU\Software\$appId 下有 PerUser" ($appRegOut -match "PerUser")

# ---------------------------------------------------------------- 4. 快捷方式

Step "4. 快捷方式"
Check "桌面快捷方式在" (Test-Path $desktopLnk)
Check "开始菜单项在" (Test-Path $menuLnk)
if (Test-Path $desktopLnk) {
    $ws = New-Object -ComObject WScript.Shell
    $sc = $ws.CreateShortcut($desktopLnk)
    Check "桌面快捷方式指向主程序" ($sc.TargetPath -ieq (Join-Path $InstallDir $mainExeName)) "实际 $($sc.TargetPath)"
    Check "桌面快捷方式工作目录是安装目录" ($sc.WorkingDirectory -ieq $InstallDir) "实际 $($sc.WorkingDirectory)"
    Check "桌面快捷方式描述是产品名" ($sc.Description -eq $productName) "实际 $($sc.Description)"
}

# ---------------------------------------------------------------- 5. 窗口图标素材

Step "5. 窗口图标素材（图标起不来就会是 Java 咖啡杯）"
$iconHit = $null
$direct = Get-ChildItem -Path $InstallDir -Recurse -Filter "app-icon-64.png" -ErrorAction SilentlyContinue | Select-Object -First 1
if ($direct) {
    $iconHit = $direct.FullName
} else {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    foreach ($jar in (Get-ChildItem -Path (Join-Path $InstallDir "app") -Filter *.jar -ErrorAction SilentlyContinue)) {
        try {
            $zip = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
            $hit = $zip.Entries | Where-Object { $_.FullName -eq "app-icon-64.png" } | Select-Object -First 1
            $zip.Dispose()
            if ($hit) { $iconHit = "$($jar.Name)!app-icon-64.png"; break }
        } catch {}
    }
}
Check "多尺寸窗口图标素材装进去了" ($null -ne $iconHit) "app-icon-64.png 没找到"
if ($iconHit) { Say "        找到：$iconHit" }

# ---------------------------------------------------------------- 6. 卸载（保留数据）

Step "6. Uninstall.exe /S /KEEPDATA（保留数据）"
New-Item -ItemType Directory -Force -Path $dataDir | Out-Null
$marker = Join-Path $dataDir "verify-marker.txt"
Set-Content -Path $marker -Value "InstallerKit 验收标记" -Encoding UTF8
Say "  先造一个数据标记：$marker"

$code = Run-Silent $uninstallExe @("/KEEPDATA")
Check "静默卸载退出码为 0" ($code -eq 0) "实际 $code"

$gone = Wait-ForGone $InstallDir $DeleteTimeoutSeconds
Check "安装目录最终完全消失（等 ${DeleteTimeoutSeconds}s 内）" $gone
Check "数据目录按 /KEEPDATA 保留下来了" (Test-Path $dataDir)
Check "数据标记文件还在" (Test-Path $marker)
$regOut2 = Get-RegQuery $regKey
Check "注册表卸载项已删除" ((-not (Test-RegKey $regKey)) -and [string]::IsNullOrWhiteSpace($regOut2))
Check "桌面快捷方式已删除" (-not (Test-Path $desktopLnk))
Check "开始菜单项已删除" (-not (Test-Path $menuLnk))

# ---------------------------------------------------------------- 7. 再装一次，彻底卸载

Step "7. 重新安装 → Uninstall.exe /S（连数据一起删）"
$code = Run-Silent $setupExe @("/DIR=`"$InstallDir`"")
Check "第二次静默安装退出码为 0" ($code -eq 0) "实际 $code"
Check "第二次也确实装上了" (Test-Path $uninstallExe)

$code = Run-Silent $uninstallExe @()
Check "彻底卸载退出码为 0" ($code -eq 0) "实际 $code"
$gone = Wait-ForGone $InstallDir $DeleteTimeoutSeconds
Check "安装目录最终完全消失" $gone
Check "数据目录也一起删掉了" (-not (Test-Path $dataDir))

# ---------------------------------------------------------------- 8. 单文件安装包（可选）

Step "8. 单文件安装包（dist 里有就测）"
$singleExe = Join-Path $kitRoot "dist\$appId-Setup-v$($cfg.version).exe"
if (-not (Test-Path $singleExe)) {
    Say "  dist 里没有 $([System.IO.Path]::GetFileName($singleExe))，跳过（跑 tools\build.cmd 生成）"
} else {
    $sfxDir = Join-Path $env:TEMP "${appId}Setup"
    # 先把上次跑剩下的向导 / 解压目录清掉：残留的 {appId}Setup 会让 install.cmd 里的
    # rmdir / mkdir 撞车，症状是「解压出来半套文件」，非常难查。
    Stop-SetupProcesses 5
    if (Test-Path $sfxDir) { Remove-Item $sfxDir -Recurse -Force -ErrorAction SilentlyContinue }
    Check "单文件测试前，%TEMP% 里的旧解压目录已清空" (-not (Test-Path $sfxDir))

    Say ("  运行 {0}（{1:N1} MB），等它自解压…" -f [System.IO.Path]::GetFileName($singleExe), ((Get-Item $singleExe).Length / 1MB))
    Start-Process -FilePath $singleExe | Out-Null

    $extracted = Join-Path $sfxDir "Setup.exe"
    # ⚠️ 别只等 Setup.exe：tar 是边解边写的，Setup.exe 可能比整个 app\ 子树先落地，
    # 这时候去查 app\ 里的文件会误判成「没解压出来」。等**整套**都到齐。
    $need = @(
        $extracted,
        (Join-Path $sfxDir "app\Setup.cfg"),
        (Join-Path $sfxDir "app\installer.config.json"),
        (Join-Path $sfxDir "runtime")
    )
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt 180) {
        $missing = @($need | Where-Object { -not (Test-Path $_) })
        if ($missing.Count -eq 0) { break }
        Start-Sleep -Milliseconds 500
    }
    Check "自解压到 %TEMP%\$appId`Setup" (Test-Path $extracted)

    if (Test-Path $extracted) {
        $appFileCount = 0
        if (Test-Path (Join-Path $sfxDir "app")) {
            $appFileCount = (Get-ChildItem (Join-Path $sfxDir "app") -File -ErrorAction SilentlyContinue | Measure-Object).Count
        }
        Check "解压出来的 app\ 里文件数够（>=40）" ($appFileCount -ge 40) "实际 $appFileCount"
        Check "解压出来的树里有 app\Setup.cfg" (Test-Path (Join-Path $sfxDir "app\Setup.cfg"))
        Check "解压出来的树里有 app\installer.config.json" (Test-Path (Join-Path $sfxDir "app\installer.config.json"))
        Check "解压出来的树里有 runtime\" (Test-Path (Join-Path $sfxDir "runtime"))

        # 向导窗口已经弹出来了，先关掉它，再用解压出来的 Setup.exe 走一遍静默安装
        Stop-SetupProcesses 30

        if ($codeSignEnabled) {
            # 这一步就是「解压出来的安装包也有签名」的正面证明
            $stSfx = Get-SigStatus $extracted
            Check "自解压出来的 Setup.exe 带有效签名" ($stSfx -eq 'Valid') "实际 $stSfx"
            $stMainSfx = Get-SigStatus (Join-Path $sfxDir $mainExeName)
            Check "自解压出来的 $mainExeName 带有效签名" ($stMainSfx -eq 'Valid') "实际 $stMainSfx"
        }

        $sfxInstallDir = "$InstallDir SFX"
        $code = Run-Silent $extracted @("/DIR=`"$sfxInstallDir`"")
        Check "单文件解压出来的 Setup.exe 静默安装退出码为 0" ($code -eq 0) "实际 $code"
        Check "单文件路径也装出了 Uninstall.exe" (Test-Path (Join-Path $sfxInstallDir "Uninstall.exe"))

        $code = Run-Silent (Join-Path $sfxInstallDir "Uninstall.exe") @()
        Check "单文件装出来的卸载器退出码为 0" ($code -eq 0) "实际 $code"
        Check "单文件装出来的目录也被删干净了" (Wait-ForGone $sfxInstallDir $DeleteTimeoutSeconds)

        if (Test-Path $sfxInstallDir) { Remove-Item $sfxInstallDir -Recurse -Force -ErrorAction SilentlyContinue }
        if (Test-Path $sfxDir) { Remove-Item $sfxDir -Recurse -Force -ErrorAction SilentlyContinue }
    }
}

# ---------------------------------------------------------------- 9. 收尾

Step "9. 收尾"
if (Test-Path $InstallDir) { Remove-Item $InstallDir -Recurse -Force -ErrorAction SilentlyContinue }
if (Test-Path $dataDir)    { Remove-Item $dataDir -Recurse -Force -ErrorAction SilentlyContinue }
Remove-RegKey $regKey
Remove-RegKey $appRegKey
if (Test-Path $desktopLnk) { Remove-Item $desktopLnk -Force }
if (Test-Path $menuLnk) { Remove-Item $menuLnk -Force }
Say "  测试残留清掉了"

Say ""
if ($failed.Count -eq 0) {
    Write-Host "=== 验收全部通过：$passed 项 ===" -ForegroundColor Green
    exit 0
} else {
    Write-Host "=== 失败 $($failed.Count) 项 / 共 $($passed + $failed.Count) 项 ===" -ForegroundColor Red
    foreach ($f in $failed) { Write-Host "   - $f" }
    exit 1
}
