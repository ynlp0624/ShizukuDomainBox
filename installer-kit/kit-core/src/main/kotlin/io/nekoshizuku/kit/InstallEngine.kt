package io.nekoshizuku.kit

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** 安装过程中的可预期失败（消息是可以直接给用户看的中文） */
class InstallException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 安装目录的问题码。
 *
 * 刻意返回**码**而不是中文句子：文案属于界面层（kit-ui 要出中英双语），
 * 核心库只管判断。
 */
enum class DirProblem { EMPTY, ROOT, ILLEGAL_CHARS, NOT_A_DIR }

/** 磁盘空间情况（都是 MB） */
data class DiskSpace(val availableMb: Long, val requiredMb: Long) {
    val ok: Boolean get() = availableMb >= requiredMb
}

/** 这台机器上已经装过的那一份 */
data class ExistingInstall(
    val perUser: Boolean,
    val installDir: File,
    val version: String?,
)

/**
 * 安装引擎。
 *
 * 核心思想（HANDOFF 1.2）：**安装 = 自我复制**。
 * 正在运行的这个 exe 所在目录就是整个 app-image（自带运行时的产品本体），
 * 把它整个复制到目标目录，再补一个卸载器 + 快捷方式 + 注册表项，就装完了。
 * 不需要额外塞 payload.zip，也不会多带一份运行时。
 */
class InstallEngine(val config: InstallerConfig) {

    val paths = WinPaths(config)
    private val reg = WinReg(paths)

    data class Options(
        val installDir: File,
        val perUser: Boolean,
        val desktopShortcut: Boolean = true,
        val startMenu: Boolean = true,
    )

    /**
     * 执行安装。任何失败都抛 [InstallException]（消息可直接展示）。
     *
     * @param source 正在运行的这套 app-image 的根目录（一般是 [KitSelf.appImageDir]）
     */
    fun install(
        source: File,
        options: Options,
        onProgress: (Int, String) -> Unit = { _, _ -> },
    ) {
        val src = canonical(source)
        val installDir = options.installDir

        if (!src.isDirectory) {
            throw InstallException("找不到程序自身目录（请从解压后的安装包里运行）：${src.absolutePath}")
        }
        validateInstallDir(installDir)?.let { throw InstallException(it) }

        if (src.path.equals(canonical(installDir).path, ignoreCase = true)) {
            throw InstallException("安装目录和当前运行目录相同，请换个位置")
        }

        onProgress(1, "检查磁盘空间…")
        diskSpace(installDir, requiredMb(config))?.let { space ->
            if (!space.ok) {
                throw InstallException(
                    "磁盘空间不够：${installDir.absolutePath} 所在磁盘只剩 ${space.availableMb} MB，" +
                        "装这一份需要约 ${space.requiredMb} MB。",
                )
            }
        }

        onProgress(2, "准备安装目录…")
        if (installDir.exists() && !installDir.isDirectory) {
            throw InstallException("目标路径被同名文件占用：${installDir.absolutePath}")
        }
        if (installDir.isDirectory) {
            // 覆盖安装：先把旧内容清掉，避免新旧文件混在一起
            if (!installDir.deleteRecursively() || installDir.exists()) {
                throw InstallException("无法清理旧的安装目录（可能有程序正在运行）：${installDir.absolutePath}")
            }
        }
        if (!installDir.mkdirs() && !installDir.isDirectory) {
            throw InstallException("无法创建安装目录：${installDir.absolutePath}")
        }

        val portable = config.isPortable

        onProgress(4, "复制程序文件…")
        if (portable) {
            // 便携模式：产品是普通文件夹，装的是 `app\payload\` 里的东西（平铺过去）
            copyPayload(src, installDir) { p, name -> onProgress(p, "复制 $name") }
        } else {
            copyImage(src, installDir) { p, name -> onProgress(p, "复制 $name") }
        }

        onProgress(84, "创建卸载程序…")
        // 便携模式的卸载器是个 PowerShell 脚本，UninstallString 因此是一整条命令行
        val uninstallCommand = if (portable) {
            writePortableUninstaller(src, installDir)
        } else {
            writeUninstaller(installDir)
            null
        }

        onProgress(88, "创建快捷方式…")
        createShortcuts(installDir, options)

        onProgress(94, "写入注册表…")
        val regOk = reg.writeInstallEntry(
            installDir = installDir,
            perUser = options.perUser,
            uninstallExe = paths.uninstallExeIn(installDir),
            appExe = paths.appExeIn(installDir),
            sizeKb = WinReg.dirSizeKb(installDir),
            uninstallCommand = uninstallCommand,
        )
        if (!regOk) {
            throw InstallException("写入注册表失败：程序装上了，但「设置 → 应用」里可能看不到这一条。")
        }

        onProgress(100, "完成")
    }

