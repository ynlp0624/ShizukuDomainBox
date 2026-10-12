package io.nekoshizuku.kit

import java.io.File

/**
 * 卸载 / 修复引擎。
 *
 * 两个必须记住的坑（HANDOFF 2.4 / 3.3）：
 * 1. 运行中的 exe 删不掉自己，而且**正在跑的 JVM 会锁住 `app\*.jar`**。
 *    所以只能先删能删的，剩下的交给一个延迟批处理**循环重试** —— 一次 `rmdir` 必然失败。
 * 2. 卸载器自己就是主程序 exe 的副本，所以「跳过自己」的判断要用
 *    `jpackage.app-path`（真实运行路径），不能靠文件名猜。
 */
class UninstallEngine(val config: InstallerConfig) {

    val paths = WinPaths(config)
    private val reg = WinReg(paths)

    /** 卸载 */
    fun uninstall(
        installDir: File,
        perUser: Boolean,
        keepData: Boolean,
        dataDir: File = paths.dataDir(),
        onProgress: (Int, String) -> Unit = { _, _ -> },
    ) {
        onProgress(5, "关闭正在运行的程序…")
        if (config.killRunningAppOnUninstall) killRunningApp()

        onProgress(15, "删除注册表项…")
        reg.removeInstallEntry(perUser)

        onProgress(28, "删除快捷方式…")
        runCatching {
            paths.desktopShortcut(perUser).delete()
            paths.startMenuShortcut(perUser).delete()
        }

        if (!keepData) {
            onProgress(45, "删除账号数据…")
            runCatching { dataDir.deleteRecursively() }
        }

        onProgress(60, "删除程序文件…")
        deleteContents(installDir)

        onProgress(85, "清理安装目录…")
        launchSelfDelete(installDir)

        onProgress(100, if (keepData) "完成（账号数据已保留）" else "完成")
    }

    /** 修复：重建快捷方式 + 重写注册表项，数据不动 */
    fun repair(
        installDir: File,
        perUser: Boolean,
        onProgress: (Int, String) -> Unit = { _, _ -> },
    ) {
        val appExe = paths.appExeIn(installDir)
        if (!appExe.isFile) throw InstallException("找不到主程序，无法修复：${appExe.absolutePath}")

        onProgress(30, "重建快捷方式…")
        runCatching {
            WinShortcut.create(
                paths.desktopShortcut(perUser), appExe, "", installDir, appExe, paths.displayName,
            )
            WinShortcut.create(
                paths.startMenuShortcut(perUser), appExe, "", installDir, appExe, paths.displayName,
            )
        }

        onProgress(70, "重写注册表…")
        reg.writeInstallEntry(
            installDir = installDir,
            perUser = perUser,
            uninstallExe = paths.uninstallExeIn(installDir),
            appExe = appExe,
            sizeKb = WinReg.dirSizeKb(installDir),
        )
        onProgress(100, "修复完成")
    }

    /** UI 友好的包装：成功返回 null，失败返回可以直接给用户看的错误文本 */
    fun runUninstall(
        installDir: File,
        perUser: Boolean,
        keepData: Boolean,
        dataDir: File = paths.dataDir(),
        onProgress: (Int, String) -> Unit = { _, _ -> },
    ): String? = runCatching { uninstall(installDir, perUser, keepData, dataDir, onProgress) }
        .exceptionOrNull()
        ?.let { InstallEngine.describe(it, "卸载失败") }

    fun runRepair(
        installDir: File,
        perUser: Boolean,
        onProgress: (Int, String) -> Unit = { _, _ -> },
    ): String? = runCatching { repair(installDir, perUser, onProgress) }
        .exceptionOrNull()
        ?.let { InstallEngine.describe(it, "修复失败") }

    /* ------------------------------ 内部 ------------------------------ */

    /** 关掉正在运行的产品主程序（否则它锁着 jar，删不干净） */
    fun killRunningApp() {
        runCatching { WinExec.run("taskkill", "/IM", paths.appExe, "/F", "/T", timeoutSeconds = 20) }
        // 给它一点时间真正退出
        runCatching { Thread.sleep(500) }
    }

    /**
     * 删掉安装目录里除了「自己和包含自己的目录」之外的所有东西。
     * 剩下的目录交给 [launchSelfDelete] 的重试批处理。
     */
    private fun deleteContents(installDir: File) {
        val self = KitSelf.exeFile
        runCatching {
            installDir.listFiles()?.forEach { f ->
                val cf = runCatching { f.canonicalFile }.getOrNull() ?: return@forEach
                if (self != null && (cf == self || self.path.startsWith(cf.path + File.separator))) return@forEach
                f.deleteRecursively()
            }
        }
    }

    /**
     * exe 删不掉自己，而且运行中的 JVM 会锁住 app 目录下的 jar。
     * 所以起一个批处理：先等一会儿，然后循环重试 rmdir，直到目录真的没了（或试满 30 次）。
     */
    fun launchSelfDelete(installDir: File) {
        runCatching {
            val dir = installDir.absolutePath
            val script = buildString {
                append("@echo off\r\n")
                append("setlocal enabledelayedexpansion\r\n")
                append("ping -n 4 127.0.0.1 > nul\r\n")
                append("set /a n=0\r\n")
                append(":retry\r\n")
                append("rmdir /s /q \"$dir\" 2>nul\r\n")
                append("if not exist \"$dir\" goto done\r\n")
                append("set /a n+=1\r\n")
                append("if !n! geq 30 goto done\r\n")
                append("ping -n 2 127.0.0.1 > nul\r\n")
                append("goto retry\r\n")
                append(":done\r\n")
                append("rmdir /q \"$dir\" 2>nul\r\n")
                append("del /f /q \"%~f0\"\r\n")
            }
            val bat = File.createTempFile("kit-uninst-", ".bat")
            bat.writeText(script, Charsets.US_ASCII)
            // 用「工作目录 + 纯文件名」启动：%TEMP% 的路径里可能有空格
            // （用户名带空格时就是 C:\Users\John Doe\AppData\Local\Temp），
            // 直接把完整路径塞给 start 会被切碎。
            ProcessBuilder("cmd", "/c", "start", "\"\"", "/min", "cmd", "/c", bat.name)
                .directory(bat.parentFile)
                .start()
        }
    }
}
