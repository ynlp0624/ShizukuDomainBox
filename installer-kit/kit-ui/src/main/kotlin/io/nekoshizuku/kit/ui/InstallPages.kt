package io.nekoshizuku.kit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.nekoshizuku.kit.DirProblem
import io.nekoshizuku.kit.DiskSpace
import io.nekoshizuku.kit.ExistingInstall
import io.nekoshizuku.kit.InstallEngine
import io.nekoshizuku.kit.InstallerConfig
import io.nekoshizuku.kit.KitArgs
import io.nekoshizuku.kit.KitSelf
import io.nekoshizuku.kit.TempCleanup
import io.nekoshizuku.kit.WinPaths
import io.nekoshizuku.kit.WinReg
import io.nekoshizuku.kit.ui.i18n.Lang
import io.nekoshizuku.kit.ui.i18n.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 安装向导的步骤。许可协议 / 已经装过 这两步会按情况自动跳过。
 *
 * 「已经装过」刻意排在欢迎页**后面第一个**：升级场景下一进来就该知道
 * 「这台机器上已经有一份了」，比翻过许可协议才看到要合理。
 */
enum class InstallStep { WELCOME, EXISTING, LICENSE, SCOPE, PATH, OPTIONS, PROGRESS, FINISH }

private fun stepsOf(config: InstallerConfig, hasExisting: Boolean): List<InstallStep> =
    InstallStep.entries.filter { step ->
        when (step) {
            InstallStep.LICENSE -> config.hasLicense
            InstallStep.EXISTING -> hasExisting
            else -> true
        }
    }

private fun labelOf(step: InstallStep, S: Strings): String = when (step) {
    InstallStep.WELCOME -> S.stepWelcome
    InstallStep.EXISTING -> S.stepExisting
    InstallStep.LICENSE -> S.stepLicense
    InstallStep.SCOPE -> S.stepScope
    InstallStep.PATH -> S.stepPath
    InstallStep.OPTIONS -> S.stepOptions
    InstallStep.PROGRESS -> S.stepInstalling
    InstallStep.FINISH -> S.stepFinish
}

/**
 * 安装向导（可直接用的入口）。
 *
 * 用法就一行：
 * ```kotlin
 * KitTheme { InstallWizard(config, KitArgs(args)) }
 * ```
 */
@Composable
fun InstallWizard(
    config: InstallerConfig,
    args: KitArgs,
    appImageDir: File? = KitSelf.appImageDir,
    lang: Lang = Lang.detect(args.lang),
) {
    KitWizard(config, args, appImageDir, lang) { InstallWizardBody() }
}

