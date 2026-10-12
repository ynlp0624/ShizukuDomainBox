package io.nekoshizuku.pack

import java.io.File

/**
 * 探测一个程序文件夹 —— 这是「导入主程序」那一步的全部逻辑。
 *
 * 要认两种东西：
 *
 * **1. jpackage app-image**（自带运行时的 Java 程序）
 * ```
 * MyApp\
 * ├── MyApp.exe          ← 启动器
 * ├── app\MyApp.cfg      ← 启动配置（改它就能让同一个 exe 变成安装向导）
 * ├── app\*.jar
 * └── runtime\           ← jlink 裁出来的 JRE
 * ```
 * 判据：根目录有 exe + `app\<同名>.cfg` + `app\` 里有 jar + 有 `runtime\`
 *
 * **2. 绿色软件 / 便携程序**（普通文件夹，一个 exe + 一堆文件）
 * ```
 * 我的工具\
 * ├── 我的工具.exe
 * ├── data\
 * └── 说明.txt
 * ```
 * 判据：根目录有 exe，但**没有** jpackage 那套结构。这种也能打包，
 * 走的是轻量向导（不背 57MB 运行时），安装包只比原程序大几十 KB。
 */
object AppImageProbe {

    /** 这两个名字的 exe 是「安装/卸载身份」，不是产品主程序，探测时要排除 */
    private val IDENTITY_EXES = setOf("setup.exe", "uninstall.exe")

    enum class Kind {
        /** jpackage app-image：自带运行时，安装器共用它的运行时 */
        JPACKAGE,

        /** 绿色软件：普通文件夹，走轻量向导 */
        PORTABLE,

        /** 认不出来 */
        UNKNOWN,
    }

    data class Probe(
        val dir: File,
        val kind: Kind,
        val exeName: String?,
        val cfgName: String?,
        val jarCount: Int,
        val hasRuntime: Boolean,
        val hasJavaDesktop: Boolean,
        val sizeMb: Long,
        val fileCount: Int,
        val problems: List<String>,
    ) {
        /** 配置里该写什么 installMode */
        val installMode: String
            get() = when (kind) {
                Kind.JPACKAGE -> "jpackage"
                Kind.PORTABLE -> "portable"
                Kind.UNKNOWN -> ""
            }

        /** 可以直接拿去打包吗 */
        val usable: Boolean get() = kind != Kind.UNKNOWN && exeName != null

        val modeLabel: String
            get() = when (kind) {
                Kind.JPACKAGE -> "自带运行时的程序（jpackage）"
                Kind.PORTABLE -> "绿色软件（一个 exe + 一堆文件）"
                Kind.UNKNOWN -> "认不出来"
            }

        val summary: String
            get() = when (kind) {
                Kind.UNKNOWN -> "这个文件夹不像是个程序"
                Kind.PORTABLE -> "$exeName · 绿色软件 · $fileCount 个文件 · ${sizeMb} MB（安装包会很小）"
                Kind.JPACKAGE -> "$exeName · app\\$cfgName · $jarCount 个 jar · ${sizeMb} MB" +
                    if (hasJavaDesktop) " · java.desktop ✓" else " · ⚠ 没有 java.desktop"
            }
    }

    fun probe(dir: File): Probe {
        if (!dir.isDirectory) {
            return Probe(dir, Kind.UNKNOWN, null, null, 0, false, false, 0, 0, listOf("目录不存在：${dir.absolutePath}"))
        }
        val problems = mutableListOf<String>()

        // ---------- 找主程序 exe ----------
        val exes = dir.listFiles { f: File -> f.isFile && f.extension.equals("exe", ignoreCase = true) }
            ?.filter { it.name.lowercase() !in IDENTITY_EXES }
            ?.sortedBy { it.name }
            .orEmpty()

        if (exes.isEmpty()) problems += "根目录里没找到主程序 exe"
        if (exes.size > 1) {
            problems += "根目录里有多个 exe（${exes.joinToString(", ") { it.name }}），默认选了第一个，请确认"
        }
        val exe = exes.firstOrNull()

        // ---------- jpackage 特征 ----------
        val appDir = File(dir, "app")
        val cfg = exe?.let { File(appDir, it.nameWithoutExtension + ".cfg") }?.takeIf { it.isFile }
        val jarCount = appDir.listFiles { f: File -> f.isFile && f.extension.equals("jar", ignoreCase = true) }?.size ?: 0
        val runtime = File(dir, "runtime")
        val release = File(runtime, "release")
        val releaseText = if (release.isFile) runCatching { release.readText(Charsets.UTF_8) }.getOrNull() else null
        val hasDesktop = releaseText?.contains("java.desktop") == true

        val kind = when {
            exe == null -> Kind.UNKNOWN
            cfg != null && jarCount > 0 && runtime.isDirectory -> Kind.JPACKAGE
            else -> Kind.PORTABLE
        }

        if (kind == Kind.JPACKAGE) {
            if (releaseText == null) {
                problems += "有 runtime\\ 但没有 runtime\\release，看不出运行时带了哪些模块"
            } else if (!hasDesktop) {
                problems += "运行时里没有 java.desktop —— 安装向导（Compose 写的）会起不来"
            }
        }

        if (kind == Kind.PORTABLE) {
            if (cfg != null && jarCount > 0 && !runtime.isDirectory) {
                problems += "有 app\\ 和 .cfg 但没有 runtime\\ —— 这像是 jpackage app-image 少了运行时，按绿色软件打会让程序跑不起来"
            }
        }

        val sizeMb = runCatching {
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } / 1024 / 1024
        }.getOrDefault(0)
        val fileCount = runCatching { dir.walkTopDown().count { it.isFile } }.getOrDefault(0)

        return Probe(
            dir = dir,
            kind = kind,
            exeName = exe?.name,
            cfgName = cfg?.name,
            jarCount = jarCount,
            hasRuntime = runtime.isDirectory,
            hasJavaDesktop = hasDesktop,
            sizeMb = sizeMb,
            fileCount = fileCount,
            problems = problems,
        )
    }
}
