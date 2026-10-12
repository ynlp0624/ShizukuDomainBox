@echo off
rem InstallerKit one-click packaging.
rem Wrapper exists to bypass the PowerShell execution policy (Restricted by default).
rem NOTE: keep this file ASCII-only. cmd.exe reads .cmd as ANSI, so non-ASCII
rem       comments turn into garbage and get executed as commands.
rem Usage: tools\build.cmd [-SkipBuild] [-SkipIExpress]
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-installer.ps1" %*
exit /b %ERRORLEVEL%
