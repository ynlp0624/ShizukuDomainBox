package io.nekoshizuku.kit

import kotlinx.serialization.Serializable

/**
 * 便携模式：安装时写给 `Uninstall.ps1` 看的信息文件（`<安装目录>\uninstall.json`）。
 *
 * 卸载器是个独立的 PowerShell 脚本，运行时读不到 kit 的配置，所以这里把
 * 它需要知道的几件事落成盘上的小文件。
 */
@Serializable
data class PortableUninstallInfo(
    val productName: String,
    val appId: String,
    val version: String,
    val publisher: String,
    /** 安装目录里主程序 exe 的名字（快捷方式、进程查杀都要用） */
    val mainExe: String,
    /** `%APPDATA%\<这个名字>` 是用户数据目录；「彻底删除」时连它一起删 */
    val dataDirName: String,
    val installDir: String,
)
