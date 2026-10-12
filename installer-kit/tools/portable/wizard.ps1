# InstallerKit 便携版安装向导（绿色软件用）
#
# 为什么是 PowerShell 而不是那套 Compose 向导：
#   绿色软件通常只有几百 KB～几十 MB，而自带的 Compose 运行时就有 57MB。
#   为了给一个 0.5MB 的小工具套个漂亮壳子而让安装包膨胀 100 倍，不划算。
#   这里用系统自带的 .NET（WinForms），安装包只比原程序大几十 KB。
#
# 它由打包流水线塞进单文件安装包里，参数：
#   -Payload <目录>   安装包解压出来的产品文件（必需）
#   -Silent           静默安装（不弹界面，用默认值）
#   -Dir <目录>       指定安装目录
#   -NoDesktop / -NoStartMenu / -NoLaunch   覆盖配置里的默认勾选
#
# ⚠️ 本文件必须存成 UTF-8 with BOM（PowerShell 5.1 读无 BOM 的 .ps1 会按 ANSI 解，中文全乱）。
#    改完记得跑 tools\fix-bom.cmd。

[CmdletBinding()]
param(
    [string]$Payload = "",
    [switch]$Silent,
    [string]$Dir = "",
    [switch]$NoDesktop,
    [switch]$NoStartMenu,
    [switch]$NoLaunch
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}

# ------------------------------------------------------------------ 读配置

$here = Split-Path -Parent $MyInvocation.MyCommand.Path

function Read-JsonFile($path) {
    if (-not (Test-Path -LiteralPath $path)) { return $null }
    try {
        $raw = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
        return ($raw | ConvertFrom-Json)
    } catch { return $null }
}

$cfg = Read-JsonFile (Join-Path $here 'installer.config.json')
if (-not $cfg -and $Payload) { $cfg = Read-JsonFile (Join-Path $Payload 'installer.config.json') }
if (-not $cfg) {
    [System.Windows.Forms.MessageBox]::Show("找不到 installer.config.json，安装包可能损坏了。", "安装失败", 'OK', 'Error') | Out-Null
    exit 1
}

$productName = [string]$cfg.productName
$appId       = [string]$cfg.appId
$version     = if ($cfg.version) { [string]$cfg.version } else { '1.0.0' }
$publisher   = if ($cfg.publisher) { [string]$cfg.publisher } else { '' }
$installName = if ($cfg.installDirName) { [string]$cfg.installDirName } else { $appId }
$dataName    = if ($cfg.dataDirName) { [string]$cfg.dataDirName } else { $installName }
$mainExe     = if ($cfg.mainExeName) { [string]$cfg.mainExeName } else { '' }
$homepage    = if ($cfg.urlInfoAbout) { [string]$cfg.urlInfoAbout } else { '' }
$helpLink    = if ($cfg.helpLink) { [string]$cfg.helpLink } else { '' }
$brandHex    = if ($cfg.brandColor) { [string]$cfg.brandColor } else { '#3E6FD9' }
$licenseFile = if ($cfg.licenseFile) { [string]$cfg.licenseFile } else { '' }

# 默认值（配置里没写就按「都勾上」来）
function CfgFlag($value, $default) {
    if ($null -eq $value) { return $default }
    return [bool]$value
}
$wantDesktop = CfgFlag $cfg.defaultDesktopShortcut $true
$wantMenu    = CfgFlag $cfg.defaultStartMenu $true
$wantLaunch  = CfgFlag $cfg.launchAfterInstall $true

$defaultDir = if ($cfg.defaultPerUser -eq $false) {
    Join-Path ${env:ProgramFiles} $installName
} else {
    Join-Path (Join-Path $env:LOCALAPPDATA 'Programs') $installName
}

