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

    /**
     * 当前主窗口。
     *
     * **为什么用全局变量而不是 CompositionLocal**：
     * 对话框的 owner 需要在 onClick / 非 @Composable 的闭包里拿到，
     * 而 `host.show { }` 这类回调的 lambda 不是 @Composable，读不到 CompositionLocal。
     * 主窗只有一个，创建时塞进来最省事，也最不容易漏。
     *
     * 用途：传给 Swing 的 JFileChooser / FileDialog 当 owner。
     * **不传 owner 时 Swing 会临时造一个隐藏的共享窗口当 owner**，
     * 对话框不受主窗管辖 —— 表现出来就是"点了没反应"或者弹到主窗后面。
     */
    @Volatile
    var mainWindow: java.awt.Window? = null

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

    /**
     * 原生「另存为」对话框。
     *
     * owner 一定要传真正的窗口（Compose 里用 `LocalWindow.current`）：
     * 传 null 时 Swing 会造一个**隐藏的共享 owner 窗口**，对话框不受主窗管辖，
     * 于是可能跑到主窗后面去，看起来就像"点了没反应"。
     */
    fun pickSaveFile(defaultName: String, title: String = "保存", owner: java.awt.Window? = mainWindow): File? {
        val d = FileDialog(owner as? Frame, title, FileDialog.SAVE)
        d.file = defaultName
        d.isVisible = true
        val dir = d.directory ?: return null
        val name = d.file ?: return null
        return File(dir, name)
    }

    /** 原生「打开」对话框（同样要传 owner） */
    fun pickOpenFile(title: String = "打开", onlyJson: Boolean = true, owner: java.awt.Window? = mainWindow): File? {
        val d = FileDialog(owner as? Frame, title, FileDialog.LOAD)
        if (onlyJson) d.setFilenameFilter { _, name -> name.endsWith(".json", true) }
        d.isVisible = true
        val dir = d.directory ?: return null
        val name = d.file ?: return null
        return File(dir, name)
    }

    /**
     * 目录选择框。
     *
     * 优先用 **Windows 原生**的 SHBrowseForFolder（JNA），失败再退回 Swing 的 JFileChooser。
     *
     * 为什么不直接用 JFileChooser：在 jpackage 精简运行时里它会抛
     * `Cannot invoke "java.awt.Image.getWidth(...)" because "<local6>" is null`，
     * 对话框根本弹不出来。
     */
    fun pickDirectory(
        title: String = "选择文件夹",
        initial: File? = null,
        owner: java.awt.Window? = mainWindow,
    ): File? {
        // ① 原生
        io.nekoshizuku.dnshe.desktop.ui.WinFolderPicker.choose(owner, title, initial)?.let { return it }
        // ② 备选：Swing（哪怕抛异常也不会把整个向导带崩）
        return runCatching {
            val fc = javax.swing.JFileChooser().apply {
                fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
                dialogTitle = title
                if (initial != null) currentDirectory = if (initial.isDirectory) initial else initial.parentFile
            }
            if (fc.showOpenDialog(owner) == javax.swing.JFileChooser.APPROVE_OPTION) fc.selectedFile else null
        }.getOrNull()
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
