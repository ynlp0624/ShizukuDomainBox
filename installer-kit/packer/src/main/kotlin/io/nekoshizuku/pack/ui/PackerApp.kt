package io.nekoshizuku.pack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.nekoshizuku.pack.AppImageProbe
import io.nekoshizuku.pack.BuildRunner
import io.nekoshizuku.pack.KitPaths
import java.io.File

/**
 * **专业版打包器** —— 所有字段都能改。
 *
 * 和简单版**共用同一份状态、同一套动作**（见 PackerState.kt），
 * 区别只有「露出多少字段」。所以随时切来切去都不会丢东西。
 */
@Composable
fun PackerApp(st: PackerState, onModeChange: (PackerMode) -> Unit) {
    val scope = rememberCoroutineScope()
    val runner = remember(st.kitRoot) { BuildRunner(st.kitRoot ?: File(".")) }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF3E6FD9),
            surface = Color(0xFFFDFDFF),
            surfaceVariant = Color(0xFFF2F4FA),
            outlineVariant = Color(0xFFD9DEE9),
            errorContainer = Color(0xFFF9DEDC),
            onErrorContainer = Color(0xFF410E0B),
        ),
    ) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            ProHeader(st, onModeChange)
            Row(Modifier.weight(1f).fillMaxWidth()) {
                FormPane(st, Modifier.weight(0.58f).fillMaxHeight())
                Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                LogPane(st, Modifier.weight(0.42f).fillMaxHeight())
            }
            ProBottomBar(st, runner, scope)
        }
    }
}

/* ============================== 顶栏 ============================== */

@Composable
private fun ProHeader(st: PackerState, onModeChange: (PackerMode) -> Unit) {
    val brand = runCatching {
        val hex = st.config.brandColor.trim().removePrefix("#")
        if (hex.length == 6) Color(0xFF000000L or hex.toLong(16)) else Color(0xFF3E6FD9)
    }.getOrDefault(Color(0xFF3E6FD9))

    Column {
        Box(Modifier.fillMaxWidth().height(4.dp).background(brand))
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("专业版 · 全部选项", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "工程：${st.kitRoot?.absolutePath ?: "（没找到 tools\\build-installer.ps1）"}　配置：${st.configPath?.absolutePath ?: "（未指定）"}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = {
                chooseDirectory("选择 InstallerKit 工程目录（里面要有 tools\\build-installer.ps1）", st.kitRoot)?.let { dir ->
                    if (KitPaths.looksLikeKitRoot(dir)) {
                        st.kitRoot = dir
                        if (st.configPath == null) st.configPath = File(dir, KitPaths.DEFAULT_CONFIG)
                        st.rememberSettings()
                        st.append("工程目录：${dir.absolutePath}")
                    } else {
                        st.append("这个目录里没有 ${KitPaths.BUILD_SCRIPT}，换一个吧")
                    }
                }
            }) { Text("换工程…", fontSize = 12.sp) }
            Spacer(Modifier.width(10.dp))
            ModeSwitch(PackerMode.PRO, onModeChange)
        }
    }
}

/* ============================== 表单 ============================== */

