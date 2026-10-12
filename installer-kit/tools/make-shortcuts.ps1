# 给打包器做桌面快捷方式（简单版 + 专业版两个入口）
#
# 用法：
#   powershell -ExecutionPolicy Bypass -File tools\make-shortcuts.ps1
#   powershell -ExecutionPolicy Bypass -File tools\make-shortcuts.ps1 -Remove
#
# ⚠️ 这个脚本自己必须存成 UTF-8 with BOM（PowerShell 5.1 读无 BOM 的 .ps1 会按 ANSI 解，
#    中文快捷方式名会变乱码）。改完它记得跑一次 tools\fix-bom.cmd。

[CmdletBinding()]
param(
    # 打包器装在哪（默认 = 本工具自己装的默认位置）
    [string]$InstallDir = "$env:LOCALAPPDATA\Programs\InstallerKitPacker",
    # InstallerKit 工程目录（会记进打包器的设置里，免得它自己找不到）
    # ⚠️ 默认值不能写 $PSScriptRoot —— param 绑定阶段它还是空的，Split-Path 会直接报错
    [string]$KitRoot,
    [switch]$Remove
)

if (-not $KitRoot) { $KitRoot = Split-Path -Parent $PSScriptRoot }

$ErrorActionPreference = "Stop"
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}

function Write-Utf8NoBom($path, $text) {
    $enc = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($path, $text, $enc)
}

$exe = Join-Path $InstallDir "InstallerKitPacker.exe"
$desktop = [Environment]::GetFolderPath("Desktop")
$startMenu = Join-Path $env:APPDATA "Microsoft\Windows\Start Menu\Programs\InstallerKit"
$settingsDir = Join-Path $env:APPDATA "InstallerKitPacker"

# 两个入口：名字 → 启动参数
$entries = @(
    @{ Name = "InstallerKit 打包器";       Args = "";       Desc = "打包你的程序（简单版：选程序 → 填名字 → 打包）" },
    @{ Name = "InstallerKit 打包器（专业版）"; Args = "--pro";   Desc = "打包你的程序（专业版：所有选项都能改）" }
)

$shell = New-Object -ComObject WScript.Shell

if ($Remove) {
    foreach ($e in $entries) {
        foreach ($dir in @($desktop, $startMenu)) {
            $lnk = Join-Path $dir "$($e.Name).lnk"
            if (Test-Path -LiteralPath $lnk) { Remove-Item -LiteralPath $lnk -Force; Write-Host "已删除 $lnk" }
        }
    }
    Write-Host "快捷方式清完了。"
    exit 0
}

if (-not (Test-Path -LiteralPath $exe)) {
    Write-Warning "没找到打包器：$exe"
    Write-Warning "先用本工具把它装起来，例如："
    Write-Warning "  tools\build.cmd -Config packer.config.json"
    Write-Warning "  然后静默安装 dist\InstallerKitPacker-Setup-v1.0.0.exe /S"
    exit 2
}

# 顺手把「工程目录」记进打包器的设置里 —— 从快捷方式启动时工作目录不固定，
# 它需要知道去哪儿找 tools\build-installer.ps1
New-Item -ItemType Directory -Force -Path $settingsDir | Out-Null
$settingsPath = Join-Path $settingsDir "settings.json"
$settings = [ordered]@{
    kitRoot    = $KitRoot
    configPath = Join-Path $KitRoot "installer.config.json"
    appImageDir = ""
}
Write-Utf8NoBom $settingsPath ($settings | ConvertTo-Json)
Write-Host "打包器设置：$settingsPath"
Write-Host "  工程目录 = $KitRoot"

New-Item -ItemType Directory -Force -Path $startMenu | Out-Null

foreach ($e in $entries) {
    foreach ($dir in @($desktop, $startMenu)) {
        $lnk = Join-Path $dir "$($e.Name).lnk"
        $s = $shell.CreateShortcut($lnk)
        $s.TargetPath = $exe
        if ($e.Args) { $s.Arguments = $e.Args }
        $s.WorkingDirectory = $InstallDir
        $s.IconLocation = "$exe,0"
        $s.Description = $e.Desc
        $s.Save()
        Write-Host "已创建 $lnk"
    }
}

Write-Host ""
Write-Host "搞定 —— 桌面上应该能看到「InstallerKit 打包器」和「InstallerKit 打包器（专业版）」，双击就能开。"