@Composable
private fun InstallWizardBody() {
    val ctx = LocalWizard.current
    val config = ctx.config
    val cli = ctx.args
    val S = ctx.strings

    val paths = remember(config) { WinPaths(config) }
    val engine = remember(config) { InstallEngine(config) }
    val admin = remember { WinReg.isAdmin() }
    val silent = cli.silent

    // 覆盖安装检测：查一次注册表就够（开机时查，向导里不再重复查）
    val existing = remember { engine.detectExisting() }
    val steps = remember(config, existing) { stepsOf(config, existing != null) }
    val progressIndex = steps.indexOf(InstallStep.PROGRESS)
    val perUserDefault = cli.scopeAll?.let { !it } ?: config.defaultPerUser
    val requiredMb = remember(config) { InstallEngine.requiredMb(config) }

    var stepIndex by remember { mutableStateOf(if (silent) progressIndex else 0) }
    var accepted by remember { mutableStateOf(!config.hasLicense) }
    var perUser by remember { mutableStateOf(perUserDefault) }
    var installDir by remember { mutableStateOf(cli.installDir ?: paths.defaultInstallDir(perUserDefault)) }
    // 用户手动改过目录之后，就不该再被「切换安装范围」悄悄改回去了
    var dirTouched by remember { mutableStateOf(cli.installDir != null) }
    var upgradeExisting by remember { mutableStateOf(existing != null) }
    var desktopShortcut by remember { mutableStateOf(config.defaultDesktopShortcut && !cli.noDesktopShortcut) }
    var startMenu by remember { mutableStateOf(config.defaultStartMenu && !cli.noStartMenu) }
    var launchAfter by remember { mutableStateOf(config.launchAfterInstall && !cli.noLaunch && !silent) }
    var progress by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf(S.progressPreparing) }
    var error by remember { mutableStateOf<String?>(null) }

    // 目标盘还剩多少：跟着目录变重算（就是个 statfs，很便宜）
    val diskSpace = remember(installDir.absolutePath, requiredMb) {
        InstallEngine.diskSpace(installDir, requiredMb)
    }

    val coroutineScope = rememberCoroutineScope()

    /** 退出前顺手把 %TEMP% 里 IExpress 解压出来的那一坨排进清理队列 */
    fun finishAndExit(code: Int) {
        ctx.appImageDir?.let { TempCleanup.scheduleIfTemp(it) }
        kotlin.system.exitProcess(code)
    }

    fun startInstall() {
        stepIndex = steps.indexOf(InstallStep.PROGRESS)
        progress = 0
        error = null
        status = S.progressPreparing
        coroutineScope.launch {
            val source = ctx.appImageDir
            val message = withContext(Dispatchers.IO) {
                if (source == null) {
                    S.diagNoImage
                } else {
                    engine.run(
                        source,
                        InstallEngine.Options(installDir, perUser, desktopShortcut, startMenu),
                    ) { p, s -> progress = p; status = s }
                }
            }
            if (message != null) {
                error = message
                if (silent) kotlin.system.exitProcess(2)
            } else {
                stepIndex = steps.indexOf(InstallStep.FINISH)
                if (silent) finishAndExit(0)
            }
        }
    }

    if (silent) LaunchedEffect(Unit) { startInstall() }

    /** 把「已经装过」那一步的选择落实：升级到原位 = 跟着旧安装的范围和目录走 */
    fun applyExistingChoice() {
        val ex = existing ?: return
        if (upgradeExisting) {
            perUser = ex.perUser
            installDir = ex.installDir
            dirTouched = true
        }
    }

    val current = steps[stepIndex.coerceIn(0, steps.lastIndex)]

    WizardFrame(
        steps = steps.map { WizardStep(labelOf(it, S)) },
        currentIndex = stepIndex,
        content = {
            when (current) {
                InstallStep.WELCOME -> WelcomePage()
                InstallStep.LICENSE -> LicensePage(accepted) { accepted = it }
                InstallStep.EXISTING -> ExistingPage(existing!!, upgradeExisting) { upgradeExisting = it }
                InstallStep.SCOPE -> ScopePage(perUser, admin) { newScope ->
                    perUser = newScope
                    // 用户显式改了安装范围 = 想要一份普通安装，覆盖掉「升级到原位」的选择。
                    // 命令行指定过目录的话就不动它。
                    if (cli.installDir == null) {
                        dirTouched = false
                        upgradeExisting = false
                        installDir = paths.defaultInstallDir(newScope)
                    }
                }
                InstallStep.PATH -> PathPage(installDir, cli.installDir != null, existing, diskSpace) {
                    installDir = it
                    dirTouched = true
                }
                InstallStep.OPTIONS -> OptionsPage(desktopShortcut, startMenu, launchAfter) { d, s, l ->
                    desktopShortcut = d; startMenu = s; launchAfter = l
                }
                InstallStep.PROGRESS -> ProgressPage(progress, status, error)
                InstallStep.FINISH -> FinishPage(installDir, launchAfter)
            }

            // 校验类错误要当场看得见（之前只有进度页会显示，等于白校验）
            if (error != null && current != InstallStep.PROGRESS) {
                Spacer(Modifier.height(14.dp))
                ErrorBox(error!!)
            }
        },
        bottom = {
            Text(
                when (current) {
                    InstallStep.PROGRESS -> status
                    InstallStep.FINISH -> "${S.finishInstallDir}：${installDir.absolutePath}"
                    else -> S.stepOf(stepIndex + 1, steps.size)
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )

            val busy = current == InstallStep.PROGRESS || current == InstallStep.FINISH
            if (!busy) {
                TextButton(onClick = { finishAndExit(0) }) { Text(S.cancel) }
                Spacer(Modifier.width(6.dp))
            }
            if (stepIndex > 0 && !busy) {
                OutlinedButton(onClick = { stepIndex -= 1 }) { Text(S.back) }
                Spacer(Modifier.width(6.dp))
            }

            when (current) {
                InstallStep.LICENSE -> Button(
                    onClick = { stepIndex += 1 },
                    enabled = accepted,
                ) { Text(S.next) }

                InstallStep.PATH -> Button(onClick = {
                    val problem = InstallEngine.checkInstallDir(installDir)
                    when {
                        problem != null -> error = dirProblemText(problem, installDir, S)
                        diskSpace != null && !diskSpace.ok -> error = S.pathSpaceNotEnough.fill(
                            "availableMb" to diskSpace.availableMb.toString(),
                            "requiredMb" to diskSpace.requiredMb.toString(),
                        )
                        else -> { error = null; stepIndex += 1 }
                    }
                }) { Text(S.next) }

                InstallStep.EXISTING -> Button(onClick = {
                    applyExistingChoice()
                    error = null
                    stepIndex += 1
                }) { Text(S.next) }

                InstallStep.OPTIONS -> Button(onClick = {
                    if (!perUser && !admin) {
                        if (relaunchElevated(installDir)) kotlin.system.exitProcess(0)
                        else error = S.scopeNotAdmin
                    } else {
                        startInstall()
                    }
                }) { Text(S.install) }

                InstallStep.FINISH -> Button(onClick = {
                    if (launchAfter) {
                        runCatching {
                            ProcessBuilder(paths.appExeIn(installDir).absolutePath)
                                .directory(installDir)
                                .start()
                        }
                    }
                    finishAndExit(0)
                }) { Text(S.finish) }

                InstallStep.PROGRESS -> Unit

                InstallStep.WELCOME, InstallStep.SCOPE -> Button(onClick = {
                    error = null
                    stepIndex += 1
                }) { Text(S.next) }
            }
        },
    )
}

