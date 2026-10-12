package io.nekoshizuku.dnshe.desktop

import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI

/** 桌面平台相关的系统能力：剪贴板 / 打开链接 / 文件选择框 */
object Sys {

    fun copyToClipboard(text: String): Boolean = runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        true
    }.getOrDefault(false)

    fun readClipboard(): String? = runCatching {
        val cb = Toolkit.getDefaultToolkit().systemClipboard
        if (!cb.isDataFlavorAvailable(DataFlavor.stringFlavor)) return@runCatching null
        cb.getData(DataFlavor.stringFlavor) as? String
    }.getOrNull()

    fun openUrl(url: String): Boolean = runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
            true
        } else {
            ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start()
            true
        }
    }.getOrDefault(false)

    /** 原生「另存为」对话框 */
    fun pickSaveFile(defaultName: String, title: String = "保存"): File? {
        val d = FileDialog(null as Frame?, title, FileDialog.SAVE)
        d.file = defaultName
        d.isVisible = true
        val dir = d.directory ?: return null
        val name = d.file ?: return null
        return File(dir, name)
    }

    /** 原生「打开」对话框 */
    fun pickOpenFile(title: String = "打开", onlyJson: Boolean = true): File? {
        val d = FileDialog(null as Frame?, title, FileDialog.LOAD)
        if (onlyJson) d.setFilenameFilter { _, name -> name.endsWith(".json", true) }
        d.isVisible = true
        val dir = d.directory ?: return null
        val name = d.file ?: return null
        return File(dir, name)
    }

    /** 在资源管理器里定位文件 */
    fun revealInExplorer(file: File) {
        runCatching {
            ProcessBuilder("explorer.exe", "/select,${file.absolutePath}").start()
        }
    }

    fun openFolder(dir: File) {
        runCatching { ProcessBuilder("explorer.exe", dir.absolutePath).start() }
    }
}

/* ====================== 剪贴板里的密钥识别（与安卓版同规则） ============== */

private val KEY_REGEX = Regex("""(cfsd_[A-Za-z0-9_\-]{4,})""")
private val JSON_KEY = Regex(""""api_key"\s*:\s*"([^"]+)"""")
private val JSON_SECRET = Regex(""""api_secret"\s*:\s*"([^"]+)"""")
private val HDR_KEY = Regex("""X-API-Key\s*:\s*(\S+)""", RegexOption.IGNORE_CASE)
private val HDR_SECRET = Regex("""X-API-Secret\s*:\s*(\S+)""", RegexOption.IGNORE_CASE)
private val BARE_SECRET = Regex("""^[A-Za-z0-9]{12,80}$""")

data class DetectedCredential(val key: String, val secret: String?)

fun detectCredential(raw: String?): DetectedCredential? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return null

    val key = JSON_KEY.find(text)?.groupValues?.get(1)
        ?: HDR_KEY.find(text)?.groupValues?.get(1)
        ?: KEY_REGEX.find(text)?.groupValues?.get(1)
        ?: return null

    val secret = JSON_SECRET.find(text)?.groupValues?.get(1)
        ?: HDR_SECRET.find(text)?.groupValues?.get(1)

    val resolved = secret ?: text.lines()
        .map { it.trim().trim(',', '"', '\'') }
        .firstOrNull { line ->
            line != key && !line.contains("cfsd_") && BARE_SECRET.matches(line) &&
                !line.startsWith("{") && !line.startsWith("X-API", ignoreCase = true) &&
                !line.contains("api_key", ignoreCase = true)
        }
        ?: text.split(Regex("""[\s,;"']+"""))
            .firstOrNull { it != key && !it.contains("cfsd_") && BARE_SECRET.matches(it) }

    return DetectedCredential(key.trim(), resolved?.trim())
}
