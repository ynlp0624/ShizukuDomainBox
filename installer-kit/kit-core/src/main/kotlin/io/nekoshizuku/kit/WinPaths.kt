package io.nekoshizuku.kit

import java.io.File

/**
 * 产品相关的路径 / 名字约定，全部由 [InstallerConfig] 推导。
 *
 * 老工程里这些是写死的常量（`WinPaths.APP_EXE` 之类），抽成工具后必须跟着配置走，
 * 否则「换产品只改配置」就是空话。
 */
class WinPaths(private val config: InstallerConfig) {

    /** 主程序 exe（app-image 根目录里那个） */
    val appExe: String get() = config.effectiveExeName

    /** 启动器的同名校验文件：`app\<appExe 去扩展名>.cfg` */
    val baseCfgName: String get() = File(appExe).nameWithoutExtension + ".cfg"

    /** 安装模式的身份：主程序 exe 的副本 + `app\Setup.cfg` */
    val setupExe: String get() = "Setup.exe"

    /** 卸载模式的身份：安装时生成的主程序 exe 副本 + `app\Uninstall.cfg` */
    val uninstallExe: String get() = "Uninstall.exe"

    /**
     * 便携模式的卸载器是个 PowerShell 脚本，不是 exe。
     * 因为便携模式的产品没有自带运行时，为了一个卸载器去装 57MB 的 JRE 不划算
     * —— 系统自带的 .NET 就够画那个小对话框了。
     */
    val uninstallScriptName: String get() = "Uninstall.ps1"

    /** 打包期放在 `app\` 里的卸载器模板，安装时被复制成安装目录里的 `Uninstall.ps1` */
    val uninstallTemplateName: String get() = "uninstall-template.ps1"

    /** 便携模式的产品文件在安装器里的位置 */
    val payloadDirName: String get() = "payload"

    /** 注册表里那棵树的键名 */
    val regKeyName: String get() = config.appId

    /** 显示名（快捷方式、控制面板、向导标题都用它） */
    val displayName: String get() = config.productName

    val publisher: String get() = config.publisher

    val version: String get() = config.effectiveVersion

    val installDirName: String get() = config.effectiveInstallDirName

    val dataDirName: String get() = config.effectiveDataDirName

    /** 控制面板里「支持链接」用的两个地址，留空就写空串 */
    val urlInfoAbout: String get() = config.urlInfoAbout

    val helpLink: String get() = config.helpLink

    /** 默认安装目录：仅我 = %LOCALAPPDATA%\Programs\<名>；所有用户 = %ProgramFiles%\<名> */
    fun defaultInstallDir(perUser: Boolean): File =
        if (perUser) File(WinEnv.localAppData(), "Programs\\$installDirName")
        else File(WinEnv.programFiles(), installDirName)

    /** 用户数据目录（产品自己的数据，不是本工具的） */
    fun dataDir(): File = File(WinEnv.roamingAppData(), dataDirName)

    fun desktopDir(perUser: Boolean): File = WinEnv.desktopDir(perUser)

    fun startMenuDir(perUser: Boolean): File = WinEnv.startMenuDir(perUser)

    /** 快捷方式文件名 */
    fun shortcutName(): String = "$displayName.lnk"

    fun desktopShortcut(perUser: Boolean): File = File(desktopDir(perUser), shortcutName())

    fun startMenuShortcut(perUser: Boolean): File = File(startMenuDir(perUser), shortcutName())

    /** 安装目录里的主程序 / 卸载器 */
    fun appExeIn(installDir: File): File = File(installDir, appExe)

    fun uninstallExeIn(installDir: File): File = File(installDir, uninstallExe)

    /** 便携模式：安装目录里的那个卸载脚本 */
    fun uninstallScriptIn(installDir: File): File = File(installDir, uninstallScriptName)

    /** 便携模式：安装目录里给卸载脚本用的信息文件 */
    fun uninstallInfoIn(installDir: File): File = File(installDir, "uninstall.json")

    /** 安装目录里的 `app\` 子目录 */
    fun appSubDirIn(installDir: File): File = File(installDir, "app")
}