private fun dirProblemText(problem: DirProblem, dir: File, S: Strings): String =
    when (problem) {
        DirProblem.EMPTY -> S.pathErrEmpty
        DirProblem.ROOT -> S.pathErrRoot
        DirProblem.NOT_A_DIR -> S.pathErrGeneric
        DirProblem.ILLEGAL_CHARS -> S.pathErrIllegal.fill("name" to dir.name)
    }

/** 用管理员身份重新启动自己（`/SCOPE=all /DIR=...`），用户点掉 UAC 就返回 false */
private fun relaunchElevated(dir: File): Boolean = runCatching {
    val exe = KitSelf.exePath ?: return false
    val cmd = "Start-Process -FilePath '${exe.replace("'", "''")}' -Verb RunAs -ArgumentList " +
        "'/SCOPE=all','/DIR=${dir.absolutePath.replace("'", "''")}'"
    ProcessBuilder("powershell", "-NoProfile", "-WindowStyle", "Hidden", "-Command", cmd).start()
    true
}.getOrDefault(false)

/* ============================== 页面 ============================== */

@Composable
fun WelcomePage() {
    val ctx = LocalWizard.current
    val S = ctx.strings
    Column {
        Title(S.welcomeTitle)
        Spacer(Modifier.height(10.dp))
        Body(S.welcomeIntro)
        if (ctx.config.publisher.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Hint(S.welcomePublisher)
        }
        Spacer(Modifier.height(16.dp))
        SectionTitle(S.welcomeCheckTitle)
        Spacer(Modifier.height(4.dp))
        S.welcomeBullets.forEach { bullet ->
            Text("  ·  $bullet", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun LicensePage(accepted: Boolean, onAccepted: (Boolean) -> Unit) {
    val ctx = LocalWizard.current
    val S = ctx.strings
    Column {
        Title(S.licenseTitle)
        Spacer(Modifier.height(8.dp))
        ScrollBox(320.dp) {
            val licenseText = ctx.config.licenseText.ifBlank { S.licenseNone }
            Text(licenseText, fontSize = 11.5.sp, lineHeight = 17.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = accepted, onCheckedChange = onAccepted)
            Text(S.licenseAccept, fontSize = 13.sp)
        }
    }
}

@Composable
fun ScopePage(perUser: Boolean, admin: Boolean, onChange: (Boolean) -> Unit) {
    val ctx = LocalWizard.current
    val S = ctx.strings
    val paths = remember(ctx.config) { WinPaths(ctx.config) }
    val perUserPath = remember(paths) { paths.defaultInstallDir(true).absolutePath }
    val allPath = remember(paths) { paths.defaultInstallDir(false).absolutePath }

    Column {
        Title(S.scopeTitle)
        Spacer(Modifier.height(10.dp))
        OptRow(
            selected = perUser,
            title = S.scopePerUser,
            desc = S.scopePerUserDesc.fill("path" to perUserPath),
            onClick = { onChange(true) },
        )
        Spacer(Modifier.height(8.dp))
        OptRow(
            selected = !perUser,
            title = S.scopeAll,
            desc = if (admin) S.scopeAllDescAdmin.fill("path" to allPath)
            else S.scopeAllDescUac.fill("path" to allPath),
            onClick = { onChange(false) },
        )
        Spacer(Modifier.height(12.dp))
        Hint(if (admin) S.scopeIsAdmin else S.scopeNotAdmin)
    }
}

@Composable
fun PathPage(
    dir: File,
    dirLocked: Boolean,
    existing: ExistingInstall?,
    space: DiskSpace?,
    onChange: (File) -> Unit,
) {
    val ctx = LocalWizard.current
    val S = ctx.strings
    val paths = remember(ctx.config) { WinPaths(ctx.config) }

    Column {
        Title(S.pathTitle)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = dir.absolutePath,
                onValueChange = { onChange(File(it)) },
                singleLine = true,
                enabled = !dirLocked,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
            )
            if (!dirLocked) {
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = {
                    runCatching {
                        val chooser = javax.swing.JFileChooser().apply {
                            fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
                            dialogTitle = S.pathTitle
                        }
                        if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
                            onChange(File(chooser.selectedFile, paths.installDirName))
                        }
                    }
                }) { Text(S.browse) }
            }
        }
        Spacer(Modifier.height(14.dp))
        InfoLine(S.pathSpace, S.pathSpaceValue.fill("sizeMb" to ctx.config.estimatedSizeMb.toString()))
        if (space != null) {
            InfoLine(
                S.pathSpaceAvailable,
                S.pathSpaceAvailableValue.fill("availableMb" to space.availableMb.toString()),
            )
        }
        InfoLine(S.pathInstallDir, dir.absolutePath)
        InfoLine(S.pathDataDir, S.pathDataDirValue.fill("dataDir" to paths.dataDir().absolutePath))

        // 装到旧安装的位置 = 覆盖升级，提前说清楚
        if (existing != null && sameDir(dir, existing.installDir)) {
            Spacer(Modifier.height(10.dp))
            Hint(
                S.pathWillUpgrade.fill(
                    "oldVersion" to (existing.version ?: "?"),
                    "newVersion" to ctx.config.effectiveVersion,
                ),
            )
        }

        if (space != null && !space.ok) {
            Spacer(Modifier.height(10.dp))
            ErrorBox(
                S.pathSpaceNotEnough.fill(
                    "availableMb" to space.availableMb.toString(),
                    "requiredMb" to space.requiredMb.toString(),
                ),
            )
        }

        Spacer(Modifier.height(10.dp))
        Hint(S.pathHint)
    }
}

