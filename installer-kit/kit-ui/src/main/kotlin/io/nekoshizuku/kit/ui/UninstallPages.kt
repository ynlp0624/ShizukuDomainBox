package io.nekoshizuku.kit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.nekoshizuku.kit.InstallerConfig
import io.nekoshizuku.kit.KitArgs
import io.nekoshizuku.kit.KitSelf
import io.nekoshizuku.kit.UninstallEngine
import io.nekoshizuku.kit.WinPaths
import io.nekoshizuku.kit.WinReg
import io.nekoshizuku.kit.ui.i18n.Lang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class UStep { OPTIONS, RUNNING, DONE }

/**
 * 卸载 / 修复向导（可直接用的入口）。
 *
 * 由 `Uninstall.exe`（主程序 exe 的副本 + `app\Uninstall.cfg` 里的 `--uninstall`）启动，
 * 所以能直接复用产品自带的运行时，不额外占空间。
 */
@Composable
fun UninstallWizard(
    config: InstallerConfig,
    args: KitArgs,
    appImageDir: File? = KitSelf.appImageDir,
    lang: Lang = Lang.detect(args.lang),
) {
    KitWizard(config, args, appImageDir, lang) { UninstallWizardBody() }
}

@Composable
private fun UninstallWizardBody() {
    val ctx = LocalWizard.current
    val config = ctx.config
    val cli = ctx.args
    val S = ctx.strings

    val paths = remember(config) { WinPaths(config) }
    val engine = remember(config) { UninstallEngine(config) }
    val reg = remember(config) { WinReg(paths) }

    val perUser = remember {
        when {
            reg.readInstallPath(true) != null -> true
            reg.readInstallPath(false) != null -> false
            else -> config.defaultPerUser
        }
    }
    val installDir = remember {
        reg.readInstallPath(perUser)?.let { File(it) }
            ?: reg.readInstallPath(!perUser)?.let { File(it) }
            ?: KitSelf.appImageDir
            ?: paths.defaultInstallDir(perUser)
    }
    val dataDir = remember { cli.dataDirOverride ?: paths.dataDir() }

    var step by remember { mutableStateOf(if (cli.silent) UStep.RUNNING else UStep.OPTIONS) }
    var repair by remember { mutableStateOf(false) }
    // 静默模式下 /KEEPDATA 是唯一依据 —— 命令行开关必须说了算，
    // 否则 `/S`（不保留）和 `/S /KEEPDATA`（保留）就没区别了。
    // 那个 uninstallKeepDataByDefault 只管交互式界面默认勾哪一个。
    var keepData by remember {
        mutableStateOf(if (cli.silent) cli.keepData else (cli.keepData || config.uninstallKeepDataByDefault))
    }
    var progress by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf(S.uninstallWorking) }
    var error by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()

    fun run() {
        step = UStep.RUNNING
        progress = 0
        error = null
        coroutineScope.launch {
            val message = withContext(Dispatchers.IO) {
                if (repair) {
                    engine.runRepair(installDir, perUser) { p, s -> progress = p; status = s }
                } else {
                    engine.runUninstall(installDir, perUser, keepData, dataDir) { p, s ->
                        progress = p; status = s
                    }
                }
            }
            error = message
            step = UStep.DONE
            if (cli.silent) {
                // 静默模式立刻退出，好让延迟批处理能删掉目录
                kotlinx.coroutines.delay(200)
                kotlin.system.exitProcess(if (message == null) 0 else 2)
            }
        }
    }

    if (cli.silent) LaunchedEffect(Unit) { run() }

    SimpleFrame(
        title = if (repair) S.uninstallRepairTitle else S.uninstallTitle,
        content = {
            when (step) {
                UStep.OPTIONS -> {
                    InfoLine(S.uninstallInstallDir, installDir.absolutePath, 84.dp)
                    InfoLine(S.uninstallDataDir, dataDir.absolutePath, 84.dp)
                    Spacer(Modifier.height(18.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OptRow(
                            selected = !repair && keepData,
                            title = S.uninstallKeepData,
                            desc = S.uninstallKeepDataDesc.fill("dataDir" to dataDir.absolutePath),
                        ) { repair = false; keepData = true }
                        OptRow(
                            selected = !repair && !keepData,
                            title = S.uninstallRemoveAll,
                            desc = S.uninstallRemoveAllDesc,
                        ) { repair = false; keepData = false }
                        OptRow(
                            selected = repair,
                            title = S.uninstallRepair,
                            desc = S.uninstallRepairDesc,
                        ) { repair = true }
                    }
                }

                UStep.RUNNING -> {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("$progress%   $status", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                UStep.DONE -> {
                    if (error != null) {
                        Text(
                            S.progressErrorTitle,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(8.dp))
                        ErrorBox(error!!)
                    } else {
                        Text(
                            if (repair) S.uninstallDoneRepair else S.uninstallDone,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(8.dp))
                        if (!repair && keepData) {
                            Text(S.uninstallDataKept.fill("dataDir" to dataDir.absolutePath), fontSize = 12.sp)
                        }
                        if (!repair) {
                            Hint(S.uninstallDirHint)
                        }
                    }
                }
            }
        },
        bottom = {
            Spacer(Modifier.weight(1f))
            when (step) {
                UStep.OPTIONS -> {
                    OutlinedButton(onClick = { kotlin.system.exitProcess(0) }) { Text(S.cancel) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { run() }) { Text(if (repair) S.startRepair else S.startUninstall) }
                }
                UStep.RUNNING -> Unit
                UStep.DONE -> Button(onClick = { kotlin.system.exitProcess(0) }) { Text(S.close) }
            }
        },
    )
}
