package io.nekoshizuku.dnshe.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import top.yukonga.miuix.kmp.basic.Button
import androidx.compose.material3.Checkbox
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.nekoshizuku.dnshe.core.WinPaths
import io.nekoshizuku.dnshe.core.WinReg
import io.nekoshizuku.dnshe.core.WinShortcut
import io.nekoshizuku.dnshe.desktop.Sys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val BrandBlue = Color(0xFF3E6FD9)
private val BrandBlueDark = Color(0xFF1E3E8C)

private enum class IPage { WELCOME, LICENSE, SCOPE, PATH, OPTIONS, PROGRESS, FINISH }

private val LICENSE_TEXT = """
雫的域名盒 · 最终用户许可协议
================================

1. 这是什么
   本程序是一个「DNSHE 免费域名」的第三方管理工具，用于在本机管理你自己的
   DNSHE 账号、子域名和 DNS 解析记录。它不是 DNSHE 官方产品，与 DNSHE
   官方没有隶属关系。

2. 你的责任
   · 你需要自己准备 DNSHE 账号和 API 密钥。
   · 密钥保存在本机（用 Windows DPAPI 按当前用户加密），
     请自行保管好保管好电脑账号，不要把装有密钥的电脑借给不可信的人。
   · 你对使用本程序对自己账号所做的任何操作负责（包括删除域名、
     修改解析记录等不可撤销的操作）。

3. 数据与隐私
   本程序不收集、不上传任何遥测数据。所有数据只存在本机：
   · 程序文件：安装目录
   · 账号 / 设置 / 日志：%APPDATA%\ShizukuDomainBox
   唯一的外部网络请求是你自己账号的 DNSHE API 调用。

4. 免责
   本程序按「现状」提供，不附带任何明示或暗示的担保。因使用本程序
   造成的任何直接或间接损失（包括但不限于域名失效、解析中断、
   数据丢失），作者不承担责任。

5. 与手机版互通
   本程序的备份文件格式与安卓版完全一致，两边的 .json 可以互相导入导出。

点击「我接受」即表示你已阅读并同意以上条款。
""".trimIndent()

/**
 * 安装向导。
 *
 * 关键设计：Setup.exe 就是主程序 exe 的副本（同目录 + Setup.cfg 里带 --install），
 * 所以「安装」= 把正在运行的这套 app-image 整个复制到目标目录。
 * 不用另外塞一份 160MB 的 payload，也不会出现两个运行时。
 */
