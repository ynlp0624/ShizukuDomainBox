package io.nekoshizuku.dnshe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import io.nekoshizuku.dnshe.data.AppContainer
import io.nekoshizuku.dnshe.data.Lang
import io.nekoshizuku.dnshe.data.UpdateChecker
import io.nekoshizuku.dnshe.data.UpdateInfo
import io.nekoshizuku.dnshe.i18n.Str
import io.nekoshizuku.dnshe.i18n.t
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 发现新版本时的弹窗。
 *
 * 显示版本号、更新说明，提供「下载并安装」和「浏览器下载」两个入口。
 */
@Composable
fun UpdateAvailableDialog(
    info: UpdateInfo,
    currentVersion: String,
    lang: Lang,
    onDismiss: () -> Unit,
    onToast: (String) -> Unit,
) {
    var show by remember { mutableStateOf(true) }
    var progress by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // 下载中状态：非空表示正在下载
    val downloading = progress != null

    fun startDownload() {
        if (downloading) return
        progress = 0L to info.sizeBytes

        // 没给「安装未知来源」权限的先引导一次
        if (!io.nekoshizuku.dnshe.data.UpdateApk.canInstallPackages(ctx)) {
            progress = null
            onToast(t(Str.UpdateNeedPermission))
            io.nekoshizuku.dnshe.data.UpdateApk.openInstallSettings(ctx)
            return
        }

        scope.launch {
            val fileName = info.downloadUrl.substringAfterLast('/')
                .ifBlank { "ShizukuDomainBox-Android-v${info.version}.apk" }
            val file = io.nekoshizuku.dnshe.data.UpdateApk.download(
                ctx = ctx,
                url = info.downloadUrl,
                fileName = fileName,
            ) { done, total ->
                progress = done to (if (total > 0) total else info.sizeBytes)
            }
            progress = null
            if (file != null && file.exists()) {
                val ok = io.nekoshizuku.dnshe.data.UpdateApk.install(ctx, file)
                if (!ok) {
                    onToast(t(Str.UpdateInstallFailed))
                    io.nekoshizuku.dnshe.data.UpdateApk.openInBrowser(ctx, info.downloadUrl)
                }
            } else {
                onToast(t(Str.UpdateDownloadFailed))
                io.nekoshizuku.dnshe.data.UpdateApk.openInBrowser(ctx, info.downloadUrl)
            }
        }
    }

    OverlayDialog(
        show = show,
        title = t(Str.UpdateFound, info.version),
        renderInRootScaffold = true,
        onDismissRequest = { show = false; onDismiss() },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 版本对比
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("v$currentVersion", MiuixTheme.colorScheme.surfaceContainer,
                    MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Text(
                    text = " → ",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Pill("v${info.version}", MiuixTheme.colorScheme.primaryContainer,
                    MiuixTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.weight(1f))
                if (info.sizeBytes > 0) {
                    Text(
                        text = formatSize(info.sizeBytes),
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = "${if (lang == Lang.EN) "Source" else "来源"} · ${info.source}" +
                    if (info.publishedAt.isNotBlank()) " · ${info.publishedAt.take(10)}" else "",
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )

            Spacer(Modifier.height(14.dp))

            // 更新说明
            if (info.notes.isNotBlank()) {
                Text(
                    text = if (lang == Lang.EN) "What's new" else "更新内容",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MiuixTheme.colorScheme.surfaceContainer)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                ) {
                    Text(
                        text = plainNotes(info.notes),
                        fontSize = 12.sp,
                        lineHeight = 19.sp,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // 下载进度
            if (downloading) {
                val (done, total) = progress ?: (0L to 0L)
                val ratio = if (total > 0) (done.toFloat() / total) else 0f
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(MiuixTheme.colorScheme.surfaceContainer),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(ratio.coerceIn(0f, 1f))
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(MiuixTheme.colorScheme.primary),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${formatSize(done)} / ${formatSize(total)}" +
                            "  ${(ratio * 100).toInt()}%",
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            // 按钮
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(
                    text = t(Str.Cancel),
                    onClick = { show = false; onDismiss() },
                    enabled = !downloading,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = if (downloading) {
                        t(Str.UpdateDownloading)
                    } else {
                        t(Str.UpdateDownloadInstall)
                    },
                    onClick = { startDownload() },
                    enabled = !downloading,
                    modifier = Modifier.weight(1.4f),
                )
            }
            Spacer(Modifier.height(4.dp))
            TextButton(
                text = t(Str.UpdateBrowserDownload),
                onClick = {
                    io.nekoshizuku.dnshe.data.UpdateApk.openInBrowser(ctx, info.downloadUrl)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 检查中 / 已是最新 / 检查失败 的轻量提示 */
@Composable
fun UpdateCheckDialog(
    state: UpdateCheckState,
    lang: Lang,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    var show by remember { mutableStateOf(true) }
    OverlayDialog(
        show = show,
        title = t(Str.UpdateCheckTitle),
        renderInRootScaffold = true,
        onDismissRequest = { show = false; onDismiss() },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = when (state) {
                    UpdateCheckState.Checking -> t(Str.UpdateChecking)
                    UpdateCheckState.UpToDate -> t(Str.UpdateUpToDate)
                    is UpdateCheckState.Failed -> t(Str.UpdateCheckFailed)
                },
                fontSize = 13.sp,
            )
            if (state is UpdateCheckState.Failed) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = state.message,
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state is UpdateCheckState.Failed) {
                    TextButton(
                        text = t(Str.Retry),
                        onClick = { show = false; onRetry() },
                        modifier = Modifier.weight(1f),
                    )
                }
                TextButton(
                    text = t(Str.Ok),
                    onClick = { show = false; onDismiss() },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

sealed interface UpdateCheckState {
    data object Checking : UpdateCheckState
    data object UpToDate : UpdateCheckState
    data class Failed(val message: String) : UpdateCheckState
}

@Composable
private fun Pill(text: String, bg: androidx.compose.ui.graphics.Color,
                 fg: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(text = text, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = fg)
    }
}

/** 把 Markdown 更新说明压成纯文本，避免弹窗里一堆 `##` 和 `**` */
private fun plainNotes(md: String): String = md.lineSequence()
    .map { it.trim() }
    .map { line ->
        line
            .removePrefix("### ").removePrefix("## ").removePrefix("# ")
            .replace(Regex("\\[([^\\]]*)]\\([^)]*\\)"), "$1")
            .replace("**", "")
            .replace("`", "")
            .let { if (it.startsWith("- [ ] ")) "☐ " + it.removePrefix("- [ ] ")
                  else if (it.startsWith("- ")) "· " + it.removePrefix("- ")
                  else it }
    }
    .filter { it.isNotEmpty() }
    .joinToString("\n")

private fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "—"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / 1048576.0)
}
