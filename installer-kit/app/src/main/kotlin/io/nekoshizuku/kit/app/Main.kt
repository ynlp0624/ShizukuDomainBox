package io.nekoshizuku.kit.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.nekoshizuku.kit.InstallerConfigs
import io.nekoshizuku.kit.KitArgs
import io.nekoshizuku.kit.KitSelf
import io.nekoshizuku.kit.ModeDetector
import io.nekoshizuku.kit.RunMode
import io.nekoshizuku.kit.ui.DiagnosticsPage
import io.nekoshizuku.kit.ui.InstallWizard
import io.nekoshizuku.kit.ui.KitAssets
import io.nekoshizuku.kit.ui.KitTheme
import io.nekoshizuku.kit.ui.KitBrand
import io.nekoshizuku.kit.ui.UninstallWizard
import io.nekoshizuku.kit.ui.i18n.Lang

/**
 * 安装器壳的入口 —— 「三种身份共用一个入口」。
 *
 * | 启动方式 | 模式 | 干什么 |
 * |---|---|---|
 * | `Setup.exe` / `--install` | 安装 | 向导 → 把整个 app-image 复制到目标目录 |
 * | `Uninstall.exe` / `--uninstall` | 卸载 | 保留数据 / 彻底删除 / 修复 |
 * | 其它 | 诊断 | 显示「配置到底读到了没有」 |
 *
 * 产品的 `MyApp.cfg` 里 mainclass 还是产品自己的，
 * 只有 `Setup.cfg` / `Uninstall.cfg` 把 mainclass 指到了这里。
 *
 * 这个文件刻意保持很薄：界面全在 kit-ui，逻辑全在 kit-core。
 */
fun main(args: Array<String>) = application {
    val cli = KitArgs(args.toList())
    cli.dataDirOverride?.let { System.setProperty("kit.dataDir", it.absolutePath) }

    val mode = ModeDetector.detect(args.toList())
    val loaded = InstallerConfigs.loadForAppImage(KitSelf.appImageDir)
    val config = loaded.config
    val appImageDir = KitSelf.appImageDir
    val lang = Lang.detect(cli.lang)
    val brand = KitBrand.parse(config.brandColor) ?: KitBrand.Default

    // 窗口 / 任务栏图标：优先用打包期注入的产品图标，没有就退回 kit 自带的
    val windowIcons = KitAssets.windowIconImages(appImageDir, config.windowIconSizes)

    when (mode) {
        RunMode.INSTALL -> Window(
            onCloseRequest = { kotlin.system.exitProcess(0) },
            title = installTitle(config.productName, lang),
            state = rememberWindowState(width = 900.dp, height = 640.dp, position = WindowPosition(Alignment.Center)),
            resizable = false,
        ) {
            ApplyWindowIcon(window, windowIcons)
            KitTheme(brand) { InstallWizard(config, cli, appImageDir, lang) }
        }

        RunMode.UNINSTALL -> Window(
            onCloseRequest = { kotlin.system.exitProcess(0) },
            title = uninstallTitle(config.productName, lang),
            state = rememberWindowState(width = 760.dp, height = 560.dp, position = WindowPosition(Alignment.Center)),
            resizable = false,
        ) {
            ApplyWindowIcon(window, windowIcons)
            KitTheme(brand) { UninstallWizard(config, cli, appImageDir, lang) }
        }

        RunMode.NORMAL -> Window(
            onCloseRequest = { kotlin.system.exitProcess(0) },
            title = "InstallerKit",
            state = rememberWindowState(width = 800.dp, height = 600.dp, position = WindowPosition(Alignment.Center)),
        ) {
            ApplyWindowIcon(window, windowIcons)
            KitTheme(brand) {
                DiagnosticsPage(config, loaded.file?.absolutePath, loaded.error, cli, appImageDir, lang)
            }
        }
    }
}

/**
 * 窗口标题直接按语言拼，不走 kit-ui 的 Strings：
 * 窗口标题在 Compose 外面就定下来了，而 Strings 属于界面层。
 */
private fun installTitle(productName: String, lang: Lang): String =
    if (lang == Lang.ZH) "安装 $productName" else "Install $productName"

private fun uninstallTitle(productName: String, lang: Lang): String =
    if (lang == Lang.ZH) "卸载 $productName" else "Uninstall $productName"

/**
 * 把多尺寸图标塞进窗口（Windows 会按场景挑合适的那张）。
 *
 * ⚠️ jpackage 的 `--icon` 只管 exe 文件本身；窗口标题栏和任务栏的图标
 * 必须由程序运行时自己设置，不设置就是 Java 默认的咖啡杯。
 */
@Composable
private fun ApplyWindowIcon(window: java.awt.Window, icons: List<java.awt.Image>) {
    LaunchedEffect(icons) {
        if (icons.isNotEmpty()) {
            runCatching { window.iconImages = icons }
        }
    }
}

private typealias Alignment = androidx.compose.ui.Alignment