function Resolve-BrandColor {
    try {
        $hex = $brandHex.TrimStart('#')
        if ($hex.Length -eq 6) {
            return [System.Drawing.Color]::FromArgb(255,
                [Convert]::ToInt32($hex.Substring(0, 2), 16),
                [Convert]::ToInt32($hex.Substring(2, 2), 16),
                [Convert]::ToInt32($hex.Substring(4, 2), 16))
        }
    } catch {}
    return [System.Drawing.Color]::FromArgb(255, 62, 111, 217)
}

# ------------------------------------------------------------------ 安装动作

function Get-PayloadFileCount($dir) {
    return (Get-ChildItem -LiteralPath $dir -Recurse -File -Force -ErrorAction SilentlyContinue | Measure-Object).Count
}

function New-Lnk([string]$linkPath, [string]$target, [string]$workDir, [string]$desc) {
    $ws = New-Object -ComObject WScript.Shell
    $sc = $ws.CreateShortcut($linkPath)
    $sc.TargetPath = $target
    $sc.WorkingDirectory = $workDir
    $sc.Description = $desc
    $sc.IconLocation = "$target,0"
    $sc.Save()
}

function Write-RegistryEntry([string]$targetDir, [long]$sizeKb, [string]$uninstallPs) {
    # 卸载走 PowerShell 脚本，所以 UninstallString 是一条完整命令行（带引号，路径有空格也安全）
    $ps = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
    $uninstallString = "`"$ps`" -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$uninstallPs`""
    $date = Get-Date -Format 'yyyyMMdd'

    $uk = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\$appId"
    New-Item -Path $uk -Force | Out-Null
    Set-ItemProperty -Path $uk -Name DisplayName     -Value $productName
    Set-ItemProperty -Path $uk -Name DisplayVersion  -Value $version
    Set-ItemProperty -Path $uk -Name Publisher       -Value $publisher
    Set-ItemProperty -Path $uk -Name InstallLocation -Value $targetDir
    Set-ItemProperty -Path $uk -Name InstallDate     -Value $date
    Set-ItemProperty -Path $uk -Name UninstallString -Value $uninstallString
    Set-ItemProperty -Path $uk -Name QuietUninstallString -Value "$uninstallString -Silent"
    Set-ItemProperty -Path $uk -Name DisplayIcon     -Value (Join-Path $targetDir $mainExe)
    Set-ItemProperty -Path $uk -Name URLInfoAbout    -Value $homepage
    Set-ItemProperty -Path $uk -Name HelpLink        -Value $helpLink
    Set-ItemProperty -Path $uk -Name NoModify        -Value 1 -Type DWord
    Set-ItemProperty -Path $uk -Name NoRepair        -Value 1 -Type DWord
    Set-ItemProperty -Path $uk -Name Language        -Value 2052 -Type DWord
    Set-ItemProperty -Path $uk -Name EstimatedSize   -Value $sizeKb -Type DWord

    $ak = "HKCU:\Software\$appId"
    New-Item -Path $ak -Force | Out-Null
    Set-ItemProperty -Path $ak -Name InstallPath     -Value $targetDir
    Set-ItemProperty -Path $ak -Name Version         -Value $version
    Set-ItemProperty -Path $ak -Name PerUser         -Value 1
    Set-ItemProperty -Path $ak -Name UninstallString -Value $uninstallString
}

function Copy-Payload([string]$targetDir) {
    New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
    # 把解压出来的产品文件原样铺进去（绿色软件就该保持它原来的目录结构）
    Get-ChildItem -LiteralPath $Payload -Force | ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination $targetDir -Recurse -Force
    }
}

