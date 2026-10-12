package io.nekoshizuku.kit

import java.io.File
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** 跑外部命令并拿到输出。刻意只走 ProcessBuilder，不引任何依赖。 */
object WinExec {

    data class CmdResult(val exitCode: Int, val output: String) {
        val ok: Boolean get() = exitCode == 0
    }

    fun run(vararg args: String, timeoutSeconds: Long = 60): CmdResult = runCatching {
        val process = ProcessBuilder(*args).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return CmdResult(-1, output)
        }
        CmdResult(process.exitValue(), output)
    }.getOrElse { CmdResult(-1, it.message ?: it::class.java.simpleName) }

    fun ok(vararg args: String): Boolean = run(*args).ok
}

/** 一条要写进注册表的值 */
data class RegValue(val name: String, val value: String, val type: String = REG_SZ) {
    companion object {
        const val REG_SZ = "REG_SZ"
        const val REG_DWORD = "REG_DWORD"
    }
}

/**
 * 注册表读写。
 *
 * 写的是「程序和功能」里那一条 —— 少一个键值在控制面板里就会显示不全，
 * 完整清单见 HANDOFF 第 2.2 节。
 *
 * ⚠️ **写入刻意不用 `reg.exe`**。原因：`QuietUninstallString` 的值形如
 * `"C:\Program Files\X\Uninstall.exe" /S /KEEPDATA`，**同时含空格和引号**；
 * 从 JVM 交给 reg.exe 时，Windows 会先拼好整条命令行再让 CRT 解析，`/d` 的参数
 * 会被切碎，reg.exe 报「错误: 无效语法」并且**一个字节都不写**（安静地失败）。
 *
 * 所以改成 `powershell -EncodedCommand <base64>`：脚本走 Base64 传给 PowerShell，
 * 完全绕开命令行解析，空格 / 引号 / 中文 / `%` 全都安全；顺便把十几次进程调用
 * 合并成一次。
 */
class WinReg(private val paths: WinPaths) {

    private fun root(perUser: Boolean) = if (perUser) "HKCU" else "HKLM"

    fun uninstallKey(perUser: Boolean): String =
        "${root(perUser)}\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\${paths.regKeyName}"

    fun appKey(perUser: Boolean): String = "${root(perUser)}\\Software\\${paths.regKeyName}"

    /* ------------------------------ 写 ------------------------------ */

    /** 一次 PowerShell 把 [values] 全部写进 [key]。返回是否成功。 */
    fun writeValues(key: String, values: List<RegValue>): Boolean {
        if (values.isEmpty()) return true
        val script = buildWriteScript(key, values)
        val encoded = Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
        return WinExec.run(
            "powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
            "-EncodedCommand", encoded,
        ).ok
    }

    /** 写「程序和功能」里那一条 + 一份给自己看的 InstallPath */
    fun writeInstallEntry(
        installDir: File,
        perUser: Boolean,
        uninstallExe: File,
        appExe: File,
        sizeKb: Long,
        /**
         * 覆盖 UninstallString。便携模式用它 —— 那边的「卸载器」是一条
         * `powershell.exe -File ...\Uninstall.ps1` 命令，不是 exe。
         */
        uninstallCommand: String? = null,
    ): Boolean {
        // 带引号：路径有空格时没有引号，控制面板里点「卸载」就失败
        val uninstallString = uninstallCommand ?: "\"${uninstallExe.absolutePath}\""
        val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

        // 静默卸载的参数：exe 用 kit 自己的 /S，脚本用它的 -Silent
        val quietExtra = if (uninstallCommand != null) "-Silent" else "/S /KEEPDATA"

        val entry = listOf(
            RegValue("DisplayName", paths.displayName),
            RegValue("DisplayVersion", paths.version),
            RegValue("Publisher", paths.publisher),
            RegValue("InstallLocation", installDir.absolutePath),
            RegValue("InstallDate", date),
            RegValue("UninstallString", uninstallString),
            RegValue("QuietUninstallString", "$uninstallString $quietExtra"),
            RegValue("DisplayIcon", appExe.absolutePath),
            RegValue("URLInfoAbout", paths.urlInfoAbout),
            RegValue("HelpLink", paths.helpLink),
            RegValue("NoModify", "1", RegValue.REG_DWORD),
            RegValue("NoRepair", "1", RegValue.REG_DWORD),
            RegValue("Language", "2052", RegValue.REG_DWORD),
            RegValue("EstimatedSize", sizeKb.toString(), RegValue.REG_DWORD),
        )
        val app = listOf(
            RegValue("InstallPath", installDir.absolutePath),
            RegValue("Version", paths.version),
            RegValue("PerUser", if (perUser) "1" else "0"),
            RegValue("UninstallString", uninstallString),
        )

        var ok = writeValues(uninstallKey(perUser), entry)
        if (!writeValues(appKey(perUser), app)) ok = false
        return ok
    }

