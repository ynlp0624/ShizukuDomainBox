package io.nekoshizuku.pack.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.nekoshizuku.kit.InstallerConfig
import io.nekoshizuku.kit.InstallerConfigs
import io.nekoshizuku.pack.AppImageProbe
import io.nekoshizuku.pack.BuildRunner
import io.nekoshizuku.pack.KitPaths
import io.nekoshizuku.pack.PackerSettings
import io.nekoshizuku.pack.ReleaseInfo
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.util.Collections

/** 打包器的两种形态：给新手的简单版，和给折腾的专业版 */
enum class PackerMode { SIMPLE, PRO }

/**
 * 两个界面共用的状态。
 *
 * 简单版和专业版**共用同一份状态和同一套动作** —— 区别只有「露出多少字段」。
 * 这样切模式的时候不会丢东西，也不会出现两个版本行为不一致。
 */
class PackerState {
    var kitRoot by mutableStateOf<File?>(null)
    var configPath by mutableStateOf<File?>(null)
    var config by mutableStateOf(InstallerConfig())
    var appImageDir by mutableStateOf<File?>(null)
    var probe by mutableStateOf<AppImageProbe.Probe?>(null)
    var buildProduct by mutableStateOf(true)
    var makeSingleExe by mutableStateOf(true)
    var running by mutableStateOf(false)
    var exitCode by mutableStateOf<Int?>(null)
    var release by mutableStateOf<ReleaseInfo?>(null)

    val log: MutableList<String> = Collections.synchronizedList(mutableListOf<String>())
    var logVersion by mutableStateOf(0)

    fun append(line: String) {
        log.add(line)
        logVersion++
    }

    fun clearLog() {
        log.clear()
        logVersion++
    }

    fun rememberSettings() {
        PackerSettings.save(
            PackerSettings(
                kitRoot = kitRoot?.absolutePath ?: "",
                configPath = configPath?.absolutePath ?: "",
                appImageDir = appImageDir?.absolutePath ?: "",
            ),
        )
    }
}

fun newState(): PackerState {
    val saved = PackerSettings.load()
    val st = PackerState()
    st.kitRoot = KitPaths.detect(remembered = saved.kitRoot.takeIf { it.isNotBlank() }?.let { File(it) })
    st.configPath = saved.configPath.takeIf { it.isNotBlank() }?.let { File(it) }
        ?: st.kitRoot?.let { File(it, KitPaths.DEFAULT_CONFIG) }
    st.config = loadConfig(st.configPath)

    // 上次选过的目录优先；没有就从配置里的 mainAppDir 反推 —— 打开就有内容，不用重新选一遍
    val fromSettings = saved.appImageDir.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.isDirectory }
    if (fromSettings != null) {
        st.appImageDir = fromSettings
        st.probe = AppImageProbe.probe(fromSettings)
    } else {
        syncAppImageFromConfig(st)
    }
    return st
}

fun loadConfig(file: File?): InstallerConfig =
    file?.takeIf { it.isFile }
        ?.let { runCatching { InstallerConfigs.load(it) }.getOrNull() }
        ?: InstallerConfig()

/** 从配置的 mainAppDir（相对配置文件解析）反推 app-image 目录并探测 */
fun syncAppImageFromConfig(st: PackerState) {
    val rel = st.config.mainAppDir
    if (rel.isBlank()) return
    val dir = File(rel).takeIf { it.isAbsolute } ?: st.configPath?.parentFile?.let { File(it, rel) }
    if (dir != null && dir.isDirectory) {
        st.appImageDir = dir
        st.probe = AppImageProbe.probe(dir)
    }
}

/**
 * 用户点了「主程序 exe 文件」或「程序文件夹」之后的统一处理。
 *
 * 选文件时：如果它的父目录不是个能打的 app-image，就往上找一两层试试
 * （新手很可能点进 `app\` 子目录里去了）。
 */
fun applyChosenPath(st: PackerState, chosen: File): String {
    val candidates = buildList {
        if (chosen.isDirectory) {
            add(chosen)
            chosen.parentFile?.let { add(it) }
        } else {
            chosen.parentFile?.let { add(it) }
            chosen.parentFile?.parentFile?.let { add(it) }
        }
    }
    val usable = candidates.firstOrNull { AppImageProbe.probe(it).usable }
    val target = usable ?: candidates.firstOrNull { it.isDirectory } ?: chosen.parentFile ?: chosen

    st.appImageDir = target
    st.probe = AppImageProbe.probe(target)
    st.rememberSettings()

    val probe = st.probe!!
    st.append("选中：${target.absolutePath}")
    st.append("  ${probe.summary}")
    probe.problems.forEach { st.append("  ⚠ $it") }

    if (probe.usable) {
        st.config = st.config.copy(
            productName = st.config.productName.ifBlank { probe.exeName?.removeSuffix(".exe") ?: target.name },
            appId = st.config.appId.ifBlank { probe.exeName?.removeSuffix(".exe") ?: target.name },
            mainAppDir = relativeTo(st.configPath?.parentFile, target),
            mainExeName = probe.exeName ?: st.config.mainExeName,
            installMode = probe.installMode,
            estimatedSizeMb = if (probe.sizeMb > 0) probe.sizeMb.toInt() else st.config.estimatedSizeMb,
            iconFile = st.config.iconFile.ifBlank { autoIco(target) },
        )
        if (usable == null) st.append("  （在上一层找到了可用的程序文件夹：${target.absolutePath}）")
    }
    return target.absolutePath
}

