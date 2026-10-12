package io.nekoshizuku.pack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import io.nekoshizuku.pack.BuildRunner
import io.nekoshizuku.pack.KitPaths
import java.io.File

/**
 * **小白版打包器** —— 三步走：选程序 → 填几个字 → 打包。
 *
 * 刻意只露三个输入框：
 *   · 产品名（安装时显示的名字）
 *   · 版本号
 *   · 发布者
 * 其余全部自动：主程序 exe、启动配置、图标、安装目录名、数据目录名、体积、
 * 快捷方式、卸载行为……都能从选中的程序里推出来，或者用推荐值。
 *
 * 想改细节就点右上角「专业版」——**两个版本共用同一份配置和同一套打包流程**，
 * 切过去不会丢东西。
 */
@Composable
fun SimpleApp(st: PackerState, onModeChange: (PackerMode) -> Unit) {
    val scope = rememberCoroutineScope()
    val runner = remember(st.kitRoot) { BuildRunner(st.kitRoot ?: File(".")) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SimpleHeader(st, onModeChange)

        Column(
            Modifier.weight(1f).fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Step1(st)
            Spacer(Modifier.height(14.dp))
            Step2(st)
            Spacer(Modifier.height(16.dp))
            Step3(st, runner, scope)
            Spacer(Modifier.height(12.dp))
            SimpleLog(st)
        }
    }
}

/* ============================== 头 ============================== */

@Composable
private fun SimpleHeader(st: PackerState, onModeChange: (PackerMode) -> Unit) {
    Column {
        Box(Modifier.fillMaxWidth().height(5.dp).background(Color(0xFF3E6FD9)))
        Row(
            Modifier.fillMaxWidth().background(Color(0xFFF2F4FA)).padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("InstallerKit 打包器", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "把你的程序变成一个能安装的安装包 —— 选程序、填名字、点打包，就这三步",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ModeSwitch(PackerMode.SIMPLE, onModeChange)
        }
    }
}

/** 简单 / 专业 切换（两个界面共用） */
@Composable
fun ModeSwitch(current: PackerMode, onChange: (PackerMode) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFFE3E8F4)).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(PackerMode.SIMPLE to "简单版", PackerMode.PRO to "专业版").forEach { (mode, label) ->
            val active = mode == current
            if (active) {
                Box(
                    Modifier.clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF3E6FD9))
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                ) {
                    Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            } else {
                TextButton(onClick = { onChange(mode) }) {
                    Text(label, fontSize = 12.sp, color = Color(0xFF44506B))
                }
            }
        }
    }
}

/* ============================== 第 1 步 ============================== */

