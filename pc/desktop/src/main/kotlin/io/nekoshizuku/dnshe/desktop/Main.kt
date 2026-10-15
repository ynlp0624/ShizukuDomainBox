package io.nekoshizuku.dnshe.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.nekoshizuku.dnshe.core.AppCore
import io.nekoshizuku.dnshe.core.ThemeMode
import io.nekoshizuku.dnshe.desktop.Sys
import io.nekoshizuku.dnshe.desktop.i18n.Str
import io.nekoshizuku.dnshe.desktop.i18n.t
import io.nekoshizuku.dnshe.desktop.ui.AppRoot
import io.nekoshizuku.dnshe.desktop.ui.InstallWizard
import io.nekoshizuku.dnshe.desktop.ui.ShizukuTheme
import io.nekoshizuku.dnshe.desktop.ui.UninstallWizard
import java.awt.Dimension
import java.awt.Image
import java.io.File
import javax.imageio.ImageIO

/**
 * 窗口 / 任务栏图标用的多尺寸图。
 *
 * 注意：jpackage 的 --icon 只决定 **exe 文件本身** 的图标，
 * 窗口标题栏和任务栏的图标必须由程序运行时自己设置，
 * 不设置就会显示 Java 默认的咖啡杯。
 */
private val WINDOW_ICON_SIZES = listOf(16, 20, 24, 32, 40, 48, 64, 96, 128, 256)

private val windowIconImages: List<Image> by lazy {
    WINDOW_ICON_SIZES.mapNotNull { size ->
        runCatching {
            object {}.javaClass.getResourceAsStream("/app-icon-$size.png")?.use { ImageIO.read(it) }
        }.getOrNull()
    }
}

/** 在窗口首次显示后把多尺寸图标塞进去（Windows 会按场景挑合适的那张） */
@Composable
private fun ApplyWindowIcon(window: java.awt.Window) {
    LaunchedEffect(Unit) {
        if (windowIconImages.isNotEmpty()) {
            runCatching { window.iconImages = windowIconImages }
        }
    }
}

fun main(args: Array<String>) = application {
    // 安装程序可能带 --data-dir=... 启动
    args.firstOrNull { it.startsWith("--data-dir=") }?.let {
        System.setProperty("dnshe.dataDir", it.removePrefix("--data-dir="))
    }

    // 卸载模式：Uninstall.exe = 主程序 exe 的副本 + app\Uninstall.cfg 里的 --uninstall
    val exeName = System.getProperty("jpackage.app-path")?.let { File(it).name } ?: ""
    val uninstallMode = args.any { it.equals("--uninstall", true) } ||
        exeName.startsWith("Uninstall", ignoreCase = true)
    // 安装模式：Setup.exe = 主程序 exe 的副本 + app\Setup.cfg 里的 --install
    val installMode = args.any { it.equals("--install", true) } ||
        exeName.startsWith("Setup", ignoreCase = true)

    val dialogIcon = painterResource("app-icon-64.png")

    if (uninstallMode) {
        Window(
            onCloseRequest = { exitApplication() },
            title = "卸载 雫的域名盒",
            icon = dialogIcon,
            state = rememberWindowState(width = 680.dp, height = 540.dp, position = WindowPosition(Alignment.Center)),
            resizable = false,
        ) {
            ApplyWindowIcon(window)
            Sys.mainWindow = window
            ShizukuTheme(ThemeMode.LIGHT) {
                UninstallWizard(args.toList())
            }
        }
        return@application
    }

    if (installMode) {
        Window(
            onCloseRequest = { kotlin.system.exitProcess(0) },
            title = "安装 雫的域名盒",
            icon = dialogIcon,
            state = rememberWindowState(width = 840.dp, height = 600.dp, position = WindowPosition(Alignment.Center)),
            resizable = false,
        ) {
            ApplyWindowIcon(window)
            Sys.mainWindow = window
            ShizukuTheme(ThemeMode.LIGHT) {
                InstallWizard(args.toList())
            }
        }
        return@application
    }

    val settings = AppCore.settings
    val initial = remember { settings.current }

    val windowState = rememberWindowState(
        width = initial.windowWidth.dp,
        height = initial.windowHeight.dp,
        position = WindowPosition(Alignment.Center),
    )

    Window(
        onCloseRequest = {
            // 记住窗口大小
            runCatching {
                settings.update {
                    it.copy(
                        windowWidth = windowState.size.width.value.toInt(),
                        windowHeight = windowState.size.height.value.toInt(),
                    )
                }
            }
            exitApplication()
        },
        state = windowState,
        title = t(Str.AppName) + " · PC",
        icon = dialogIcon,
    ) {
        ApplyWindowIcon(window)
        Sys.mainWindow = window
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(980, 640)
        }
        AppRoot()
    }
}

private typealias Alignment = androidx.compose.ui.Alignment
