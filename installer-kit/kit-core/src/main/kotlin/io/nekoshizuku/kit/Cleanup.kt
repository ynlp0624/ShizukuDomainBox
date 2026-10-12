package io.nekoshizuku.kit

import java.io.File

/**
 * 临时目录清理。
 *
 * IExpress 封的单文件安装包会把自己解压到 `%TEMP%\<名字>Setup\` 再启动 Setup.exe，
 * 装完那个目录就没人管了 —— 一次留下 100+ MB。所以安装结束时排一个退出后删除的任务。
 *
 * 只删 `%TEMP%` 下面的目录，别的一律不动。
 */
object TempCleanup {

    /** 如果是临时目录就排进清理队列；返回是否真的排了 */
    fun scheduleIfTemp(dir: File): Boolean {
        val temp = runCatching { WinEnv.tempDir().canonicalFile }.getOrNull() ?: return false
        val target = runCatching { dir.canonicalFile }.getOrNull() ?: return false
        if (target.path.equals(temp.path, ignoreCase = true)) return false
        if (!target.path.startsWith(temp.path + File.separator, ignoreCase = true)) return false
        return schedule(target)
    }

    /** 排一个「等一会儿再循环删」的批处理（正在跑的 JVM 会锁着 jar，一次删不掉） */
    fun schedule(dir: File, attempts: Int = 15): Boolean = runCatching {
        val path = dir.absolutePath
        val script = buildString {
            append("@echo off\r\n")
            append("setlocal enabledelayedexpansion\r\n")
            append("ping -n 3 127.0.0.1 > nul\r\n")
            append("set /a n=0\r\n")
            append(":retry\r\n")
            append("rmdir /s /q \"$path\" 2>nul\r\n")
            append("if not exist \"$path\" goto done\r\n")
            append("set /a n+=1\r\n")
            append("if !n! geq $attempts goto done\r\n")
            append("ping -n 2 127.0.0.1 > nul\r\n")
            append("goto retry\r\n")
            append(":done\r\n")
            append("del /f /q \"%~f0\"\r\n")
        }
        val bat = File.createTempFile("kit-cleanup-", ".bat")
        bat.writeText(script, Charsets.US_ASCII)
        // 同 UninstallEngine：用「工作目录 + 纯文件名」，躲开 %TEMP% 里的空格
        ProcessBuilder("cmd", "/c", "start", "\"\"", "/min", "cmd", "/c", bat.name)
            .directory(bat.parentFile)
            .start()
        true
    }.getOrDefault(false)
}