    /* ------------------------------ 读 ------------------------------ */

    fun query(key: String, name: String): String? {
        val out = WinExec.run("reg", "query", key, "/v", name).output
        return Regex("${Regex.escape(name)}\\s+REG_SZ\\s+(.+)")
            .find(out)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun readInstallPath(perUser: Boolean): String? = query(appKey(perUser), "InstallPath")

    fun readVersion(perUser: Boolean): String? = query(appKey(perUser), "Version")

    /** 「程序和功能」里是否已经有这一条（覆盖安装检测用） */
    fun hasInstallEntry(perUser: Boolean): Boolean = query(uninstallKey(perUser), "DisplayName") != null

    /** 删掉注册表项。键不存在时 reg.exe 会返回非 0，忽略即可。 */
    fun removeInstallEntry(perUser: Boolean) {
        WinExec.run("reg", "delete", uninstallKey(perUser), "/f")
        WinExec.run("reg", "delete", appKey(perUser), "/f")
    }

    companion object {

        /**
         * 生成「写注册表」的 PowerShell 脚本。
         *
         * 抽成独立函数是为了能被无头自检直接断言（见 VerifyCore）——
         * 这段代码里到处都是 PowerShell 的 `$`，写在 Kotlin 字符串里必须
         * 写成 `${'$'}`，忘了就编译不过（或者更糟：编过了但脚本是错的）。
         */
        fun buildWriteScript(key: String, values: List<RegValue>): String {
            val psPath = toProviderPath(key)
            return buildString {
                append("\$p = ${psQuote(psPath)}; ")
                append("if (-not (Test-Path -LiteralPath \$p)) { New-Item -Path \$p -Force | Out-Null }; ")
                values.forEach { v ->
                    val isDword = v.type.equals(RegValue.REG_DWORD, ignoreCase = true)
                    val type = if (isDword) "DWord" else "String"
                    val valueExpr = if (isDword) (v.value.toIntOrNull() ?: 0).toString() else psQuote(v.value)
                    append("New-ItemProperty -LiteralPath \$p -Name ${psQuote(v.name)} ")
                    append("-Value $valueExpr -PropertyType $type -Force | Out-Null; ")
                }
                append("exit 0")
            }
        }

        /** `HKCU\Software\X` → `HKCU:\Software\X`（PowerShell 注册表 provider 要这个写法） */
        fun toProviderPath(key: String): String {
            val hiveEnd = key.indexOf('\\')
            if (hiveEnd < 0) return "$key:\\"
            return key.substring(0, hiveEnd) + ":\\" + key.substring(hiveEnd + 1)
        }

        /** PowerShell 单引号字符串：里面的单引号写成两个 */
        fun psQuote(s: String): String = "'" + s.replace("'", "''") + "'"

        fun dirSizeKb(dir: File): Long =
            runCatching { dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } / 1024 }.getOrDefault(0)

        /** `net session` 能跑通 = 当前是管理员 */
        fun isAdmin(): Boolean = runCatching {
            val p = ProcessBuilder("net", "session").redirectErrorStream(true).start()
            p.inputStream.bufferedReader().readText()
            p.waitFor() == 0
        }.getOrDefault(false)
    }
}
