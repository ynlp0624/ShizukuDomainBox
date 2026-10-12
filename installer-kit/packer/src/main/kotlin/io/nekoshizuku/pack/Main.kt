package io.nekoshizuku.pack

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.nekoshizuku.pack.ui.PackerApp
import io.nekoshizuku.pack.ui.PackerMode
import io.nekoshizuku.pack.ui.SimpleApp
import io.nekoshizuku.pack.ui.newState
import java.awt.Image
import javax.imageio.ImageIO
import kotlin.system.exitProcess

/**
 * InstallerKit 打包器 —— 「带界面的打包工具」。
 *
 * 三种跑法：
 * - 不带参数：开**简单版**窗口（给新手：选程序 → 填名字 → 打包）
 * - `--pro`：开**专业版**窗口（所有字段都能改）
 * - `--headless ...`：无界面跑完（CI / 脚本 / 自检）
 *
 * 两个界面共用同一份状态和同一份配置，点右上角随时对切，不会丢东西。
 */
fun main(args: Array<String>) {
    val cli = PackerArgs(args.toList())

    if (cli.headless) {
        exitProcess(PackerCli.run(cli) { println(it) })
    }

    application {
        var mode by remember { mutableStateOf(if (cli.proMode) PackerMode.PRO else PackerMode.SIMPLE) }
        val state = remember { newState() }
        val windowState = rememberWindowState(position = WindowPosition(Alignment.Center), size = sizeOf(mode))

        LaunchedEffect(mode) { windowState.size = sizeOf(mode) }

        Window(
            onCloseRequest = ::exitApplication,
            title = when (mode) {
                PackerMode.SIMPLE -> "InstallerKit 打包器（简单版）"
                PackerMode.PRO -> "InstallerKit 打包器（专业版）"
            },
            state = windowState,
        ) {
            LaunchedEffect(Unit) { window.minimumSize = java.awt.Dimension(880, 660) }
            ApplyWindowIcon(window)
            when (mode) {
                PackerMode.SIMPLE -> SimpleApp(state) { mode = it }
                PackerMode.PRO -> PackerApp(state) { mode = it }
            }
        }
    }
}

private fun sizeOf(mode: PackerMode): DpSize = when (mode) {
    PackerMode.SIMPLE -> DpSize(940.dp, 800.dp)
    PackerMode.PRO -> DpSize(1200.dp, 820.dp)
}

private val WINDOW_ICON_SIZES = listOf(16, 20, 24, 32, 40, 48, 64, 96, 128, 256)

private val windowIcons: List<Image> by lazy {
    WINDOW_ICON_SIZES.mapNotNull { size ->
        runCatching {
            object {}.javaClass.getResourceAsStream("/app-icon-$size.png")?.use { ImageIO.read(it) }
        }.getOrNull()
    }
}

@androidx.compose.runtime.Composable
private fun ApplyWindowIcon(window: java.awt.Window) {
    LaunchedEffect(Unit) {
        if (windowIcons.isNotEmpty()) runCatching { window.iconImages = windowIcons }
    }
}