@Composable
private fun Step1(st: PackerState) {
    val probe = st.probe
    StepTitle(1, "选主程序", "选一个 .exe 文件，或者直接选程序所在的文件夹")

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = {
            chooseFile("选主程序（.exe）", listOf("exe"), st.appImageDir?.absolutePath)?.let { applyChosenPath(st, it) }
        }) { Text("选主程序 exe 文件…") }
        OutlinedButton(onClick = {
            chooseDirectory("选程序所在的文件夹", st.appImageDir)?.let { applyChosenPath(st, it) }
        }) { Text("选程序文件夹…") }
    }

    if (probe == null) {
        Spacer(Modifier.height(6.dp))
        Text(
            "还没选。一般来说选程序文件夹更省事 —— 里面的东西会一起打包。",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (probe.usable) Color(0xFFEFF7EF) else MaterialTheme.colorScheme.errorContainer)
                .border(
                    1.dp,
                    if (probe.usable) Color(0xFFBFE0BF) else MaterialTheme.colorScheme.error,
                    RoundedCornerShape(10.dp),
                )
                .padding(12.dp),
        ) {
            Column {
                Text(
                    if (probe.usable) "✅ 认识这个程序了" else "⚠ 这个文件夹看起来不太对",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                Text("主程序：${probe.exeName ?: "（没找到）"}", fontSize = 12.sp)
                if (probe.usable) {
                    Text(
                        "类型：${probe.modeLabel}" + when (probe.kind) {
                            io.nekoshizuku.pack.AppImageProbe.Kind.PORTABLE -> "　→ 安装包只比原程序大几十 KB"
                            io.nekoshizuku.pack.AppImageProbe.Kind.JPACKAGE -> "　→ 共用它自带的运行时，安装包和程序差不多大"
                            io.nekoshizuku.pack.AppImageProbe.Kind.UNKNOWN -> ""
                        },
                        fontSize = 12.sp,
                        color = Color(0xFF2E6B2E),
                    )
                }
                Text(
                    when (probe.kind) {
                        io.nekoshizuku.pack.AppImageProbe.Kind.PORTABLE ->
                            "会一起打包 ${probe.fileCount} 个文件 · ${probe.sizeMb} MB"
                        else ->
                            "会一起打包 ${probe.jarCount} 个文件 · ${probe.sizeMb} MB" +
                                if (probe.hasRuntime) (if (probe.hasJavaDesktop) " · 自带运行时 ✓" else " · ⚠ 运行时不全") else ""
                    },
                    fontSize = 12.sp,
                )
                if (probe.kind == io.nekoshizuku.pack.AppImageProbe.Kind.PORTABLE) {
                    Text(
                        if (st.config.useFullWizardInPortable)
                            "安装界面：完整版（和正式安装器一样的外观，安装包会大 57MB）"
                        else
                            "安装界面：轻量版（安装包只比原来大几十 KB）—— 想要一样的外观，到「专业版」勾一下就行",
                        fontSize = 11.sp,
                        color = Color(0xFF2E6B2E),
                    )
                }
                probe.problems.forEach {
                    Spacer(Modifier.height(3.dp))
                    Text("· $it", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/* ============================== 第 2 步 ============================== */

@Composable
private fun Step2(st: PackerState) {
    StepTitle(2, "填几个字", "这些会显示在安装向导和「设置 → 应用」里")

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = st.config.productName,
            onValueChange = {
                // 产品名变了，AppId 还空着的话跟着它走（AppId 是给注册表用的 ASCII 名字）
                st.config = st.config.copy(productName = it)
                if (st.config.appId.isBlank()) st.config = st.config.copy(appId = asciiId(it))
            },
            singleLine = true,
            label = { Text("产品名", fontSize = 12.sp) },
            supportingText = { Text("安装时显示的名字，可以写中文", fontSize = 10.sp) },
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(1.6f),
        )
        OutlinedTextField(
            value = st.config.version,
            onValueChange = { st.config = st.config.copy(version = it) },
            singleLine = true,
            label = { Text("版本号", fontSize = 12.sp) },
            supportingText = { Text("例如 1.0.0", fontSize = 10.sp) },
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(1f),
        )
    }
    OutlinedTextField(
        value = st.config.publisher,
        onValueChange = { st.config = st.config.copy(publisher = it) },
        singleLine = true,
        label = { Text("发布者", fontSize = 12.sp) },
        supportingText = { Text("署名，可以写你自己的名字", fontSize = 10.sp) },
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    )

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
        Checkbox(
            checked = st.config.codeSign.enabled,
            onCheckedChange = { st.config = st.config.copy(codeSign = st.config.codeSign.copy(enabled = it)) },
        )
        Text("打包时签名", fontSize = 12.sp)
        Spacer(Modifier.width(6.dp))
        Text(
            if (st.config.codeSign.profile.isBlank()) "（用你自己建的证书）" else "（用证书档案 ${st.config.codeSign.profile}）",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/* ============================== 第 3 步 ============================== */

@Composable
private fun Step3(st: PackerState, runner: BuildRunner, scope: kotlinx.coroutines.CoroutineScope) {
    StepTitle(3, "打包", "点一下就行 —— 大概要等一两分钟")

    // 小白版要：名字填了、程序认出来了、工程目录找到了
    val ready = st.probe?.usable == true && st.config.productName.isNotBlank() && st.kitRoot != null

    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(
            enabled = ready && !st.running,
            onClick = {
                if (st.config.appId.isBlank()) st.config = st.config.copy(appId = asciiId(st.config.productName))
                if (st.config.installDirName.isBlank()) st.config = st.config.copy(installDirName = st.config.appId)
                if (st.config.dataDirName.isBlank()) st.config = st.config.copy(dataDirName = st.config.appId)
                startBuild(st, runner, scope)
            },
            modifier = Modifier.height(48.dp),
        ) {
            Text(if (st.running) "正在打包…" else "开始打包", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        if (st.running) {
            Spacer(Modifier.width(14.dp))
            CircularProgressIndicator(Modifier.width(20.dp).height(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("正在打包，别关窗口～", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(14.dp))

        when {
            st.exitCode == 0 && st.release?.installer != null -> {
                Text("✅ 打包好了！", fontSize = 13.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = { openInExplorer(File(st.release!!.installer!!).parentFile) }) {
                    Text("打开产物文件夹")
                }
            }
            st.exitCode == 0 -> Text("✅ 打包好了（目录版）", fontSize = 13.sp, color = Color(0xFF2E7D32))
            st.exitCode != null -> Text("❌ 没成功，看下面的日志", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            !ready -> Text(
                when {
                    st.kitRoot == null -> "先把 InstallerKit 工程目录找出来"
                    st.probe == null -> "先选一个程序"
                    st.probe?.usable != true -> "这个程序还没认出来，换一个文件夹试试"
                    else -> "还需要填产品名"
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (st.release?.installer != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            "安装包：${st.release!!.installer}",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "把它发给别人（或自己双击）就能装了。",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (st.kitRoot == null) {
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = {
            chooseDirectory("选 InstallerKit 工程目录（里面要有 tools\\build-installer.ps1）", null)?.let { dir ->
                if (KitPaths.looksLikeKitRoot(dir)) {
                    st.kitRoot = dir
                    if (st.configPath == null) st.configPath = File(dir, KitPaths.DEFAULT_CONFIG)
                    st.rememberSettings()
                    st.append("工程目录：${dir.absolutePath}")
                } else {
                    st.append("这个目录里没有 ${KitPaths.BUILD_SCRIPT}")
                }
            }
        }) { Text("手动指定 InstallerKit 工程目录…") }
    }
}

/* ============================== 日志 ============================== */

@Composable
private fun SimpleLog(st: PackerState) {
    val scroll = rememberScrollState()
    val version = st.logVersion
    val lines = remember(version) { st.log.toList() }

    LaunchedEffect(version) { if (scroll.maxValue > 0) scroll.animateScrollTo(scroll.maxValue) }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("打包过程", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text(
                st.configPath?.absolutePath ?: "",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 220.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1E2233))
                .padding(12.dp)
                .verticalScroll(scroll),
        ) {
            if (lines.isEmpty()) {
                Text(
                    "还没开始。选好程序、填好名字，点「开始打包」就行。",
                    color = Color(0xFF9AA3B8),
                    fontSize = 11.sp,
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
                            lineHeight = 15.sp,
                        )
                    }
                }
            }
        }
    }
}

/* ============================== 小件 ============================== */

@Composable
private fun StepTitle(no: Int, title: String, hint: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.width(22.dp).height(22.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFF3E6FD9)),
            contentAlignment = Alignment.Center,
        ) {
            Text("$no", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(10.dp))
        Text(hint, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(8.dp))
}

/** 中文产品名 → 一个能当 AppId 用的 ASCII 名字（注册表 / 目录名都靠它） */
fun asciiId(name: String): String {
    val cleaned = name.filter { it.isLetterOrDigit() && it.code < 128 }
    return cleaned.takeIf { it.isNotBlank() } ?: "MyApp"
}
