package io.nekoshizuku.dnshe.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.nekoshizuku.dnshe.core.WinPaths
import io.nekoshizuku.dnshe.core.WinReg
import io.nekoshizuku.dnshe.core.WinUninstall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class Step { OPTIONS, RUNNING, DONE }

/**
 * 卸载 / 修复向导。
 * 由 Uninstall.exe（主程序 exe 的副本 + app\Uninstall.cfg 里的 --uninstall 参数）启动，
 * 所以能直接复用主程序自带的运行时，不额外占空间。
 */
@Composable
fun UninstallWizard(args: List<String>) {
    val silent = args.any { it.equals("/S", true) }
    val keepDataArg = args.any { it.equals("/KEEPDATA", true) }

    val perUser = remember {
        when {
            WinReg.readInstallPath(true) != null -> true
            WinReg.readInstallPath(false) != null -> false
            else -> true
        }
    }
    val installDir = remember {
        val fromReg = WinReg.readInstallPath(perUser) ?: WinReg.readInstallPath(!perUser)
        fromReg?.let { File(it) }
            ?: System.getProperty("jpackage.app-path")?.let { File(it).parentFile }
            ?: WinPaths.defaultInstallDir(true)
    }

    var step by remember { mutableStateOf(if (silent) Step.RUNNING else Step.OPTIONS) }
    var repair by remember { mutableStateOf(false) }
    var keepData by remember { mutableStateOf(keepDataArg) }
    var progress by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf("准备…") }
    val scope = rememberCoroutineScope()

    fun run() {
        step = Step.RUNNING
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    if (repair) {
                        WinUninstall.repair(installDir, perUser) { p, s -> progress = p; status = s }
                    } else {
                        WinUninstall.uninstall(installDir, perUser, keepData) { p, s -> progress = p; status = s }
                    }
                }.onFailure { status = "出错：${it.message}" }
            }
            step = Step.DONE
            // 静默模式立刻退出，好让延迟脚本能删掉目录
            if (silent) {
                kotlinx.coroutines.delay(200)
                kotlin.system.exitProcess(0)
            }
        }
    }

    if (silent) LaunchedEffect(Unit) { run() }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize().padding(28.dp)) {
            AppLogo(52.dp)
            Spacer(Modifier.height(10.dp))
            Text(
                if (repair) "修复 ${WinPaths.DISPLAY_NAME}" else "卸载 ${WinPaths.DISPLAY_NAME}",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(16.dp))

            when (step) {
                Step.OPTIONS -> {
                    InfoRow("安装位置", installDir.absolutePath, mono = true)
                    InfoRow("数据目录", WinPaths.dataDir().absolutePath, mono = true)
                    Spacer(Modifier.height(18.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OptRow(
                            selected = !repair && keepData,
                            title = "卸载（保留账号数据）",
                            desc = "只删程序；账号 / 设置 / 日志留在 %APPDATA%\\ShizukuDomainBox，重装后还在",
                        ) { repair = false; keepData = true }
                        OptRow(
                            selected = !repair && !keepData,
                            title = "彻底卸载（同时删除账号数据）",
                            desc = "程序 + 账号 + 设置 + 日志全部删除，不可恢复",
                        ) { repair = false; keepData = false }
                        OptRow(
                            selected = repair,
                            title = "修复安装",
                            desc = "重建快捷方式和注册表项，账号数据不动",
                        ) { repair = true }
                    }
                }

                Step.RUNNING -> {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("$progress%   $status", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Step.DONE -> {
                    Text(
                        if (repair) "修复完成喵~" else "卸载完成，谢谢使用喵~",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(10.dp))
                    if (!repair && keepData) {
                        Text(
                            "账号数据还留在 ${WinPaths.dataDir().absolutePath}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                when (step) {
                    Step.OPTIONS -> {
                        OutlinedButton(onClick = { kotlin.system.exitProcess(0) }) { Text("取消") }
                        Button(onClick = { run() }) { Text(if (repair) "开始修复" else "开始卸载") }
                    }
                    Step.RUNNING -> Unit
                    Step.DONE -> Button(onClick = { kotlin.system.exitProcess(0) }) { Text("关闭") }
                }
            }
        }
    }
}

@Composable
private fun OptRow(selected: Boolean, title: String, desc: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(10.dp),
            )
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(6.dp))
        Column {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