/** 两个路径是不是同一个目录（注册表里的路径和用户手输的可能写法不同） */
private fun sameDir(a: File, b: File): Boolean =
    runCatching { a.canonicalPath.equals(b.canonicalPath, ignoreCase = true) }.getOrDefault(false)

/**
 * 「这台电脑上已经装过」那一步。
 *
 * 覆盖升级是默认选项：绝大多数情况下用户就是来升级的，
 * 而且我们的安装是「整个目录替换」，用户数据本来就在 %APPDATA% 里不受影响。
 */
@Composable
fun ExistingPage(existing: ExistingInstall, upgrade: Boolean, onChange: (Boolean) -> Unit) {
    val ctx = LocalWizard.current
    val S = ctx.strings
    val paths = remember(ctx.config) { WinPaths(ctx.config) }

    Column {
        Title(S.existingTitle)
        Spacer(Modifier.height(10.dp))
        Body(
            S.existingIntro.fill(
                "oldVersion" to ("v" + (existing.version ?: "?")),
                "oldDir" to existing.installDir.absolutePath,
            ),
        )
        Spacer(Modifier.height(16.dp))
        OptRow(
            selected = upgrade,
            title = S.existingUpgrade,
            desc = S.existingUpgradeDesc.fill(
                "oldDir" to existing.installDir.absolutePath,
                "dataDir" to paths.dataDir().absolutePath,
            ),
            onClick = { onChange(true) },
        )
        Spacer(Modifier.height(8.dp))
        OptRow(
            selected = !upgrade,
            title = S.existingFresh,
            desc = S.existingFreshDesc,
            onClick = { onChange(false) },
        )
        Spacer(Modifier.height(12.dp))
        Hint(S.existingHint)
    }
}

