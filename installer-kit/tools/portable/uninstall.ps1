# InstallerKit 便携版卸载器（安装时被放进安装目录）
#
# 由注册表的 UninstallString 调起：
#   powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "<安装目录>\Uninstall.ps1"
# 参数：
#   -Silent      静默卸载，默认**保留数据**
#   -RemoveData  连数据一起删
#   -KeepData    明确保留数据
#
# ⚠️ 必须 UTF-8 with BOM。改完跑 tools\fix-bom.cmd。

[CmdletBinding()]
param(
    [switch]$Silent,
    [switch]$RemoveData,
    [switch]$KeepData
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}

$installDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$infoPath = Join-Path $installDir 'uninstall.json'

if (-not (Test-Path -LiteralPath $infoPath)) {
    [System.Windows.Forms.MessageBox]::Show("找不到 uninstall.json，没法知道要卸什么。`n可以直接删掉这个文件夹：`n$installDir", "卸载失败", 'OK', 'Error') | Out-Null
    exit 1
}

$info = [System.IO.File]::ReadAllText($infoPath, [System.Text.Encoding]::UTF8) | ConvertFrom-Json
$productName = [string]$info.productName
$appId       = [string]$info.appId
$mainExe     = [string]$info.mainExe
$dataName    = if ($info.dataDirName) { [string]$info.dataDirName } else { $appId }

$dataDir = Join-Path $env:APPDATA $dataName

# ------------------------------------------------------------------ 动作

function Test-AppRunning {
    if (-not $mainExe) { return $false }
    $base = [System.IO.Path]::GetFileNameWithoutExtension($mainExe)
    return @(Get-Process -Name $base -ErrorAction SilentlyContinue).Count -gt 0
}

function Stop-App {
    if (-not $mainExe) { return }
    $base = [System.IO.Path]::GetFileNameWithoutExtension($mainExe)
    Get-Process -Name $base -ErrorAction SilentlyContinue | ForEach-Object {
        try { $_.Kill(); $_.WaitForExit(5000) } catch {}
    }
}

function Remove-Shortcuts {
    $desktop = Join-Path ([Environment]::GetFolderPath('Desktop')) "$productName.lnk"
    $menu = Join-Path $env:APPDATA "Microsoft\Windows\Start Menu\Programs\$productName.lnk"
    foreach ($p in @($desktop, $menu)) {
        if (Test-Path -LiteralPath $p) { Remove-Item -LiteralPath $p -Force -ErrorAction SilentlyContinue }
    }
}

function Write-Shortcuts {
    if (-not $mainExe) { return }
    $exe = Join-Path $installDir $mainExe
    if (-not (Test-Path -LiteralPath $exe)) { return }
    $ws = New-Object -ComObject WScript.Shell
    foreach ($p in @(
            (Join-Path ([Environment]::GetFolderPath('Desktop')) "$productName.lnk"),
            (Join-Path $env:APPDATA "Microsoft\Windows\Start Menu\Programs\$productName.lnk")
        )) {
        $sc = $ws.CreateShortcut($p)
        $sc.TargetPath = $exe
        $sc.WorkingDirectory = $installDir
        $sc.Description = $productName
        $sc.IconLocation = "$exe,0"
        $sc.Save()
    }
}

function Remove-Registry {
    foreach ($k in @(
            "HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\$appId",
            "HKCU:\Software\$appId"
        )) {
        if (Test-Path $k) { Remove-Item -Path $k -Recurse -Force -ErrorAction SilentlyContinue }
    }
}

