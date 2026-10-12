@echo off
rem Create (or remove) desktop + start menu shortcuts for the packer.
rem Usage: tools\shortcuts.cmd [-Remove]
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0make-shortcuts.ps1" %*
exit /b %ERRORLEVEL%