function Install-Product {
    param([string]$TargetDir, [bool]$Desktop, [bool]$Menu, [string]$ProgressText)

    if (-not (Test-Path -LiteralPath $Payload)) { throw "安装包解压目录不见了：$Payload" }

    Copy-Payload $TargetDir

    # 卸载器 + 它要用的信息
    $uninstallPs = Join-Path $TargetDir 'Uninstall.ps1'
    Copy-Item -LiteralPath (Join-Path $here 'uninstall.ps1') -Destination $uninstallPs -Force
    $uninstallInfo = [ordered]@{
        productName = $productName
        appId       = $appId
        version     = $version
        publisher   = $publisher
        mainExe     = $mainExe
        dataDirName = $dataName
        installDir  = $TargetDir
    }
    $json = $uninstallInfo | ConvertTo-Json
    [System.IO.File]::WriteAllText((Join-Path $TargetDir 'uninstall.json'), $json, (New-Object System.Text.UTF8Encoding($false)))

    $appExe = Join-Path $TargetDir $mainExe
    $sizeKb = [long](((Get-ChildItem -LiteralPath $TargetDir -Recurse -File -Force -ErrorAction SilentlyContinue |
        Measure-Object -Property Length -Sum).Sum) / 1KB)

    if ($Desktop) {
        $lnk = Join-Path ([Environment]::GetFolderPath('Desktop')) "$productName.lnk"
        New-Lnk $lnk $appExe $TargetDir $productName
    }
    if ($Menu) {
        $menuDir = Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs'
        New-Item -ItemType Directory -Force -Path $menuDir | Out-Null
        New-Lnk (Join-Path $menuDir "$productName.lnk") $appExe $TargetDir $productName
    }

    Write-RegistryEntry $TargetDir $sizeKb $uninstallPs
}