@Composable
fun InstallWizard(args: List<String>) {
    val cliScope = args.firstOrNull { it.startsWith("/SCOPE=") }?.removePrefix("/SCOPE=")
    val cliDir = args.firstOrNull { it.startsWith("/DIR=") }?.removePrefix("/DIR=")
    val silent = args.any { it.equals("/S", true) }

    var page by remember { mutableStateOf(if (silent) IPage.PROGRESS else IPage.WELCOME) }
    var accepted by remember { mutableStateOf(silent) }
    var perUser by remember { mutableStateOf(cliScope != "all") }
    var installDir by remember {
        mutableStateOf(cliDir?.let { File(it) } ?: WinPaths.defaultInstallDir(cliScope != "all"))
    }
    var desktopShortcut by remember { mutableStateOf(true) }
    var startMenu by remember { mutableStateOf(true) }
    var launchAfter by remember { mutableStateOf(!silent) }
    var progress by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf("准备安装…") }
    var error by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val admin = remember { WinReg.isAdmin() }

    fun doInstall(finishSilently: Boolean = false) {
        page = IPage.PROGRESS
        progress = 0
        error = null
        scope.launch {
            val fail = withContext(Dispatchers.IO) {
                runCatching {
                    val source = selfDir()
                        ?: return@runCatching "找不到程序自身目录（请从解压后的安装包里运行）"
                    if (source.canonicalPath == installDir.canonicalPath) {
                        return@runCatching "安装目录和当前运行目录相同，请换个位置"
                    }

                    status = "创建安装目录…"
                    progress = 3
                    if (installDir.exists()) installDir.deleteRecursively()
                    installDir.mkdirs()

                    status = "复制程序文件…"
                    copySelf(source, installDir) { p, name -> progress = p; status = "复制 $name" }

                    status = "创建卸载程序…"
                    progress = 84
                    val appExe = File(installDir, WinPaths.APP_EXE)
                    Files.copy(
                        appExe.toPath(),
                        File(installDir, WinPaths.UNINSTALL_EXE).toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                    val appDir = File(installDir, "app")
                    val baseCfg = File(appDir, "ShizukuDomainBox.cfg")
                    if (baseCfg.exists()) {
                        val text = baseCfg.readText(Charsets.UTF_8)
                        File(appDir, "Uninstall.cfg").writeText(
                            text.trimEnd() + "\r\n\r\n[ArgOptions]\r\narguments=--uninstall\r\n",
                            Charsets.UTF_8,
                        )
                        File(appDir, "Setup.cfg").writeText(
                            text.trimEnd() + "\r\n\r\n[ArgOptions]\r\narguments=--install\r\n",
                            Charsets.UTF_8,
                        )
                    }

                    status = "创建快捷方式…"
                    progress = 90
                    val deskLink = File(WinPaths.desktopDir(perUser), "${WinPaths.DISPLAY_NAME}.lnk")
                    val menuLink = File(WinPaths.startMenuDir(perUser), "${WinPaths.DISPLAY_NAME}.lnk")
                    if (desktopShortcut) WinShortcut.create(deskLink, appExe, "", installDir, appExe) else deskLink.delete()
                    if (startMenu) WinShortcut.create(menuLink, appExe, "", installDir, appExe) else menuLink.delete()

                    status = "写入注册表…"
                    progress = 95
                    WinReg.writeInstallEntry(
                        installDir, perUser,
                        File(installDir, WinPaths.UNINSTALL_EXE), appExe,
                        WinReg.dirSizeKb(installDir),
                    )

                    status = "完成"
                    progress = 100
                    null
                }.getOrElse { "${it::class.java.simpleName}: ${it.message}" }
            }
            if (fail != null) {
                error = fail
                if (finishSilently) kotlin.system.exitProcess(2)
            } else {
                page = IPage.FINISH
                if (finishSilently) {
                    kotlinx.coroutines.delay(300)
                    kotlin.system.exitProcess(0)
                }
            }
        }
    }

    if (silent) {
        LaunchedEffect(Unit) { doInstall(finishSilently = true) }
    }

    Frame(
        stepIndex = page.ordinal,
        stepCount = IPage.entries.size - 1,
        content = {
            when (page) {
                IPage.WELCOME -> WelcomePage()
                IPage.LICENSE -> LicensePage(accepted) { accepted = it }
                IPage.SCOPE -> ScopePage(perUser, admin) { v ->
                    perUser = v
                    if (cliDir == null) installDir = WinPaths.defaultInstallDir(v)
                }
                IPage.PATH -> PathPage(installDir) { installDir = it }
                IPage.OPTIONS -> OptionsPage(desktopShortcut, startMenu, launchAfter) { d, s, l ->
                    desktopShortcut = d; startMenu = s; launchAfter = l
                }
                IPage.PROGRESS -> ProgressPage(progress, status, error)
                IPage.FINISH -> FinishPage(installDir, launchAfter)
            }
        },
        bottom = {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                when (page) {
                    IPage.PROGRESS -> status
                    IPage.FINISH -> "安装位置：${installDir.absolutePath}"
                    else -> "第 ${page.ordinal + 1} 步 / 共 ${IPage.entries.size - 1} 步"
                },
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.weight(1f),
            )
            if (page != IPage.PROGRESS && page != IPage.FINISH) {
                TextButton(text = "取消", onClick = { kotlin.system.exitProcess(0) })
                Spacer(Modifier.width(6.dp))
            }
            if (page.ordinal > 0 && page != IPage.PROGRESS && page != IPage.FINISH) {
                TextButton(text = "上一步", onClick = { page = IPage.entries[page.ordinal - 1] })
                Spacer(Modifier.width(6.dp))
            }
            when (page) {
                IPage.LICENSE -> Button(onClick = { page = IPage.SCOPE }, enabled = accepted) { Text("下一步") }
                IPage.PATH -> Button(onClick = {
                    if (installDir.absolutePath.length < 4 || installDir.absolutePath.endsWith(":\\")) {
                        error = "请选一个具体的目录"
                    } else page = IPage.OPTIONS
                }) { Text("下一步") }
                IPage.OPTIONS -> Button(onClick = {
                    if (!perUser && !admin) {
                        if (relaunchElevated(installDir)) kotlin.system.exitProcess(0)
                        else error = "需要管理员权限才能为所有用户安装"
                    } else doInstall()
                }) { Text("安装") }
                IPage.FINISH -> Button(onClick = {
                    if (launchAfter) runCatching {
                        ProcessBuilder(File(installDir, WinPaths.APP_EXE).absolutePath).directory(installDir).start()
                    }
                    kotlin.system.exitProcess(0)
                }) { Text("完成") }
                IPage.PROGRESS -> Unit
                else -> Button(onClick = { page = IPage.entries[page.ordinal + 1] }) { Text("下一步") }
            }
        }
        },
    )
}