    /** UI 友好的包装：成功返回 null，失败返回可以直接给用户看的错误文本 */
    fun run(
        source: File,
        options: Options,
        onProgress: (Int, String) -> Unit = { _, _ -> },
    ): String? = runCatching { install(source, options, onProgress) }
        .exceptionOrNull()
        ?.let { describe(it, "安装失败") }

    /* ------------------------------ 覆盖安装检测 ------------------------------ */

    /**
     * 查一下这台机器上是不是已经装过了。
     *
     * 先看「仅当前用户」，再看「所有用户」—— 两份都可能存在
     * （用户自己先按用户装了一份，后来管理员又装了一份），先报用户级的那份。
     *
     * 注册表里有记录但目录已经没了（手动删了文件夹 / 卸载没干净）不算已安装。
     */
    fun detectExisting(): ExistingInstall? {
        for (perUser in listOf(true, false)) {
            val path = reg.readInstallPath(perUser) ?: continue
            val dir = File(path)
            if (!dir.isDirectory) continue
            return ExistingInstall(perUser, dir, reg.readVersion(perUser))
        }
        return null
    }

    /* ------------------------------ 内部步骤 ------------------------------ */

    /** 整个 app-image 复制过去；只跳过「打包期临时身份」那两个文件 */
    private fun copyImage(source: File, target: File, onProgress: (Int, String) -> Unit) {
        val files = source.walkTopDown().filter { it.isFile }.toList()
        val total = files.size.coerceAtLeast(1)

        files.forEachIndexed { idx, f ->
            val rel = f.relativeTo(source).path
            if (!isTransient(rel)) {
                val out = File(target, rel)
                out.parentFile?.mkdirs()
                try {
                    Files.copy(f.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING)
                } catch (e: Exception) {
                    throw InstallException("复制失败：$rel（${e.message}）", e)
                }
            }
            if (idx % 20 == 0 || idx == files.lastIndex) {
                onProgress(4 + (idx + 1) * 78 / total, rel)
            }
        }

        // 主程序 exe 必须在。极端情况下（本来就只有 Setup.exe）用 Setup.exe 顶上去。
        val wanted = paths.appExeIn(target)
        if (!wanted.isFile) {
            val setup = File(source, paths.setupExe)
            if (setup.isFile) {
                Files.copy(setup.toPath(), wanted.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } else {
                throw InstallException("复制后找不到主程序：${wanted.absolutePath}")
            }
        }

        val baseCfg = File(paths.appSubDirIn(target), paths.baseCfgName)
        if (!baseCfg.isFile) {
            throw InstallException("复制后找不到启动配置：${baseCfg.absolutePath}")
        }
    }

    /** 打包期身份文件：装完之后不该留在安装目录里 */
    private fun isTransient(relativePath: String): Boolean {
        val norm = relativePath.replace('/', '\\')
        return norm.equals(paths.setupExe, ignoreCase = true) ||
            norm.equals("app\\Setup.cfg", ignoreCase = true)
    }

    /**
     * 便携模式：把 `app\payload\` 里的产品文件**平铺**复制进安装目录。
     *
     * 平铺（而不是套一层文件夹）是故意的 —— 绿色软件本来就该保持它原来的目录结构。
     */
    private fun copyPayload(source: File, target: File, onProgress: (Int, String) -> Unit) {
        val payload = File(source, "app\\${paths.payloadDirName}")
        if (!payload.isDirectory) {
            throw InstallException("安装包里没有产品文件：${payload.absolutePath}")
        }
        val files = payload.walkTopDown().filter { it.isFile }.toList()
        val total = files.size.coerceAtLeast(1)

        files.forEachIndexed { idx, f ->
            val rel = f.relativeTo(payload).path
            val out = File(target, rel)
            out.parentFile?.mkdirs()
            try {
                Files.copy(f.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } catch (e: Exception) {
                throw InstallException("复制失败：$rel（${e.message}）", e)
            }
            if (idx % 20 == 0 || idx == files.lastIndex) {
                onProgress(4 + (idx + 1) * 78 / total, rel)
            }
        }

        val wanted = paths.appExeIn(target)
        if (!wanted.isFile) {
            throw InstallException("复制后找不到主程序：${wanted.absolutePath}（配置里的 mainExeName 对不对？）")
        }
    }

    /**
     * 便携模式的卸载器：把打包期放进 `app\` 的脚本模板复制成安装目录里的
     * `Uninstall.ps1`，再写一份 `uninstall.json` 告诉它卸什么。
     *
     * 返回可以直接写进注册表 UninstallString 的一整条命令。
     */
    private fun writePortableUninstaller(source: File, installDir: File): String {
        val template = File(source, "app\\${paths.uninstallTemplateName}")
        if (!template.isFile) {
            throw InstallException("安装包里缺少卸载器模板：${template.absolutePath}")
        }
        val script = paths.uninstallScriptIn(installDir)
        try {
            Files.copy(template.toPath(), script.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            throw InstallException("无法创建卸载程序：${e.message}", e)
        }

        val info = PortableUninstallInfo(
            productName = paths.displayName,
            appId = paths.regKeyName,
            version = paths.version,
            publisher = paths.publisher,
            mainExe = paths.appExe,
            dataDirName = paths.dataDirName,
            installDir = installDir.absolutePath,
        )
        // 不带 BOM：PowerShell 的 ConvertFrom-Json 读带 BOM 的也能认，但不带最保险
        paths.uninstallInfoIn(installDir).writeText(
            InstallerConfigs.json.encodeToString(PortableUninstallInfo.serializer(), info),
            Charsets.UTF_8,
        )

        val ps = File(System.getenv("SystemRoot") ?: "C:\\Windows", "System32\\WindowsPowerShell\\v1.0\\powershell.exe")
        return "\"${ps.absolutePath}\" -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File \"${script.absolutePath}\""
    }

    /**
     * 生成卸载器：
     * - `Uninstall.exe` = 主程序 exe 的副本（jpackage 启动器按同名的 cfg 读配置，
     *   所以 `Uninstall.exe` 会去读 `app\Uninstall.cfg`）
     * - `app\Uninstall.cfg` = 原 cfg → 换 mainclass 成 kit 向导 → 补 kit 的 classpath
     *   → 追加 `[ArgOptions] arguments=--uninstall`
     */
    private fun writeUninstaller(installDir: File) {
        val appExe = paths.appExeIn(installDir)
        if (!appExe.isFile) throw InstallException("安装目录里没有主程序：${appExe.absolutePath}")

        try {
            Files.copy(
                appExe.toPath(),
                paths.uninstallExeIn(installDir).toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (e: Exception) {
            throw InstallException("无法创建卸载程序：${e.message}", e)
        }

        val appDir = paths.appSubDirIn(installDir)
        val baseCfg = File(appDir, paths.baseCfgName)
        if (!baseCfg.isFile) throw InstallException("找不到启动配置：${baseCfg.absolutePath}")

        val derived = JpackageCfg.deriveIdentity(
            baseCfg = baseCfg.readText(Charsets.UTF_8),
            mainClass = config.wizardMainClass,
            extraClasspath = config.wizardClasspath,
            arguments = "--uninstall",
        )
        File(appDir, "Uninstall.cfg").writeText(derived, Charsets.UTF_8)
    }

    private fun createShortcuts(installDir: File, options: Options) {
        val appExe = paths.appExeIn(installDir)
        val desktopLink = paths.desktopShortcut(options.perUser)
        val menuLink = paths.startMenuShortcut(options.perUser)

        runCatching { desktopLink.delete() }
        runCatching { menuLink.delete() }

        if (options.desktopShortcut) {
            WinShortcut.create(desktopLink, appExe, "", installDir, appExe, paths.displayName)
        }
        if (options.startMenu) {
            WinShortcut.create(menuLink, appExe, "", installDir, appExe, paths.displayName)
        }
    }

    companion object {

        fun canonical(file: File): File = runCatching { file.canonicalFile }.getOrElse { file.absoluteFile }

        fun describe(error: Throwable, fallback: String): String = when (error) {
            is InstallException -> error.message ?: fallback
            else -> "${error::class.java.simpleName}: ${error.message ?: fallback}"
        }

        private val ILLEGAL_DIR_CHARS = charArrayOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')

        /** 磁盘余量系数：复制过程中有额外开销，别卡着边装 */
        private const val SPACE_MARGIN = 1.15

        /** 装这一份大约要多少 MB */
        fun requiredMb(config: InstallerConfig): Long =
            (config.estimatedSizeMb * SPACE_MARGIN).toLong() + 20

        /**
         * 查目标目录所在卷还剩多少 MB。
         *
         * 返回 null = 查不到（比如盘还没挂上、或者文件系统不报可用空间）——
         * 这时候**不要**拦着用户，交给真正的写入去失败。
         *
         * 目录还不存在时，往上找到第一个存在的祖先再问。
         */
        fun diskSpace(dir: File, requiredMb: Long): DiskSpace? {
            if (requiredMb <= 0) return null
            val anchor = generateSequence(dir.absoluteFile) { it.parentFile }
                .firstOrNull { it.exists() } ?: return null
            val usable = runCatching { anchor.usableSpace }.getOrNull() ?: return null
            if (usable <= 0) return null
            return DiskSpace(usable / (1024 * 1024), requiredMb)
        }

        /** 检查安装目录。返回 null = 通过。 */
        fun checkInstallDir(dir: File): DirProblem? {
            // 注意：File("").absolutePath 是「当前工作目录」，不是空串，所以要先看原始 path
            if (dir.path.isBlank()) return DirProblem.EMPTY
            val path = dir.absolutePath.trim()
            if (path.isEmpty()) return DirProblem.EMPTY
            if (path.length < 4 || path.endsWith(":\\")) return DirProblem.ROOT
            val last = dir.name
            if (last.isEmpty()) return DirProblem.NOT_A_DIR
            if (last.indexOfAny(ILLEGAL_DIR_CHARS) >= 0) return DirProblem.ILLEGAL_CHARS
            return null
        }

        /** 老接口：直接给一句中文，方便命令行 / 自检用 */
        fun validateInstallDir(dir: File): String? = when (checkInstallDir(dir)) {
            null -> null
            DirProblem.EMPTY -> "安装目录不能为空"
            DirProblem.ROOT -> "请选一个具体的目录，不能直接装到盘符根目录"
            DirProblem.NOT_A_DIR -> "请选一个具体的目录"
            DirProblem.ILLEGAL_CHARS -> "目录名里有非法字符：${dir.name}"
        }
    }
}
