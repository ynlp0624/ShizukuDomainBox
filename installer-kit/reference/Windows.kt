package io.nekoshizuku.dnshe.core

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Windows 平台路径约定（安装 / 卸载 / 快捷方式都用这套） */
object WinPaths {
    const val APP_EXE = "ShizukuDomainBox.exe"
    const val UNINSTALL_EXE = "Uninstall.exe"
    const val REG_KEY_NAME = "ShizukuDomainBox"
    const val DISPLAY_NAME = "雫的域名盒"
    const val PUBLISHER = "Nekoshizuku"
    const val VERSION = "1.0.0"
    const val APP_DATA_FOLDER = "ShizukuDomainBox"

    fun programFiles(): File = File(System.getenv("ProgramFiles") ?: "C:\\Program Files")

    fun localAppData(): File = File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"))

    /** 默认安装目录 */
    fun defaultInstallDir(perUser: Boolean): File =
        if (perUser) File(localAppData(), "Programs\\$APP_DATA_FOLDER")
        else File(programFiles(), APP_DATA_FOLDER)

    /** 用户数据目录（和 AppPaths 保持一致） */
    fun dataDir(): File {
        val appData = System.getenv("APPDATA") ?: System.getProperty("user.home")
        return File(appData, APP_DATA_FOLDER)
    }

    fun desktopDir(perUser: Boolean): File =
        if (perUser) File(System.getenv("USERPROFILE") ?: System.getProperty("user.home"), "Desktop")
        else File(System.getenv("PUBLIC") ?: "C:\\Users\\Public", "Desktop")

    fun startMenuDir(perUser: Boolean): File =
        if (perUser) File(System.getenv("APPDATA") ?: "", "Microsoft\\Windows\\Start Menu\\Programs")
        else File(System.getenv("ProgramData") ?: "C:\\ProgramData", "Microsoft\\Windows\\Start Menu\\Programs")
}

/** 注册表读写（走 reg.exe，避免引入额外依赖） */
object WinReg {

    private fun root(perUser: Boolean) = if (perUser) "HKCU" else "HKLM"

    fun uninstallKey(perUser: Boolean): String =
        "${root(perUser)}\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\${WinPaths.REG_KEY_NAME}"

    fun appKey(perUser: Boolean): String = "${root(perUser)}\\Software\\${WinPaths.REG_KEY_NAME}"

    fun run(vararg args: String): String = runCatching {
        val p = ProcessBuilder(*args).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        out
    }.getOrDefault("")

    fun add(key: String, name: String, value: String, type: String = "REG_SZ") {
        run("reg", "add", key, "/v", name, "/t", type, "/d", value, "/f")
    }

    fun deleteTree(key: String) {
        run("reg", "delete", key, "/f")
    }

    fun query(key: String, name: String): String? {
        val out = run("reg", "query", key, "/v", name)
        return Regex("$name\\s+REG_SZ\\s+(.+)").find(out)?.groupValues?.get(1)?.trim()
    }

    /** 写「程序和功能」里那一条 */
    fun writeInstallEntry(installDir: File, perUser: Boolean, uninstallExe: File, appExe: File, sizeKb: Long) {
        val key = uninstallKey(perUser)
        val uninstallString = "\"${uninstallExe.absolutePath}\""
        val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

        add(key, "DisplayName", WinPaths.DISPLAY_NAME)
        add(key, "DisplayVersion", WinPaths.VERSION)
        add(key, "Publisher", WinPaths.PUBLISHER)
        add(key, "InstallLocation", installDir.absolutePath)
        add(key, "InstallDate", date)
        add(key, "UninstallString", uninstallString)
        add(key, "QuietUninstallString", "$uninstallString /S /KEEPDATA")
        add(key, "DisplayIcon", appExe.absolutePath)
        add(key, "URLInfoAbout", "https://my.dnshe.com/")
        add(key, "HelpLink", "https://my.dnshe.com/knowledgebase/13/DNSHE-Free-Domain-API-User-Guide-V2.0.html")
        add(key, "NoModify", "1", "REG_DWORD")
        add(key, "NoRepair", "1", "REG_DWORD")
        add(key, "Language", "2052", "REG_DWORD")
        add(key, "EstimatedSize", sizeKb.toString(), "REG_DWORD")

        val app = appKey(perUser)
        add(app, "InstallPath", installDir.absolutePath)
        add(app, "Version", WinPaths.VERSION)
        add(app, "PerUser", if (perUser) "1" else "0")
    }

    fun readInstallPath(perUser: Boolean): String? = query(appKey(perUser), "InstallPath")

    fun removeInstallEntry(perUser: Boolean) {
        deleteTree(uninstallKey(perUser))
        deleteTree(appKey(perUser))
    }