/* ------------------------- 安装动作 ------------------------- */

/** 正在运行的这套 app-image 的根目录 */
private fun selfDir(): File? =
    System.getProperty("jpackage.app-path")?.let { File(it).parentFile }

private fun copySelf(source: File, target: File, cb: (Int, String) -> Unit) {
    val selfExe = System.getProperty("jpackage.app-path")?.let { File(it).name } ?: WinPaths.APP_EXE
    val files = source.walkTopDown().filter { it.isFile }.toList()
    files.forEachIndexed { idx, f ->
        val rel = f.relativeTo(source).path
        val out = File(target, rel)
        out.parentFile?.mkdirs()
        runCatching { Files.copy(f.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        if (idx % 20 == 0) cb(3 + (idx * 78 / files.size.coerceAtLeast(1)), rel)
    }
    // 如果是从 Setup.exe 启动的，目标目录里也叫 Setup.exe，补一个主程序名的 exe
    val wanted = File(target, WinPaths.APP_EXE)
    val setup = File(target, selfExe)
    if (!wanted.exists() && setup.exists()) {
        runCatching { Files.copy(setup.toPath(), wanted.toPath(), StandardCopyOption.REPLACE_EXISTING) }
    }
}

private fun relaunchElevated(dir: File): Boolean = runCatching {
    val exe = System.getProperty("jpackage.app-path") ?: return false
    val cmd = "Start-Process -FilePath '${exe.replace("'", "''")}' -Verb RunAs -ArgumentList " +
        "'/SCOPE=all','/DIR=${dir.absolutePath}'"
    ProcessBuilder("powershell", "-NoProfile", "-WindowStyle", "Hidden", "-Command", cmd).start()
    true
}.getOrDefault(false)

/* ------------------------- 页面 ------------------------- */

@Composable
private fun Frame(stepIndex: Int, stepCount: Int, content: @Composable () -> Unit, bottom: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background)) {
        Box(
            modifier = Modifier.width(238.dp).fillMaxHeight()
                .background(Brush.verticalGradient(listOf(BrandBlueDark, BrandBlue))),
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                AppLogo(64.dp)
                Spacer(Modifier.height(16.dp))
                Text(WinPaths.DISPLAY_NAME, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                Text("v${WinPaths.VERSION} · Windows", color = Color(0xFFBFD2FF), fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                Text("DNSHE 免费域名\n多账号管理工具", color = Color(0xFFD5E2FF), fontSize = 12.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(14.dp))
                Text("· 与手机版 JSON 互通", color = Color(0xFFD5E2FF), fontSize = 11.sp)
                Text("· 密钥本机加密保存", color = Color(0xFFD5E2FF), fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text("安装进度", color = Color(0xFFBFD2FF), fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = stepIndex.toFloat() / stepCount.toFloat(),
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    colors = top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults.progressIndicatorColors(foregroundColor = Color.White, backgroundColor = Color(0x553D6FD9)),
                )
                Spacer(Modifier.height(10.dp))
                Text("(c) 2026 Nekoshizuku", color = Color(0x88FFFFFF), fontSize = 10.sp)
            }
        }
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .background(MiuixTheme.colorScheme.surface)
                    .verticalScroll(rememberScrollState())
                    .padding(26.dp),
            ) { content() }
            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(MiuixTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) { bottom() }
        }
    }
}

@Composable
private fun Title(text: String) =
    Text(text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = MiuixTheme.colorScheme.onSurface)

