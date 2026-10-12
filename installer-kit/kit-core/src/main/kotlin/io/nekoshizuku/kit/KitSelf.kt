package io.nekoshizuku.kit

import java.io.File

/**
 * 运行中这套 app-image 的「自我认知」。
 *
 * 关键点：jpackage 启动器会注入 `jpackage.app-path` 系统属性，指向正在运行的 exe。
 * 它的父目录就是**整个 app-image 根目录** —— 安装 = 把这个目录整个复制走。
 */
object KitSelf {

    /** 正在运行的 exe 的完整路径 */
    val exePath: String? get() = System.getProperty("jpackage.app-path")

    /** 正在运行的 exe */
    val exeFile: File?
        get() = exePath?.let { runCatching { File(it).canonicalFile }.getOrNull() }

    /** 正在运行的 exe 的文件名（模式判定的依据之一） */
    val exeName: String get() = exeFile?.name.orEmpty()

    /** app-image 根目录（exe 所在目录）—— 安装时要复制的货物就是它 */
    val appImageDir: File? get() = exeFile?.parentFile

    /** 打包期注入的配置：`<app-image>\app\installer.config.json` */
    val configFile: File? get() = appImageDir?.let { File(File(it, "app"), InstallerConfigs.FILE_NAME) }

    /** 是否跑在 jpackage app-image 里（开发期直接 java -jar 时为 false） */
    val inAppImage: Boolean get() = appImageDir != null
}

/** 三种身份 */
enum class RunMode {
    /** 普通启动（产品自己的程序） */
    NORMAL,

    /** 安装模式：Setup.exe，或带 --install */
    INSTALL,

    /** 卸载模式：Uninstall.exe，或带 --uninstall */
    UNINSTALL,
}

/**
 * 模式判定：**参数 + 文件名双重判定**。
 * 命令行直接传参能进对模式，双击改过名的 exe 也能进对模式。
 */
object ModeDetector {
    fun detect(args: List<String>, exeName: String = KitSelf.exeName): RunMode {
        if (args.any { it.equals("--uninstall", true) }) return RunMode.UNINSTALL
        if (args.any { it.equals("--install", true) }) return RunMode.INSTALL
        if (exeName.startsWith("Uninstall", ignoreCase = true)) return RunMode.UNINSTALL
        if (exeName.startsWith("Setup", ignoreCase = true)) return RunMode.INSTALL
        return RunMode.NORMAL
    }
}

/**
 * 命令行参数。支持静默安装/卸载：
 *
 * ```
 * Setup.exe     /S [/DIR=<目录>] [/SCOPE=all|user] [/NODESKTOP] [/NOSTARTMENU] [/NOLAUNCH]
 * Uninstall.exe /S [/KEEPDATA] [--data-dir=<目录>]
 * ```
 *
 * 为了兼容老惯例，`/XXX` 和 `--xxx` 两种写法都认。
 */
class KitArgs(args: List<String>) {

    val raw: List<String> = args.toList()

    private fun value(vararg names: String): String? =
        raw.firstOrNull { a -> names.any { n -> a.startsWith("$n=", ignoreCase = true) } }
            ?.substringAfter('=')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun flag(vararg names: String): Boolean =
        raw.any { a -> names.any { n -> a.equals(n, ignoreCase = true) } }

    /** 静默模式：不显示界面，装完自己退出（退出码 0 = 成功，2 = 失败） */
    val silent: Boolean get() = flag("/S", "/SILENT", "--silent")

    /** 安装目录 */
    val installDir: File? get() = value("/DIR", "--dir")?.let { File(it) }

    /** 安装范围：true = 所有用户，false = 仅当前用户，null = 没指定 */
    val scopeAll: Boolean?
        get() = value("/SCOPE", "--scope")?.let { it.equals("all", ignoreCase = true) }

    /** 卸载时保留账号数据 */
    val keepData: Boolean get() = flag("/KEEPDATA", "--keep-data")

    val noDesktopShortcut: Boolean get() = flag("/NODESKTOP", "--no-desktop-shortcut")
    val noStartMenu: Boolean get() = flag("/NOSTARTMENU", "--no-start-menu")
    val noLaunch: Boolean get() = flag("/NOLAUNCH", "--no-launch")

    /** 覆盖用户数据目录 */
    val dataDirOverride: File? get() = value("--data-dir")?.let { File(it) }

    /** 界面语言（`--lang=zh` / `--lang=en`）；没给就跟随系统 */
    val lang: String? get() = value("/LANG", "--lang")

    override fun toString(): String = raw.joinToString(" ")
}
