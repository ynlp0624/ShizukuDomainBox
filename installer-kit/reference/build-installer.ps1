# 构建「雫的域名盒」PC 版安装包
#   1) 生成主程序 app-image（自带运行时）
#   2) 复制成发行目录，并在里面生成 Setup.exe / Setup.cfg / Uninstall.cfg
#   3) 用 IExpress 封成单个 Setup.exe（可选）
#
# 用法: powershell -File D:\dnshe-pc\tools\build-installer.ps1 [-SkipIExpress]

param(
    [switch]$SkipIExpress
)

$ErrorActionPreference = "Stop"
$env:JAVA_HOME = "D:\Android\jdk-17"
$gradle = "D:\Android\gradle-8.7\bin\gradle.bat"
$appImage = "D:\dnshe-pc\desktop\build\compose\binaries\main\app\ShizukuDomainBox"
$stage = "D:\dnshe-pc\build\setup"
$dist = "D:\dnshe-pc\dist"

function Write-Utf8NoBom($path, $text) {
    $enc = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($path, $text, $enc)
}

Write-Host "=== 1/3 构建主程序 app-image ==="
& $gradle -p D:\dnshe-pc :desktop:createDistributable --console=plain
if (-not (Test-Path $appImage)) { throw "app-image 未生成: $appImage" }

Write-Host "=== 2/3 生成发行目录 ==="
if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
New-Item -ItemType Directory -Force -Path (Split-Path $stage) | Out-Null
Copy-Item $appImage $stage -Recurse

# Setup.exe：主程序 exe 的副本，靠文件名触发 --install 模式
Copy-Item "$stage\ShizukuDomainBox.exe" "$stage\Setup.exe" -Force

# Setup.cfg / Uninstall.cfg：同名的配置文件才会被 jpackage 启动器读到
$baseCfg = Get-Content "$stage\app\ShizukuDomainBox.cfg" -Raw -Encoding UTF8
Write-Utf8NoBom "$stage\app\Setup.cfg" ($baseCfg.TrimEnd() + "`r`n`r`n[ArgOptions]`r`narguments=--install`r`n")
Write-Utf8NoBom "$stage\app\Uninstall.cfg" ($baseCfg.TrimEnd() + "`r`n`r`n[ArgOptions]`r`narguments=--uninstall`r`n")

$size = (Get-ChildItem $stage -Recurse -File | Measure-Object -Property Length -Sum).Sum
Write-Host ("    发行目录: {0}  ({1:N1} MB)" -f $stage, ($size / 1MB))
Get-ChildItem $stage | ForEach-Object { Write-Host "      $($_.Name)" }

New-Item -ItemType Directory -Force -Path $dist | Out-Null

if ($SkipIExpress) {
    Write-Host "=== 跳过 IExpress，只输出目录 ==="
    exit 0
}

Write-Host "=== 3/3 用 IExpress 封成单个 Setup.exe ==="
$sfxStaging = "D:\dnshe-pc\build\sfx"
if (Test-Path $sfxStaging) { Remove-Item $sfxStaging -Recurse -Force }
New-Item -ItemType Directory -Force -Path $sfxStaging | Out-Null

# IExpress 的 SED 要逐条列文件，两千个文件不现实；
# 改成把整套程序打成单个 payload.zip，再用一个 cmd 负责解压 + 启动。
$payloadZip = Join-Path $sfxStaging "payload.zip"
Write-Host "    压缩 payload.zip …"
& tar.exe -a -c -f $payloadZip -C $stage .
Write-Host ("    payload.zip: {0:N1} MB" -f ((Get-Item $payloadZip).Length / 1MB))

$installCmd = Join-Path $sfxStaging "install.cmd"
$cmdText = "@echo off`r`nset `"DEST=%TEMP%\ShizukuDomainBoxSetup`"`r`nif exist `"%DEST%`" rmdir /s /q `"%DEST%`"`r`nmkdir `"%DEST%`"`r`ntar -xf `"%~dp0payload.zip`" -C `"%DEST%`"`r`nstart `"`" `"%DEST%\Setup.exe`"`r`nexit`r`n"
Write-Utf8NoBom $installCmd $cmdText

$target = Join-Path $dist "ShizukuDomainBox-Setup-v1.0.0.exe"
if (Test-Path $target) { Remove-Item $target -Force }

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
FriendlyName=Shizuku Domain Box Setup
AppLaunched=install.cmd
PostInstallCmd=<None>
AdminQuietInstCmd=
UserQuietInstCmd=
SourceFiles=SourceFiles
[Strings]
FILE0="payload.zip"
FILE1="install.cmd"
[SourceFiles]
SourceFiles0=$sfxStaging\
[SourceFiles0]
%FILE0%=
%FILE1%=
"@

$sedPath = "D:\dnshe-pc\build\setup.sed"
Write-Utf8NoBom $sedPath $sed

Write-Host "    运行 IExpress（大包会比较慢，请耐心等）…"
$p = Start-Process -FilePath "$env:SystemRoot\System32\iexpress.exe" -ArgumentList "/N","/Q","$sedPath" -Wait -PassThru
Write-Host "    IExpress 退出码: $($p.ExitCode)"

if (Test-Path $target) {
    Write-Host ("    生成: {0}  ({1:N1} MB)" -f $target, ((Get-Item $target).Length / 1MB))
} else {
    Write-Warning "IExpress 没有产出目标文件，请改用目录发行版"
}
