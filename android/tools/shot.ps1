# Emulator debug helper: screenshot + UI hierarchy dump + crash check
# Usage:  powershell -File D:\dnshe\tools\shot.ps1 -Name accounts
param(
    [string]$Name = "shot",
    [string]$OutDir = "D:\dnshe\screens",
    [switch]$NoUi
)

$ErrorActionPreference = "Continue"
$adb = "D:\Android\Sdk\platform-tools\adb.exe"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$png = Join-Path $OutDir "$Name.png"
# NOTE: do NOT use "adb exec-out screencap -p > file" in Windows PowerShell 5.1:
# its redirection re-encodes the byte stream as text and corrupts the PNG.
& $adb shell screencap -p /sdcard/_shot.png 2>&1 | Out-Null
& $adb pull /sdcard/_shot.png $png 2>&1 | Out-Null
$f = Get-Item $png -ErrorAction SilentlyContinue
if ($f) { Write-Host "screenshot -> $png  ($([math]::Round($f.Length/1KB,1)) KB)" } else { Write-Host "screenshot FAILED" }

if (-not $NoUi) {
    $remote = "/sdcard/ui.xml"
    & $adb shell uiautomator dump $remote 2>&1 | Out-Null
    $local = Join-Path $OutDir "$Name.xml"
    & $adb pull $remote $local 2>&1 | Out-Null
    if (Test-Path $local) {
        [xml]$doc = Get-Content -LiteralPath $local -Encoding UTF8
        $nodes = $doc.SelectNodes("//node")
        Write-Host "--- visible nodes ($($nodes.Count)) ---"
        foreach ($n in $nodes) {
            $t = $n.GetAttribute("text")
            $d = $n.GetAttribute("content-desc")
            $cls = $n.GetAttribute("class")
            $b = $n.GetAttribute("bounds")
            $click = $n.GetAttribute("clickable")
            if ($t -or $d) {
                Write-Host ("  [{0}] text='{1}' desc='{2}' clickable={3} {4}" -f $cls.Split('.')[-1], $t, $d, $click, $b)
            }
        }
    }
}

Write-Host "--- crash check ---"
$crash = & $adb logcat -d -s AndroidRuntime:E 2>&1 | Select-String -Pattern "FATAL|nekoshizuku" | Select-Object -Last 25
if ($crash) { $crash | ForEach-Object { Write-Host $_ } } else { Write-Host "  no crash found [OK]" }