@Composable
private fun Line(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(label, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.width(84.dp))
        Text(value, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun OptRow(selected: Boolean, title: String, desc: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.dividerLine, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(6.dp))
        Column {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(desc, fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}

@Composable
private fun WelcomePage() {
    Column {
        Title("欢迎使用 ${WinPaths.DISPLAY_NAME} 安装向导")
        Spacer(Modifier.height(10.dp))
        Text("这个向导会引导你把「${WinPaths.DISPLAY_NAME}」PC 版装到这台电脑上。", fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Text("安装前请确认：", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        listOf(
            "你有 DNSHE 账号（没有的话可以先去官网注册一个）",
            "你手里有 API Key 和 API Secret",
            "安装过程不需要联网，程序运行时才需要",
        ).forEach {
            Text("  ·  $it", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(vertical = 2.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("程序自带 Java 运行时，电脑上不用另外装 Java。", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun LicensePage(accepted: Boolean, onAccepted: (Boolean) -> Unit) {
    Column {
        Title("许可协议")
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(310.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, MiuixTheme.colorScheme.dividerLine, RoundedCornerShape(8.dp))
                .background(MiuixTheme.colorScheme.surfaceContainer)
                .padding(14.dp)
                .verticalScroll(rememberScrollState()),
        ) { Text(LICENSE_TEXT, fontSize = 11.5.sp, lineHeight = 17.sp) }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = accepted, onCheckedChange = onAccepted)
            Text("我接受以上许可协议", fontSize = 13.sp)
        }
    }
}

@Composable
private fun ScopePage(perUser: Boolean, admin: Boolean, onChange: (Boolean) -> Unit) {
    Column {
        Title("选择安装范围")
        Spacer(Modifier.height(10.dp))
        OptRow(
            selected = perUser,
            title = "仅为我安装（推荐）",
            desc = "装到 ${WinPaths.defaultInstallDir(true).absolutePath}，不需要管理员权限",
            onClick = { onChange(true) },
        )
        Spacer(Modifier.height(8.dp))
        OptRow(
            selected = !perUser,
            title = "为这台电脑上的所有用户安装",
            desc = "装到 ${WinPaths.defaultInstallDir(false).absolutePath}" + if (admin) "（需要管理员权限）" else "；下一步会弹 UAC 提权",
            onClick = { onChange(false) },
        )
        Spacer(Modifier.height(14.dp))
        Text(if (admin) "当前以管理员身份运行。" else "当前不是管理员身份。", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun PathPage(dir: File, onChange: (File) -> Unit) {
    Column {
        Title("选择安装位置")
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = dir.absolutePath,
                onValueChange = { onChange(File(it)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
            )
            Spacer(Modifier.width(10.dp))
            TextButton(text = "浏览…", onClick = {
                Sys.pickDirectory("选择安装目录", dir)?.let { picked ->
                    onChange(File(picked, WinPaths.APP_DATA_FOLDER))
                }
            })
        }
        Spacer(Modifier.height(16.dp))
        Line("所需空间", "约 160 MB")
        Line("将安装到", dir.absolutePath)
        Line("用户数据", WinPaths.dataDir().absolutePath + "（不受影响）")
        Spacer(Modifier.height(12.dp))
        Text("提示：装到系统盘以外的位置也完全可以，程序不会往安装目录写用户数据。", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun OptionsPage(d: Boolean, s: Boolean, l: Boolean, onChange: (Boolean, Boolean, Boolean) -> Unit) {
    Column {
        Title("附加任务")
        Spacer(Modifier.height(10.dp))
        Check(d, "创建桌面快捷方式") { onChange(it, s, l) }
        Check(s, "创建开始菜单项") { onChange(d, it, l) }
        Spacer(Modifier.height(16.dp))
        Title("安装完成后")
        Spacer(Modifier.height(8.dp))
        Check(l, "立即启动 ${WinPaths.DISPLAY_NAME}") { onChange(d, s, it) }
        Spacer(Modifier.height(16.dp))
        Text(
            "卸载程序会一起装到安装目录（Uninstall.exe），随时可以在\n" +
                "「设置 → 应用」里卸载，也可以用开始菜单里的卸载入口。",
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            lineHeight = 17.sp,
        )
    }
}

@Composable
private fun Check(checked: Boolean, text: String, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(text, fontSize = 13.sp)
    }
}

@Composable
private fun ProgressPage(progress: Int, status: String, error: String?) {
    Column {
        Title(if (error == null) "正在安装" else "出错了")
        Spacer(Modifier.height(18.dp))
        LinearProgressIndicator(
            progress = progress / 100f,
            modifier = Modifier.fillMaxWidth().height(8.dp),
                    colors = top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults.progressIndicatorColors(backgroundColor = MiuixTheme.colorScheme.surfaceContainer),
                )
        Spacer(Modifier.height(10.dp))
        Text("$progress%   $status", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        if (error != null) {
            Spacer(Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .background(MiuixTheme.colorScheme.errorContainer).padding(12.dp)) {
                Text(error, fontSize = 12.sp, color = MiuixTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun FinishPage(dir: File, launchAfter: Boolean) {
    Column {
        Title("安装完成喵~")
        Spacer(Modifier.height(12.dp))
        Text("「${WinPaths.DISPLAY_NAME}」已经装好了。", fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Line("安装位置", dir.absolutePath)
        Line("卸载程序", File(dir, WinPaths.UNINSTALL_EXE).absolutePath)
        Line("数据目录", WinPaths.dataDir().absolutePath)
        Spacer(Modifier.height(16.dp))
        Text(
            "打开程序后，在「账号」页添加你的 API Key / Secret 就能用了。\n" +
                "手机版导出的 .json 备份可以直接在这边导入，反过来也行。",
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (launchAfter) "点「完成」会自动启动程序。" else "点「完成」关闭安装向导。",
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}