/** app-image 根目录里那个 .ico（jpackage 会把启动器图标放这儿） */
fun autoIco(dir: File): String {
    val ico = dir.listFiles { f: File -> f.isFile && f.extension.equals("ico", ignoreCase = true) }
        ?.firstOrNull() ?: return ""
    return ico.absolutePath
}

fun saveConfig(st: PackerState): Boolean {
    val file = st.configPath ?: run {
        st.append("没有指定配置文件路径")
        return false
    }
    return runCatching {
        file.parentFile?.mkdirs()
        file.writeText(InstallerConfigs.encode(st.config), Charsets.UTF_8)
    }.onSuccess {
        st.append("配置已保存：${file.absolutePath}")
        st.rememberSettings()
    }.onFailure {
        st.append("保存失败：${it.message}")
    }.isSuccess
}

fun loadConfigInto(st: PackerState) {
    val picked = chooseFile("载入 installer.config.json", listOf("json"), st.configPath?.absolutePath) ?: return
    runCatching { InstallerConfigs.load(picked) }
        .onSuccess {
            st.configPath = picked
            st.config = it
            syncAppImageFromConfig(st)
            st.append("配置已载入：${picked.absolutePath}")
            st.probe?.let { p -> st.append("  ${p.summary}") }
            st.rememberSettings()
        }
        .onFailure { st.append("载入失败：${it.message}") }
}

fun startBuild(st: PackerState, runner: BuildRunner, scope: CoroutineScope) {
    val file = st.configPath ?: run {
        st.append("没有指定配置文件路径")
        return
    }
    st.running = true
    st.exitCode = null
    st.release = null
    st.clearLog()
    st.append("=== 开始打包 ${st.config.productName} v${st.config.effectiveVersion} ===")

    saveConfig(st)

    val extra = buildList {
        if (!st.buildProduct) add("-SkipBuild")
        if (!st.makeSingleExe) add("-SkipIExpress")
    }

    runner.start(
        configFile = file,
        extraArgs = extra,
        scope = scope,
        onLine = { line -> st.append(line) },
        onExit = { code ->
            st.running = false
            st.exitCode = code
            if (code == 0) {
                st.release = runner.readRelease()
                st.release?.let { r ->
                    st.append("")
                    st.append("=== 产物 ===")
                    r.installer?.let { st.append("  单文件安装包：$it") }
                    r.stageDir?.let { st.append("  目录版：$it") }
                    r.sha256?.let { st.append("  SHA256：$it") }
                }
                st.append("")
                st.append("完成喵~")
            } else {
                st.append("")
                st.append("打包没成功（退出码 $code），往上看最后几行红字。")
            }
        },
    )
}

/* ============================== 系统对话框 ============================== */

fun chooseDirectory(title: String, initial: File?): File? = runCatching {
    val fc = javax.swing.JFileChooser().apply {
        fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
        dialogTitle = title
        initial?.takeIf { it.isDirectory }?.let { currentDirectory = it }
    }
    if (fc.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) fc.selectedFile else null
}.getOrNull()

fun chooseFile(title: String, extensions: List<String>, initial: String?): File? = runCatching {
    val fc = javax.swing.JFileChooser().apply {
        fileSelectionMode = javax.swing.JFileChooser.FILES_ONLY
        dialogTitle = title
        extensions.firstOrNull()?.let { ext ->
            fileFilter = javax.swing.filechooser.FileNameExtensionFilter("$ext 文件", ext)
        }
        initial?.takeIf { it.isNotBlank() }?.let { path ->
            val f = File(path)
            if (f.isDirectory) currentDirectory = f
            else {
                f.parentFile?.takeIf { it.isDirectory }?.let { currentDirectory = it }
                selectedFile = f
            }
        }
    }
    if (fc.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) fc.selectedFile else null
}.getOrNull()

fun openInExplorer(dir: File?) {
    if (dir == null || !dir.isDirectory) return
    runCatching { ProcessBuilder("explorer.exe", dir.absolutePath).start() }
}

/** 能转成相对配置文件的路径就转（配置好搬），不行就用绝对路径 */
fun relativeTo(base: File?, path: File): String {
    if (base == null) return path.absolutePath
    return runCatching { base.toPath().relativize(path.toPath()).toString().replace('/', '\\') }
        .getOrElse { path.absolutePath }
}