    fun dirSizeKb(dir: File): Long =
        runCatching { dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } / 1024 }.getOrDefault(0)

    fun isAdmin(): Boolean = runCatching {
        val p = ProcessBuilder("net", "session").redirectErrorStream(true).start()
        p.inputStream.bufferedReader().readText()
        p.waitFor() == 0
    }.getOrDefault(false)
}

/** 快捷方式（用 WScript.Shell COM，中文路径也稳） */
object WinShortcut {
    fun create(link: File, targetExe: File, args: String, workDir: File, iconExe: File): Boolean = runCatching {
        link.parentFile?.mkdirs()
        val ps = buildString {
            // $ 必须转义，否则会被 Kotlin 当字符串模板
            append("\$ws = New-Object -ComObject WScript.Shell; ")
            append("\$s = \$ws.CreateShortcut('${link.absolutePath.replace("'", "''")}'); ")
            append("\$s.TargetPath = '${targetExe.absolutePath.replace("'", "''")}'; ")
            if (args.isNotBlank()) append("\$s.Arguments = '$args'; ")
            append("\$s.WorkingDirectory = '${workDir.absolutePath.replace("'", "''")}'; ")
            append("\$s.IconLocation = '${iconExe.absolutePath.replace("'", "''")},0'; ")
            append("\$s.Description = '${WinPaths.DISPLAY_NAME}'; ")
            append("\$s.Save()")
        }
        val p = ProcessBuilder("powershell", "-NoProfile", "-WindowStyle", "Hidden", "-Command", ps)
            .redirectErrorStream(true).start()
        p.inputStream.bufferedReader().readText()
        p.waitFor() == 0 && link.exists()
    }.getOrDefault(false)
}

/** 卸载 / 修复 */
object WinUninstall {

    fun uninstall(installDir: File, perUser: Boolean, keepData: Boolean, onProgress: (Int, String) -> Unit) {
        onProgress(15, "删除注册表项…")
        WinReg.removeInstallEntry(perUser)

        onProgress(30, "删除快捷方式…")
        runCatching {
            File(WinPaths.desktopDir(perUser), "${WinPaths.DISPLAY_NAME}.lnk").delete()
            File(WinPaths.startMenuDir(perUser), "${WinPaths.DISPLAY_NAME}.lnk").delete()
        }

        if (!keepData) {
            onProgress(45, "删除账号数据…")
            runCatching { WinPaths.dataDir().deleteRecursively() }
        }

        onProgress(60, "删除程序文件…")
        val self = System.getProperty("jpackage.app-path")?.let { runCatching { File(it).canonicalFile }.getOrNull() }
        runCatching {
            installDir.listFiles()?.forEach { f ->
                val cf = runCatching { f.canonicalFile }.getOrNull() ?: return@forEach
                // 自己正在运行，跳过自己和包含自己的目录
                if (self != null && (cf == self || self.path.startsWith(cf.path + File.separator))) return@forEach
                f.deleteRecursively()
            }
        }

        onProgress(85, "清理安装目录…")
        launchSelfDelete(installDir)

        onProgress(100, "完成")
    }

    /**
     * exe 删不掉自己，而且运行中的 JVM 会锁住 app 目录下的 jar。
     * 所以起一个批处理：先等一会儿，然后循环重试 rmdir，直到目录真的没了（或试满 30 次）。
     */
    private fun launchSelfDelete(installDir: File) {
        runCatching {
            val bat = File.createTempFile("dnshe-uninst", ".bat")
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
            bat.writeText(script, Charsets.UTF_8)
            ProcessBuilder("cmd", "/c", "start", "\"\"", "/min", "cmd", "/c", bat.absolutePath).start()
        }
    }

    /** 修复：重建快捷方式 + 重写注册表 */
    fun repair(installDir: File, perUser: Boolean, onProgress: (Int, String) -> Unit) {
        onProgress(30, "重建快捷方式…")
        val appExe = File(installDir, WinPaths.APP_EXE)
        val uninstallExe = File(installDir, WinPaths.UNINSTALL_EXE)
        runCatching {
            WinShortcut.create(File(WinPaths.desktopDir(perUser), "${WinPaths.DISPLAY_NAME}.lnk"), appExe, "", installDir, appExe)
            WinShortcut.create(File(WinPaths.startMenuDir(perUser), "${WinPaths.DISPLAY_NAME}.lnk"), appExe, "", installDir, appExe)
        }
        onProgress(70, "重写注册表…")
        WinReg.writeInstallEntry(installDir, perUser, uninstallExe, appExe, WinReg.dirSizeKb(installDir))
        onProgress(100, "修复完成")
    }
}
