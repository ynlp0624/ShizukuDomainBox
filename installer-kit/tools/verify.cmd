@echo off
rem InstallerKit end-to-end acceptance check.
rem Wrapper exists to bypass the PowerShell execution policy (Restricted by default).
rem NOTE: keep this file ASCII-only (cmd.exe reads .cmd as ANSI).
rem Usage: tools\verify.cmd [-InstallDir "D:\tmp\XxxTest"]
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0verify-install.ps1" %*
exit /b %ERRORLEVEL%