function Clear-Payload {
    if (-not $Payload) { return }
    # ⚠️ 只有「从单文件包解压到 %TEMP%」的那份才删。
    # 目录版的 payload 就躺在安装包旁边，删掉等于把发行目录毁了
    # —— 踩过一次：拿目录版装了一遍之后，那个目录就变成空壳，再也装不了了。
    try {
        $tempRoot = [System.IO.Path]::GetFullPath($env:TEMP).TrimEnd('\')
        $payloadFull = [System.IO.Path]::GetFullPath($Payload).TrimEnd('\')
        if (-not $payloadFull.StartsWith($tempRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
            return
        }
        if (Test-Path -LiteralPath $Payload) {
            Remove-Item -LiteralPath $Payload -Recurse -Force -ErrorAction SilentlyContinue
        }
    } catch {}
}

# ------------------------------------------------------------------ 静默模式

if ($Silent) {
    $target = if ($Dir) { $Dir } else { $defaultDir }
    try {
        Install-Product -TargetDir $target -Desktop (-not $NoDesktop -and $wantDesktop) `
            -Menu (-not $NoStartMenu -and $wantMenu) -ProgressText ''
        Clear-Payload
        Write-Host "已安装到 $target"
        exit 0
    } catch {
        Write-Host "安装失败：$($_.Exception.Message)"
        exit 1
    }
}

# ------------------------------------------------------------------ 界面模式

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[System.Windows.Forms.Application]::EnableVisualStyles()

# ⚠️ 取色要在 Add-Type 之后 —— 上面那段静默分支根本不会走到这里，
# 而 [System.Drawing.Color] 在没 Add-Type 的进程里是找不到的类型（踩过一次）。
$brand = Resolve-BrandColor

$font = New-Object System.Drawing.Font('Microsoft YaHei UI', 9)
$fontTitle = New-Object System.Drawing.Font('Microsoft YaHei UI', 13, [System.Drawing.FontStyle]::Bold)
$fontSmall = New-Object System.Drawing.Font('Microsoft YaHei UI', 8)

$form = New-Object System.Windows.Forms.Form
$form.Text = "安装 $productName"
$form.Size = New-Object System.Drawing.Size(560, 380)
$form.StartPosition = 'CenterScreen'
$form.FormBorderStyle = 'FixedDialog'
$form.MaximizeBox = $false
$form.MinimizeBox = $false
$form.BackColor = [System.Drawing.Color]::White
$form.Font = $font

# 顶部品牌条
$brandBar = New-Object System.Windows.Forms.Panel
$brandBar.Dock = 'Top'
$brandBar.Height = 6
$brandBar.BackColor = $brand
$form.Controls.Add($brandBar)

$lblTitle = New-Object System.Windows.Forms.Label
$lblTitle.Text = "安装 $productName"
$lblTitle.Font = $fontTitle
$lblTitle.Location = New-Object System.Drawing.Point(24, 26)
$lblTitle.AutoSize = $true
$form.Controls.Add($lblTitle)

$lblSub = New-Object System.Windows.Forms.Label
$lblSub.Text = "版本 $version" + $(if ($publisher) { "　·　$publisher" } else { "" })
$lblSub.Location = New-Object System.Drawing.Point(26, 56)
$lblSub.AutoSize = $true
$lblSub.ForeColor = [System.Drawing.Color]::Gray
$lblSub.Font = $fontSmall
$form.Controls.Add($lblSub)

$lblDir = New-Object System.Windows.Forms.Label
$lblDir.Text = "安装位置"
$lblDir.Location = New-Object System.Drawing.Point(26, 92)
$lblDir.AutoSize = $true
$form.Controls.Add($lblDir)

$txtDir = New-Object System.Windows.Forms.TextBox
$txtDir.Location = New-Object System.Drawing.Point(28, 112)
$txtDir.Size = New-Object System.Drawing.Size(400, 26)
$txtDir.Text = $defaultDir
$form.Controls.Add($txtDir)

$btnBrowse = New-Object System.Windows.Forms.Button
$btnBrowse.Text = "浏览…"
$btnBrowse.Location = New-Object System.Drawing.Point(436, 111)
$btnBrowse.Size = New-Object System.Drawing.Size(86, 27)
$btnBrowse.FlatStyle = 'System'
$btnBrowse.Add_Click({
    $dlg = New-Object System.Windows.Forms.FolderBrowserDialog
    $dlg.Description = "选择安装位置"
    if ($txtDir.Text -and (Test-Path $txtDir.Text)) { $dlg.SelectedPath = $txtDir.Text }
    if ($dlg.ShowDialog() -eq 'OK') { $txtDir.Text = $dlg.SelectedPath }
})
$form.Controls.Add($btnBrowse)

$chkDesktop = New-Object System.Windows.Forms.CheckBox
$chkDesktop.Text = "创建桌面快捷方式"
$chkDesktop.Location = New-Object System.Drawing.Point(28, 152)
$chkDesktop.AutoSize = $true
$chkDesktop.Checked = $wantDesktop
$form.Controls.Add($chkDesktop)

$chkMenu = New-Object System.Windows.Forms.CheckBox
$chkMenu.Text = "创建开始菜单项"
$chkMenu.Location = New-Object System.Drawing.Point(210, 152)
$chkMenu.AutoSize = $true
$chkMenu.Checked = $wantMenu
$form.Controls.Add($chkMenu)

$chkLaunch = New-Object System.Windows.Forms.CheckBox
$chkLaunch.Text = "安装完成后启动 $productName"
$chkLaunch.Location = New-Object System.Drawing.Point(28, 178)
$chkLaunch.AutoSize = $true
$chkLaunch.Checked = $wantLaunch
$form.Controls.Add($chkLaunch)

$lblNote = New-Object System.Windows.Forms.Label
$lblNote.Text = "会一起安装 $(Get-PayloadFileCount $Payload) 个文件"
$lblNote.Location = New-Object System.Drawing.Point(28, 206)
$lblNote.AutoSize = $true
$lblNote.ForeColor = [System.Drawing.Color]::Gray
$lblNote.Font = $fontSmall
$form.Controls.Add($lblNote)

$progress = New-Object System.Windows.Forms.ProgressBar
$progress.Location = New-Object System.Drawing.Point(28, 232)
$progress.Size = New-Object System.Drawing.Size(494, 8)
$progress.Style = 'Continuous'
$form.Controls.Add($progress)

$lblStatus = New-Object System.Windows.Forms.Label
$lblStatus.Text = ""
$lblStatus.Location = New-Object System.Drawing.Point(28, 246)
$lblStatus.AutoSize = $true
$lblStatus.ForeColor = [System.Drawing.Color]::DimGray
$lblStatus.Font = $fontSmall
$form.Controls.Add($lblStatus)

$btnInstall = New-Object System.Windows.Forms.Button
$btnInstall.Text = "安装"
$btnInstall.Location = New-Object System.Drawing.Point(346, 288)
$btnInstall.Size = New-Object System.Drawing.Size(84, 30)
$btnInstall.BackColor = $brand
$btnInstall.ForeColor = [System.Drawing.Color]::White
$btnInstall.FlatStyle = 'Flat'
$btnInstall.FlatAppearance.BorderSize = 0
$form.Controls.Add($btnInstall)

$btnCancel = New-Object System.Windows.Forms.Button
$btnCancel.Text = "取消"
$btnCancel.Location = New-Object System.Drawing.Point(438, 288)
$btnCancel.Size = New-Object System.Drawing.Size(84, 30)
$btnCancel.FlatStyle = 'System'
$form.Controls.Add($btnCancel)

$btnCancel.Add_Click({ $form.Close() })

$script:installed = $false

$btnInstall.Add_Click({
    $target = $txtDir.Text.Trim()
    if (-not $target) {
        [System.Windows.Forms.MessageBox]::Show("请先选一个安装位置。", "提示", 'OK', 'Warning') | Out-Null
        return
    }
    # 确认覆盖
    if (Test-Path -LiteralPath $target) {
        $existing = @(Get-ChildItem -LiteralPath $target -Force -ErrorAction SilentlyContinue)
        if ($existing.Count -gt 0) {
            $r = [System.Windows.Forms.MessageBox]::Show("这个文件夹里已经有东西了：`n$target`n`n继续会把新文件盖进去，确定吗？", "确认", 'YesNo', 'Question')
            if ($r -ne 'Yes') { return }
        }
    }

    $btnInstall.Enabled = $false
    $btnBrowse.Enabled = $false
    $txtDir.Enabled = $false
    $lblStatus.Text = "正在安装…"
    $progress.Value = 15
    [System.Windows.Forms.Application]::DoEvents()

    try {
        Install-Product -TargetDir $target -Desktop $chkDesktop.Checked -Menu $chkMenu.Checked -ProgressText ''
        $progress.Value = 100
        $script:installed = $true
        $lblStatus.Text = "安装完成"
        $lblNote.Text = "已经装到：$target"
        $lblNote.ForeColor = [System.Drawing.Color]::FromArgb(46, 125, 50)
        $lblDir.Visible = $false; $txtDir.Visible = $false; $btnBrowse.Visible = $false
        $chkDesktop.Visible = $false; $chkMenu.Visible = $false
        $btnInstall.Visible = $false
        $btnCancel.Text = "完成"
        $chkLaunch.Location = New-Object System.Drawing.Point(28, 118)
        $chkLaunch.Visible = $true
        Clear-Payload
    } catch {
        $lblStatus.Text = "安装失败"
        $lblStatus.ForeColor = [System.Drawing.Color]::Firebrick
        [System.Windows.Forms.MessageBox]::Show("安装没成功：`n$($_.Exception.Message)", "安装失败", 'OK', 'Error') | Out-Null
        $btnInstall.Enabled = $true
        $btnBrowse.Enabled = $true
        $txtDir.Enabled = $true
    }
})

$btnCancel.Add_Click({
    if ($script:installed) {
        if ($chkLaunch.Checked -and $mainExe) {
            $exe = Join-Path $txtDir.Text.Trim() $mainExe
            if (Test-Path -LiteralPath $exe) {
                Start-Process -FilePath $exe -WorkingDirectory (Split-Path -Parent $exe)
            }
        }
    } else {
        Clear-Payload
    }
    $form.Close()
})

[void]$form.ShowDialog()
$form.Dispose()

if ($script:installed) { exit 0 } else { Clear-Payload; exit 1 }
