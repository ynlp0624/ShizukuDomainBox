package io.nekoshizuku.kit.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.Image
import java.io.File
import javax.imageio.ImageIO

/**
 * 向导用到的图片素材。
 *
 * **优先用打包期注入的产品素材**（`tools\build-installer.ps1` 会把它们放到 app-image 的 `app\` 下）：
 *
 * | 文件 | 用途 |
 * |---|---|
 * | `app\kit-logo.png` | 向导左侧品牌区的 logo |
 * | `app\kit-icon-<size>.png` | 窗口标题栏 / 任务栏图标 |
 *
 * 找不到就退回 kit 自带的 classpath 资源（`/app-logo.png`、`/app-icon-*.png`）——
 * 这样「换个产品只改配置」连外观也一起换了，而 kit 单独跑（诊断页）也不会没图。
 */
object KitAssets {

    const val LOGO_FILE = "kit-logo.png"
    const val ICON_PREFIX = "kit-icon-"

    val DEFAULT_ICON_SIZES = listOf(16, 20, 24, 32, 40, 48, 64, 96, 128, 256)

    fun appDir(appImageDir: File?): File? = appImageDir?.let { File(it, "app") }

    fun productLogoFile(appImageDir: File?): File? =
        appDir(appImageDir)?.let { File(it, LOGO_FILE) }?.takeIf { it.isFile }

    fun productIconFile(appImageDir: File?, size: Int): File? =
        appDir(appImageDir)?.let { File(it, "$ICON_PREFIX$size.png") }?.takeIf { it.isFile }

    /** 产品 logo；没有就返回 null（界面会退回内置 logo） */
    fun productLogo(appImageDir: File?): ImageBitmap? =
        productLogoFile(appImageDir)?.let { readBitmap(it) }

    /** 有没有注入过产品图标（诊断页显示用） */
    fun hasProductIcons(appImageDir: File?, sizes: List<Int> = DEFAULT_ICON_SIZES): Boolean =
        sizes.any { productIconFile(appImageDir, it) != null }

    /**
     * 窗口 / 任务栏图标（AWT Image 列表，直接喂给 `window.iconImages`）。
     * 产品素材优先；某个尺寸没注入就用内置资源补上，保证不出现 Java 咖啡杯。
     */
    fun windowIconImages(appImageDir: File?, sizes: List<Int> = DEFAULT_ICON_SIZES): List<Image> =
        sizes.mapNotNull { size ->
            productIconFile(appImageDir, size)?.let { readAwtImage(it) }
                ?: readClasspathImage("/app-icon-$size.png")
        }

    private fun readBitmap(file: File): ImageBitmap? =
        runCatching { ImageIO.read(file)?.toComposeImageBitmap() }.getOrNull()

    private fun readAwtImage(file: File): Image? = runCatching { ImageIO.read(file) }.getOrNull()

    fun readClasspathImage(path: String): Image? = runCatching {
        object {}.javaClass.getResourceAsStream(path)?.use { ImageIO.read(it) }
    }.getOrNull()
}