# 自己就在要被删的目录里 —— 只能交给一个第三方 cmd 延时来删
function Remove-SelfLater([bool]$alsoData) {
    $bat = Join-Path $env:TEMP ("kit_uninstall_$appId.bat")
    $lines = @(
        '@echo off',
        'ping -n 3 127.0.0.1 >nul',
        ('rd /s /q "{0}"' -f $installDir)
    )
    if ($alsoData) { $lines += ('rd /s /q "{0}"' -f $dataDir) }
    $lines += 'del "%~f0"'
    # .bat 必须是纯 ASCII，所以路径里的非 ASCII 全靠 CHCP 65001 + 这里用 UTF8NoBom 写
    $enc = New-Object System.Text.UTF8Encoding($false)
    $header = "@chcp 65001 >nul`r`n"
    [System.IO.File]::WriteAllText($bat, $header + ($lines -join "`r`n") + "`r`n", $enc)

    Start-Process -FilePath "$env:SystemRoot\System32\cmd.exe" -ArgumentList '/c', "`"$bat`"" -WindowStyle Hidden
}

function Do-Uninstall([bool]$removeData) {
    if (Test-AppRunning) { Stop-App; Start-Sleep -Milliseconds 600 }
    Remove-Shortcuts
    Remove-Registry
    Remove-SelfLater $removeData
}

# ------------------------------------------------------------------ 静默模式

if ($Silent) {
    $removeData = $RemoveData -and (-not $KeepData)
    try {
        Do-Uninstall $removeData
        Write-Host "已卸载 $productName$(if (-not $removeData) { '（数据保留）' })"
        exit 0
    } catch {
        Write-Host "卸载失败：$($_.Exception.Message)"
        exit 1
    }
}

# ------------------------------------------------------------------ 界面模式

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[System.Windows.Forms.Application]::EnableVisualStyles()

$font = New-Object System.Drawing.Font('Microsoft YaHei UI', 9)
$fontTitle = New-Object System.Drawing.Font('Microsoft YaHei UI', 13, [System.Drawing.FontStyle]::Bold)
$fontSmall = New-Object System.Drawing.Font('Microsoft YaHei UI', 8)

$form = New-Object System.Windows.Forms.Form
$form.Text = "卸载 $productName"
$form.Size = New-Object System.Drawing.Size(540, 340)
$form.StartPosition = 'CenterScreen'
$form.FormBorderStyle = 'FixedDialog'
$form.MaximizeBox = $false
$form.MinimizeBox = $false
$form.BackColor = [System.Drawing.Color]::White
$form.Font = $font

$lblTitle = New-Object System.Windows.Forms.Label
$lblTitle.Text = "卸载 $productName"
$lblTitle.Font = $fontTitle
$lblTitle.Location = New-Object System.Drawing.Point(24, 24)
$lblTitle.AutoSize = $true
$form.Controls.Add($lblTitle)

$lblWhere = New-Object System.Windows.Forms.Label
$lblWhere.Text = $installDir
$lblWhere.Location = New-Object System.Drawing.Point(26, 54)
$lblWhere.AutoSize = $true
$lblWhere.ForeColor = [System.Drawing.Color]::Gray
$lblWhere.Font = $fontSmall
$form.Controls.Add($lblWhere)

$radioKeep = New-Object System.Windows.Forms.RadioButton
$radioKeep.Text = "只删除程序，保留我的数据"
$radioKeep.Location = New-Object System.Drawing.Point(28, 88)
$radioKeep.AutoSize = $true
$radioKeep.Checked = $true
$form.Controls.Add($radioKeep)

$lblKeep = New-Object System.Windows.Forms.Label
$lblKeep.Text = "　数据目录会留着：$dataDir"
$lblKeep.Location = New-Object System.Drawing.Point(46, 110)
$lblKeep.AutoSize = $true
$lblKeep.ForeColor = [System.Drawing.Color]::Gray
$lblKeep.Font = $fontSmall
$form.Controls.Add($lblKeep)

$radioAll = New-Object System.Windows.Forms.RadioButton
$radioAll.Text = "彻底删除（连数据一起）"
$radioAll.Location = New-Object System.Drawing.Point(28, 138)
$radioAll.AutoSize = $true
$form.Controls.Add($radioAll)

$kill = Test-AppRunning
$chkKill = New-Object System.Windows.Forms.CheckBox
$chkKill.Text = "$productName 正在运行，先帮我关掉它"
$chkKill.Location = New-Object System.Drawing.Point(28, 168)
$chkKill.AutoSize = $true
$chkKill.Checked = $true
$chkKill.Visible = $kill
$form.Controls.Add($chkKill)

$lblStatus = New-Object System.Windows.Forms.Label
$lblStatus.Text = ""
$lblStatus.Location = New-Object System.Drawing.Point(28, 200)
$lblStatus.AutoSize = $true
$lblStatus.ForeColor = [System.Drawing.Color]::DimGray
$lblStatus.Font = $fontSmall
$form.Controls.Add($lblStatus)

$btnOk = New-Object System.Windows.Forms.Button
$btnOk.Text = "卸载"
$btnOk.Location = New-Object System.Drawing.Point(326, 248)
$btnOk.Size = New-Object System.Drawing.Size(84, 30)
$btnOk.BackColor = [System.Drawing.Color]::FromArgb(197, 58, 58)
$btnOk.ForeColor = [System.Drawing.Color]::White
$btnOk.FlatStyle = 'Flat'
$btnOk.FlatAppearance.BorderSize = 0
$form.Controls.Add($btnOk)

$btnCancel = New-Object System.Windows.Forms.Button
$btnCancel.Text = "取消"
$btnCancel.Location = New-Object System.Drawing.Point(418, 248)
$btnCancel.Size = New-Object System.Drawing.Size(84, 30)
$btnCancel.FlatStyle = 'System'
$form.Controls.Add($btnCancel)

$btnCancel.Add_Click({ $form.Close() })

$btnOk.Add_Click({
    if ($chkKill.Visible -and (-not $chkKill.Checked) -and (Test-AppRunning)) {
        [System.Windows.Forms.MessageBox]::Show("它还开着，先关掉再卸载吧。", "提示", 'OK', 'Warning') | Out-Null
        return
    }
    $btnOk.Enabled = $false
    $lblStatus.Text = "正在卸载…"
    [System.Windows.Forms.Application]::DoEvents()
    try {
        Do-Uninstall $radioAll.Checked
        $lblStatus.Text = "卸载完成，这扇窗马上会自己关掉。"
        Start-Sleep -Milliseconds 700
        $form.Close()
    } catch {
        $lblStatus.Text = "卸载失败：$($_.Exception.Message)"
        $btnOk.Enabled = $true
    }
})

[void]$form.ShowDialog()
$form.Dispose()
exit 0