@Composable
fun OptionsPage(desktop: Boolean, startMenu: Boolean, launchAfter: Boolean, onChange: (Boolean, Boolean, Boolean) -> Unit) {
    val S = wizardStrings
    Column {
        Title(S.optionsTitle)
        Spacer(Modifier.height(10.dp))
        CheckRow(desktop, S.optionsDesktop) { onChange(it, startMenu, launchAfter) }
        CheckRow(startMenu, S.optionsStartMenu) { onChange(desktop, it, launchAfter) }
        Spacer(Modifier.height(16.dp))
        Title(S.optionsAfterTitle)
        Spacer(Modifier.height(8.dp))
        CheckRow(launchAfter, S.optionsLaunch) { onChange(desktop, startMenu, it) }
        Spacer(Modifier.height(16.dp))
        Hint(S.optionsHint)
    }
}

@Composable
fun ProgressPage(progress: Int, status: String, error: String?) {
    val S = wizardStrings
    Column {
        Title(if (error == null) S.progressTitle else S.progressErrorTitle)
        Spacer(Modifier.height(18.dp))
        LinearProgressIndicator(
            progress = { progress / 100f },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Text("$progress%   $status", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (error != null) {
            Spacer(Modifier.height(16.dp))
            ErrorBox(error)
        }
    }
}

@Composable
fun FinishPage(installDir: File, launchAfter: Boolean) {
    val ctx = LocalWizard.current
    val S = ctx.strings
    val paths = remember(ctx.config) { WinPaths(ctx.config) }

    Column {
        Title(S.finishTitle)
        Spacer(Modifier.height(12.dp))
        Body(S.finishIntro)
        Spacer(Modifier.height(14.dp))
        InfoLine(S.finishInstallDir, installDir.absolutePath)
        InfoLine(S.finishUninstaller, paths.uninstallExeIn(installDir).absolutePath)
        InfoLine(S.finishDataDir, paths.dataDir().absolutePath)
        Spacer(Modifier.height(14.dp))
        Hint(if (launchAfter) S.finishLaunchHint else S.finishCloseHint)
    }
}
