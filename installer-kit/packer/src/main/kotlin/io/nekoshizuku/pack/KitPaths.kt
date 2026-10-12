package io.nekoshizuku.pack

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 找到「工具本体」在哪 —— 也就是 `tools\build-installer.ps1` 所在的工程根目录。
 *
 * 打包器本身可能从三个地方被启动：Gradle 的 `:packer:run`、IDE、或者一个已打包的
 * app-image。工作目录各不相同，所以要挨个试。
 */
object KitPaths {

    const val BUILD_SCRIPT = "tools\\build-installer.ps1"
    const val VERIFY_SCRIPT = "tools\\verify-install.ps1"
    const val DEFAULT_CONFIG = "installer.config.json"

    fun looksLikeKitRoot(dir: File?): Boolean =
        dir != null && dir.isDirectory && File(dir, BUILD_SCRIPT).isFile

    /** 从 [start] 往上找，直到磁盘根 */
    private fun walkUp(start: File?): File? {
        var cur = start?.absoluteFile
        var guard = 0
        while (cur != null && guard++ < 12) {
            if (looksLikeKitRoot(cur)) return cur
            cur = cur.parentFile
        }
        return null
    }

    /** 依次尝试：显式指定 → 记住的设置 → 工作目录往上 → 打包器自己的位置附近 → 常见位置 */
    fun detect(explicit: File? = null, remembered: File? = null): File? =
        listOfNotNull(
            explicit?.takeIf { looksLikeKitRoot(it) },
            remembered?.takeIf { looksLikeKitRoot(it) },
            walkUp(File(System.getProperty("user.dir") ?: ".")),
            walkUp(PackerSettings.file().parentFile ?: File(".")),
            File("D:\\installer-kit").takeIf { looksLikeKitRoot(it) },
        ).firstOrNull()
}

/** 打包器自己的偏好设置（记住上次用的工程目录 / 配置文件） */
@Serializable
data class PackerSettings(
    val kitRoot: String = "",
    val configPath: String = "",
    val appImageDir: String = "",
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

        fun dir(): File = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "InstallerKitPacker")

        fun file(): File = File(dir(), "settings.json")

        fun load(): PackerSettings = runCatching {
            val f = file()
            if (f.isFile) json.decodeFromString(PackerSettings.serializer(), f.readText(Charsets.UTF_8))
            else PackerSettings()
        }.getOrElse { PackerSettings() }

        fun save(settings: PackerSettings): Boolean = runCatching {
            val f = file()
            f.parentFile?.mkdirs()
            f.writeText(json.encodeToString(PackerSettings.serializer(), settings), Charsets.UTF_8)
            true
        }.getOrDefault(false)
    }
}
