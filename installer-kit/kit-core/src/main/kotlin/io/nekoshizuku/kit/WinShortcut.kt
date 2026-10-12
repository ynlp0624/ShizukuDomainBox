package io.nekoshizuku.kit

import java.io.File

/**
 * 快捷方式。走 PowerShell 的 WScript.Shell COM —— 中文路径 / 中文文件名都稳。
 *
 * ⚠️ 在 Kotlin 字符串里拼 PowerShell，`$` 必须写成 `${'$'}`，
 * 否则会被当成 Kotlin 字符串模板（老工程踩过）。
 */
object WinShortcut {

    fun create(
        link: File,
        targetExe: File,
        args: String,
        workDir: File,
        iconExe: File,
        description: String = "",
    ): Boolean = runCatching {
        link.parentFile?.mkdirs()
        val ps = buildString {
            append("\$ws = New-Object -ComObject WScript.Shell; ")
            append("\$s = \$ws.CreateShortcut('${link.absolutePath.replace("'", "''")}'); ")
            append("\$s.TargetPath = '${targetExe.absolutePath.replace("'", "''")}'; ")
            if (args.isNotBlank()) append("\$s.Arguments = '${args.replace("'", "''")}'; ")
            append("\$s.WorkingDirectory = '${workDir.absolutePath.replace("'", "''")}'; ")
            append("\$s.IconLocation = '${iconExe.absolutePath.replace("'", "''")},0'; ")
            if (description.isNotBlank()) append("\$s.Description = '${description.replace("'", "''")}'; ")
            append("\$s.Save()")
        }
        val p = ProcessBuilder("powershell", "-NoProfile", "-WindowStyle", "Hidden", "-Command", ps)
            .redirectErrorStream(true).start()
        p.inputStream.bufferedReader().readText()
        p.waitFor() == 0 && link.isFile
    }.getOrDefault(false)
}
