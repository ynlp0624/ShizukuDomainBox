@echo off
rem Launch the InstallerKit packer (GUI).
rem If it has not been built yet, build it first.
rem NOTE: keep this file ASCII-only (cmd.exe reads .cmd as ANSI).
setlocal
set "ROOT=%~dp0.."
set "EXE=%ROOT%\packer\build\compose\binaries\main\app\InstallerKitPacker\InstallerKitPacker.exe"

if exist "%EXE%" (
    start "" "%EXE%"
    exit /b 0
)

echo Packer is not built yet. Building it now (first run takes a minute or two)...
if not defined JAVA_HOME set "JAVA_HOME=D:\Android\jdk-17"
call "%ROOT%\..\Android\gradle-8.7\bin\gradle.bat" -p "%ROOT%" :packer:createDistributable --console=plain
if errorlevel 1 (
    echo Build failed. See the output above.
    pause
    exit /b 1
)
start "" "%EXE%"
exit /b 0
