@echo off
rem Re-add the UTF-8 BOM to every .ps1 under tools\ (recursively), then syntax-check them.
rem
rem Why the BOM: PowerShell 5.1 decodes a BOM-less .ps1 as ANSI (GBK on a Chinese
rem Windows), so Chinese comments/strings turn into garbage and the script fails to
rem parse. Most editors/tools (including patch tools) drop the BOM when saving, so
rem run this after every edit.
rem
rem Why recursive: the portable-mode wizard lives in tools\portable\ and is copied
rem into install packages as-is. A missed subdirectory = an installer that can't run.
rem
rem Why syntax-check here: a mangled encoding shows up as confusing "Unexpected
rem token" errors at unrelated line numbers. Checking right after the fix turns a
rem 10-minute mystery into an instant message.
rem
rem This wrapper is a .cmd on purpose: a .ps1 fixer would itself need a BOM.
rem Keep this file ASCII-only (cmd.exe reads .cmd as ANSI).
rem
rem Usage: tools\fix-bom.cmd

setlocal
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$bad=0; Get-ChildItem '%~dp0' -Recurse -Filter '*.ps1' | ForEach-Object { $t=[IO.File]::ReadAllText($_.FullName,(New-Object Text.UTF8Encoding($false))); [IO.File]::WriteAllText($_.FullName,$t,(New-Object Text.UTF8Encoding($true))); $e=$null; [void][Management.Automation.Language.Parser]::ParseFile($_.FullName,[ref]$null,[ref]$e); $rel=$_.FullName.Replace('%~dp0',''); if ($e -and $e.Count -gt 0) { $script:bad++; Write-Host ('[BOM ok / SYNTAX ERROR] ' + $rel); $e | Select-Object -First 5 | ForEach-Object { Write-Host ('    line ' + $_.Extent.StartLineNumber + ': ' + $_.Message) } } else { Write-Host ('[BOM ok / syntax ok]  ' + $rel) } }; if ($bad -gt 0) { exit 1 }"
endlocal
