package io.nekoshizuku.pack

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 跑打包流水线 —— 复用 `tools\build-installer.ps1`。
 *
 * 为什么不把流水线用 Kotlin 重写一遍：那套流程（组装 → 签名 → 封包 → 验签）
 * 已经被 69 项端到端验收盯住了，是**唯一被证明能出正确安装包**的东西。
 * 打包器的职责是「收集配置 + 起进程 + 把日志显示出来」，不是重造一遍。
 */
class BuildRunner(private val kitRoot: File) {

    private var process: Process? = null

    val script: File get() = File(kitRoot, KitPaths.BUILD_SCRIPT)

    fun isAvailable(): Boolean = script.isFile

    fun isRunning(): Boolean = process?.isAlive == true

    fun cancel() {
        runCatching { process?.destroyForcibly() }
    }

    /**
     * 起一个打包任务。日志按行回调（在 IO 线程读、由调用方负责切回 UI 线程）。
     *
     * @param extraArgs 额外的脚本参数，比如 `-SkipIExpress`
     * @param onExit 退出码（正常完成才有意义；被取消会是 1 或 -1）
     */
    fun start(
        configFile: File,
        extraArgs: List<String> = emptyList(),
        scope: CoroutineScope,
        onLine: (String) -> Unit,
        onExit: (Int) -> Unit,
    ) {
        if (isRunning()) {
            onLine("已经有一个打包任务在跑了")
            return
        }
        if (!isAvailable()) {
            onLine("找不到打包脚本：${script.absolutePath}")
            onExit(-1)
            return
        }

        val cmd = mutableListOf(
            "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
            "-File", script.absolutePath,
            "-Config", configFile.absolutePath,
        )
        cmd += extraArgs

        scope.launch {
            val code = withContext(Dispatchers.IO) {
                runCatching {
                    val p = ProcessBuilder(cmd)
                        .directory(kitRoot)
                        .redirectErrorStream(true)
                        .start()
                    process = p
                    p.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        reader.forEachLine { line -> onLine(line) }
                    }
                    p.waitFor()
                }.getOrElse { e ->
                    onLine("启动打包脚本失败：${e.message}")
                    -1
                }
            }
            process = null
            onExit(code)
        }
    }

    /** 阻塞式跑完（命令行 / 自检用） */
    fun runBlocking(configFile: File, extraArgs: List<String> = emptyList(), onLine: (String) -> Unit): Int {
        if (!isAvailable()) {
            onLine("找不到打包脚本：${script.absolutePath}")
            return -1
        }
        val cmd = mutableListOf(
            "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
            "-File", script.absolutePath,
            "-Config", configFile.absolutePath,
        )
        cmd += extraArgs
        return runCatching {
            val p = ProcessBuilder(cmd).directory(kitRoot).redirectErrorStream(true).start()
            process = p
            p.inputStream.bufferedReader(Charsets.UTF_8).use { reader -> reader.forEachLine(onLine) }
            p.waitFor()
        }.getOrElse { e ->
            onLine("启动打包脚本失败：${e.message}")
            -1
        }.also { process = null }
    }

    /** 产物信息（打包脚本会写 dist\release.json） */
    fun readRelease(): ReleaseInfo? = runCatching {
        val f = File(kitRoot, "dist\\release.json")
        if (!f.isFile) return null
        ReleaseInfo.parse(f.readText(Charsets.UTF_8))
    }.getOrNull()
}

/** `dist\release.json` 里的内容（打包脚本写的） */
data class ReleaseInfo(
    val productName: String,
    val appId: String,
    val version: String,
    val installer: String?,
    val stageDir: String?,
    val sha256: String?,
) {
    companion object {
        /**
         * 手写解析：只要这几个字段，不值得为它引一个序列化依赖到界面层。
         *
         * ⚠️ 必须做 JSON 反转义：`ConvertTo-Json` 写出来的 Windows 路径是
         * `"D:\\installer-kit\\dist\\x.exe"`，直接当字符串用会得到双反斜杠的错路径。
         */
        fun parse(text: String): ReleaseInfo {
            fun str(key: String): String? =
                Regex("\"$key\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(text)
                    ?.groupValues?.get(1)
                    ?.let { unescapeJson(it) }
                    ?.takeIf { it.isNotEmpty() && it != "null" }

            return ReleaseInfo(
                productName = str("productName") ?: "",
                appId = str("appId") ?: "",
                version = str("version") ?: "",
                installer = str("installer"),
                stageDir = str("stageDir"),
                sha256 = str("sha256"),
            )
        }

        /** 够用的 JSON 字符串反转义（\\ \" \/ \n \r \t） */
        fun unescapeJson(raw: String): String {
            val sb = StringBuilder(raw.length)
            var i = 0
            while (i < raw.length) {
                val c = raw[i]
                if (c == '\\' && i + 1 < raw.length) {
                    when (val n = raw[i + 1]) {
                        '\\' -> sb.append('\\')
                        '"' -> sb.append('"')
                        '/' -> sb.append('/')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'u' -> {
                            if (i + 5 < raw.length) {
                                val hex = raw.substring(i + 2, i + 6)
                                sb.append(hex.toIntOrNull(16)?.toChar() ?: '?')
                                i += 6
                                continue
                            }
                            sb.append(n)
                        }
                        else -> sb.append(n)
                    }
                    i += 2
                } else {
                    sb.append(c)
                    i++
                }
            }
            return sb.toString()
        }
    }
}
