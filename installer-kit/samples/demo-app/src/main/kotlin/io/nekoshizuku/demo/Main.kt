package io.nekoshizuku.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Image as AwtImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.imageio.ImageIO

/**
 * samples/demo-app —— InstallerKit 的**回归样本**。
 *
 * 它不是产品，是一块「用来把工具踩坏的地方」：
 * 故意做得和真实产品一样（自带运行时的 Compose Desktop app-image + 多尺寸窗口图标
 * + 往 %APPDATA% 写数据），这样「构建 → 组装 → 安装 → 卸载」整条链路都能验。
 *
 * 它**刻意不依赖 kit-core**：任意一个别人家的 jpackage app-image 就是这个样子。
 */

private val WINDOW_ICON_SIZES = listOf(16, 20, 24, 32, 40, 48, 64, 96, 128, 256)

private val windowIconImages: List<AwtImage> by lazy {
    WINDOW_ICON_SIZES.mapNotNull { size ->
        runCatching {
            object {}.javaClass.getResourceAsStream("/app-icon-$size.png")?.use { ImageIO.read(it) }
        }.getOrNull()
    }
}

private val DATA_DIR: File by lazy {
    File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "KitDemoBox")
}

private fun selfExe(): String =
    System.getProperty("jpackage.app-path") ?: "（不是从 app-image 启动的）"

private fun selfImageDir(): String =
    System.getProperty("jpackage.app-path")?.let { File(it).parentFile?.absolutePath } ?: "—"

fun main(args: Array<String>) = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "雫的演示盒 · InstallerKit 回归样本",
        state = rememberWindowState(width = 760.dp, height = 560.dp, position = WindowPosition(Alignment.Center)),
    ) {
        LaunchedEffect(Unit) {
            if (windowIconImages.isNotEmpty()) runCatching { window.iconImages = windowIconImages }
        }
        MaterialTheme { DemoBody() }
    }
}

@Composable
private fun DemoBody() {
    var nickname by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("雫的演示盒", fontSize = 22.sp)
        Text(
            "这是 InstallerKit 的回归样本，用来验证「构建 → 组装安装包 → 安装 → 卸载」整条链路。",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))

        Line("exe", selfExe())
        Line("app-image", selfImageDir())
        Line("窗口标题图标", if (windowIconImages.isEmpty()) "❌ 没加载到（可能显示成 Java 咖啡杯）" else "✅ 已加载 ${windowIconImages.size} 个尺寸")
        Line("数据目录", DATA_DIR.absolutePath)
        Line("启动参数", if (argsOf().isEmpty()) "（无）" else argsOf().joinToString(" "))

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it },
            label = { Text("随便输点字，验证界面是活的") },
            singleLine = true,
        )

        Spacer(Modifier.height(10.dp))
        Button(onClick = {
            message = runCatching {
                DATA_DIR.mkdirs()
                val f = File(DATA_DIR, "keep-me.txt")
                val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                f.appendText("$now  ${if (nickname.isBlank()) "(空)" else nickname}\r\n", Charsets.UTF_8)
                "已写入 ${f.absolutePath}"
            }.getOrElse { "写入失败：${it.message}" }
        }) { Text("往数据目录写一行字（卸载时用「保留数据」验它还在不在）") }

        if (message.isNotBlank()) {
            Text(message, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** 拿启动参数：jpackage 启动器不会塞进 ProcessHandle，只能从 sun.java.command 抠 */
private fun argsOf(): List<String> =
    runCatching {
        val cmd = System.getProperty("sun.java.command") ?: return emptyList()
        cmd.split(' ').drop(1).filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

@Composable
private fun Line(label: String, value: String) {
    androidx.compose.foundation.layout.Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 10.dp),
        )
        Text(value, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}