@Composable
private fun FormPane(st: PackerState, modifier: Modifier) {
    val formScroll = rememberScrollState()
    // 刚打开时窗口会给某个输入框焦点，Compose 会「把焦点滚进可视区」，
    // 结果是表单初始位置停在中间（看着像坏了）。这里在开头一秒内反复拉回顶部。
    LaunchedEffect(Unit) {
        repeat(8) {
            formScroll.scrollTo(0)
            kotlinx.coroutines.delay(120)
        }
    }

    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.surface)
            .verticalScroll(formScroll)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        st.probe?.let { probe ->
            if (probe.usable) {
                Text("# ${probe.summary}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
            }
        }

        Section("① 主程序 app-image") {
            PickerRow(
                label = "目录",
                value = st.appImageDir?.absolutePath ?: "（还没选）",
                buttonText = "选择…",
                onPick = {
                    chooseDirectory("选择程序文件夹（jpackage app-image）", st.appImageDir)?.let { applyChosenPath(st, it) }
                },
            )
            st.probe?.problems?.forEach { problem ->
                Text("⚠ $problem", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
            }
            Field("主程序 exe 名", st.config.mainExeName, "留空 = <AppId>.exe") {
                st.config = st.config.copy(mainExeName = it)
            }
            Toggle("打包前先构建产品 app-image（跑下面这个 Gradle 任务）", st.buildProduct) { st.buildProduct = it }
            Field("构建任务", st.config.buildTask, "例如 :desktop:createDistributable") {
                st.config = st.config.copy(buildTask = it)
            }
            // 便携模式专属：装绿色软件时用哪套向导
            if (st.config.isPortable) {
                Toggle(
                    "便携模式用完整向导（和 jpackage 模式一模一样的外观，但安装包要多背约 57MB）",
                    st.config.useFullWizardInPortable,
                ) {
                    st.config = st.config.copy(portableWizard = if (it) "full" else "lite")
                }
                Text(
                    if (st.config.useFullWizardInPortable)
                        "　→ 现在是：完整版 Compose 向导，安装包 ≈ 57MB + 产品体积"
                    else
                        "　→ 现在是：轻量版向导（系统自带 .NET），安装包 ≈ 产品体积 + 几十 KB",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Section("② 产品信息") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("产品名", st.config.productName, null, Modifier.weight(1.4f)) {
                    st.config = st.config.copy(productName = it)
                }
                Field("AppId", st.config.appId, "ASCII，用于注册表", Modifier.weight(1f)) {
                    st.config = st.config.copy(appId = it)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("版本", st.config.version, null, Modifier.weight(1f)) {
                    st.config = st.config.copy(version = it)
                }
                Field("发布者", st.config.publisher, null, Modifier.weight(1.4f)) {
                    st.config = st.config.copy(publisher = it)
                }
            }
            Field("主页链接", st.config.urlInfoAbout, null) { st.config = st.config.copy(urlInfoAbout = it) }
            Field("帮助链接", st.config.helpLink, null) { st.config = st.config.copy(helpLink = it) }
        }

        Section("③ 外观") {
            PickerRow("exe 图标（.ico）", st.config.iconFile.ifBlank { "（留空 = 用主程序自带图标）" }, "选择…") {
                chooseFile("选择 .ico 图标", listOf("ico"), st.config.iconFile)?.let { f ->
                    st.config = st.config.copy(iconFile = relativeTo(st.configPath?.parentFile, f))
                }
            }
            PickerRow("窗口 logo（.png）", st.config.logoFile.ifBlank { "（留空 = 用内置 logo）" }, "选择…") {
                chooseFile("选择 logo 图片", listOf("png"), st.config.logoFile)?.let { f ->
                    st.config = st.config.copy(logoFile = relativeTo(st.configPath?.parentFile, f))
                }
            }
            PickerRow("窗口图标目录", st.config.windowIconDir.ifBlank { "（可选：里面放 app-icon-<尺寸>.png）" }, "选择…") {
                chooseDirectory("选择窗口图标目录", st.config.windowIconDir.takeIf { it.isNotBlank() }?.let { File(it) })?.let { d ->
                    st.config = st.config.copy(windowIconDir = relativeTo(st.configPath?.parentFile, d))
                }
            }
            Field("品牌主色", st.config.brandColor, "#RRGGBB，留空 = 默认蓝") {
                st.config = st.config.copy(brandColor = it)
            }
        }

        Section("④ 安装行为") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("安装目录名", st.config.installDirName, null, Modifier.weight(1f)) {
                    st.config = st.config.copy(installDirName = it)
                }
                Field("数据目录名", st.config.dataDirName, null, Modifier.weight(1f)) {
                    st.config = st.config.copy(dataDirName = it)
                }
                Field("预计体积 MB", st.config.estimatedSizeMb.toString(), null, Modifier.weight(0.8f)) {
                    it.toIntOrNull()?.let { n -> st.config = st.config.copy(estimatedSizeMb = n) }
                }
            }
            Toggle("默认「仅为我安装」", st.config.defaultPerUser) { st.config = st.config.copy(defaultPerUser = it) }
            Toggle("允许用户改安装范围（关掉就锁死上面那个）", st.config.allowScopeChoice) {
                st.config = st.config.copy(allowScopeChoice = it)
            }
            Toggle("默认创建桌面快捷方式", st.config.defaultDesktopShortcut) {
                st.config = st.config.copy(defaultDesktopShortcut = it)
            }
            Toggle("默认创建开始菜单项", st.config.defaultStartMenu) {
                st.config = st.config.copy(defaultStartMenu = it)
            }
            Toggle("安装完默认启动程序", st.config.launchAfterInstall) {
                st.config = st.config.copy(launchAfterInstall = it)
            }
            Toggle("卸载时默认保留账号数据", st.config.uninstallKeepDataByDefault) {
                st.config = st.config.copy(uninstallKeepDataByDefault = it)
            }
            Toggle("卸载时自动关掉正在运行的程序", st.config.killRunningAppOnUninstall) {
                st.config = st.config.copy(killRunningAppOnUninstall = it)
            }
        }

        Section("⑤ 许可协议") {
            PickerRow("协议文件（.txt）", st.config.licenseFile.ifBlank { "（留空 = 向导跳过许可页）" }, "选择…") {
                chooseFile("选择许可协议文本", listOf("txt", "md"), st.config.licenseFile)?.let { f ->
                    st.config = st.config.copy(licenseFile = relativeTo(st.configPath?.parentFile, f))
                }
            }
        }

        Section("⑥ 数字签名") {
            Toggle("打包时签名（安装包 + 发行目录里的 exe）", st.config.codeSign.enabled) {
                st.config = st.config.copy(codeSign = st.config.codeSign.copy(enabled = it))
            }
            Field("证书档案", st.config.codeSign.profile, "ShizukuCodeSign 的档案名") {
                st.config = st.config.copy(codeSign = st.config.codeSign.copy(profile = it))
            }
            Field("签名工具目录", st.config.codeSign.toolsDir, "里面有 ShizukuCodeSign.psm1") {
                st.config = st.config.copy(codeSign = st.config.codeSign.copy(toolsDir = it))
            }
            Toggle("加时间戳（需要联网）", st.config.codeSign.timestamp) {
                st.config = st.config.copy(codeSign = st.config.codeSign.copy(timestamp = it))
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

/* ============================== 日志 ============================== */

@Composable
private fun LogPane(st: PackerState, modifier: Modifier) {
    val scroll = rememberScrollState()
    val version = st.logVersion
    val lines = remember(version) { st.log.toList() }

    LaunchedEffect(version) {
        if (scroll.maxValue > 0) scroll.animateScrollTo(scroll.maxValue)
    }

    Column(modifier.background(Color(0xFF1E2233)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("打包日志", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            if (st.running) LinearProgressIndicator(Modifier.width(90.dp).height(4.dp))
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
            if (lines.isEmpty()) {
                Text(
                    "还没开始。左边选好程序文件夹，点右下角「开始打包」。",
                    color = Color(0xFF9AA3B8),
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                )
            } else {
                Column {
                    lines.forEach { line ->
                        Text(
                            line,
                            color = when {
                                line.contains("[FAIL]") || line.contains("错误") || line.contains("WARNING") -> Color(0xFFFF9A8B)
                                line.contains("[ OK ]") -> Color(0xFF8BE28B)
                                line.startsWith("===") -> Color(0xFF7FB3FF)
                                else -> Color(0xFFD6DBE8)
                            },
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }
        }
    }
}

/* ============================== 底栏 ============================== */

@Composable
private fun ProBottomBar(st: PackerState, runner: BuildRunner, scope: kotlinx.coroutines.CoroutineScope) {
    Column(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Toggle("只出目录版（不封单文件 exe）", !st.makeSingleExe) { st.makeSingleExe = !it }
            Spacer(Modifier.width(16.dp))
            Text(
                when {
                    st.running -> "正在打包…（大包会比较慢，耐心等）"
                    st.exitCode == 0 && st.release != null -> {
                        val r = st.release!!
                        "打包成功：${r.installer?.let { File(it).name } ?: "（目录版）"}　SHA256 ${r.sha256?.take(16) ?: "-"}…"
                    }
                    st.exitCode == 0 -> "打包成功"
                    st.exitCode != null -> "打包失败，退出码 ${st.exitCode}（看右边日志最后几行）"
                    else -> ""
                },
                fontSize = 11.sp,
                color = if (st.exitCode != null && st.exitCode != 0) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )

            if (st.release?.installer != null) {
                TextButton(onClick = { openInExplorer(File(st.release!!.installer!!).parentFile) }) { Text("打开产物目录") }
            }
            TextButton(onClick = { saveConfig(st) }) { Text("保存配置") }
            TextButton(onClick = { loadConfigInto(st) }) { Text("载入配置") }
            Spacer(Modifier.width(6.dp))

            if (st.running) {
                OutlinedButton(onClick = { runner.cancel() }) { Text("取消") }
            } else {
                Button(
                    enabled = st.kitRoot != null && st.probe?.usable != false,
                    onClick = { startBuild(st, runner, scope) },
                ) { Text("开始打包") }
            }
        }
    }
}

/* ============================== 小件 ============================== */

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(10.dp))
    Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(4.dp))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
}

@Composable
private fun Field(
    label: String,
    value: String,
    hint: String? = null,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onChange: (String) -> Unit,
) {
    Column(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            label = { Text(label, fontSize = 12.sp) },
            supportingText = hint?.let { { Text(it, fontSize = 10.sp) } },
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PickerRow(label: String, value: String, buttonText: String, onPick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 2)
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onPick) { Text(buttonText, fontSize = 12.sp) }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, fontSize = 12.sp)
    }
}
