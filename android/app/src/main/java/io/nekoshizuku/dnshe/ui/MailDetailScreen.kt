package io.nekoshizuku.dnshe.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.nekoshizuku.dnshe.data.MailApi
import io.nekoshizuku.dnshe.data.MailAttachment
import io.nekoshizuku.dnshe.data.MailMessage
import io.nekoshizuku.dnshe.i18n.Str
import io.nekoshizuku.dnshe.i18n.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 邮件详情页 */
@Composable
fun MailDetailScreen(
    message: MailMessage,
    token: String,
    api: MailApi,
    onBack: () -> Unit,
    onToast: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf<String?>(null) }

    // 列表接口返回的 body 可能为空，详情页自己再拉一次完整的
    var full by remember { mutableStateOf<MailMessage?>(null) }
    var loadingBody by remember { mutableStateOf(false) }
    LaunchedEffect(message.id) {
        if (message.id.isBlank()) return@LaunchedEffect
        if (message.bodyText.isNotBlank() || message.bodyHtml.isNotBlank()) {
            full = message
            return@LaunchedEffect
        }
        loadingBody = true
        runCatching { api.getMessage(token, message.id) }
            .onSuccess { full = it }
            .onFailure { full = message }
        loadingBody = false
    }
    val msg = full ?: message

    fun saveAttachment(att: MailAttachment) {
        if (downloading != null) return
        downloading = att.id
        scope.launch {
            runCatching {
                val bytes = api.downloadAttachment(token, att.id)
                withContext(Dispatchers.IO) {
                    val dir = File(
                        ctx.getExternalFilesDir(null) ?: ctx.filesDir,
                        "mail-attachments",
                    ).apply { mkdirs() }
                    val safeName = att.filename.ifBlank { att.id }
                    val f = File(dir, safeName)
                    f.writeBytes(bytes)
                    f
                }
            }.onSuccess { f ->
                downloading = null
                onToast(t(Str.MailSaved) + "  " + f.absolutePath)
            }.onFailure {
                downloading = null
                onToast(t(Str.MailLoadFailed) + ": " + (it.message ?: ""))
            }
        }
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = t(Str.MailDetail),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = t(Str.Back), modifier = Modifier.size(24.dp))
                    }
                },
                color = MiuixTheme.colorScheme.background,
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            // 主题
            Text(
                text = msg.subject.ifBlank { t(Str.MailNoSubject) },
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))

            // 元信息卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    MetaRow(t(Str.MailFromLabel), msg.displayFrom)
                    MetaRow(t(Str.MailToLabel), msg.to.joinToString(", "))
                    if (!msg.cc.isNullOrEmpty()) {
                        MetaRow(t(Str.MailCc), msg.cc.joinToString(", "))
                    }
                    MetaRow(t(Str.MailTime), formatTime(msg.sentAt.ifBlank { msg.receivedAt }))
                    MetaRow(t(Str.MailSize), humanSize(msg.sizeBytes))
                }
            }

            // 验签
            if (msg.authentication.hasAnyResult) {
                Spacer(Modifier.height(12.dp))
                SectionTitle(t(Str.MailAuth))
                Spacer(Modifier.height(6.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AuthRow(t(Str.MailSpf), msg.authentication.spf)
                        AuthRow(t(Str.MailDkim), msg.authentication.dkim)
                        AuthRow(t(Str.MailDmarc), msg.authentication.dmarc)
                    }
                }
            }

            // 附件
            if (msg.attachments.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                SectionTitle(t(Str.MailAttachmentCount, msg.attachments.size))
                Spacer(Modifier.height(6.dp))
                msg.attachments.forEach { att ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        insideMargin = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    att.filename.ifBlank { att.id },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    humanSize(att.sizeBytes) + "  " + att.contentType,
                                    fontSize = 11.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            TextButton(
                                text = if (downloading == att.id) "…" else t(Str.MailDownload),
                                onClick = { saveAttachment(att) },
                                enabled = downloading == null,
                            )
                        }
                    }
                }
            }

            // 正文
            Spacer(Modifier.height(16.dp))
            SectionTitle(t(Str.MailBody))
            Spacer(Modifier.height(8.dp))

            val body = msg.bodyText.ifBlank {
                // 没有纯文本时，从 HTML 粗剥一下标签
                msg.bodyHtml
                    .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                    .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n")
                    .replace(Regex("<[^>]+>"), "")
                    .replace("&nbsp;", " ")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&amp;", "&")
                    .trim()
            }

            Text(
                text = body.ifBlank { t(Str.MailEmptyBody) },
                fontSize = 14.sp,
                lineHeight = 22.sp,
            )

            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row {
        Text(
            label,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.width(56.dp),
        )
        Text(value.ifBlank { "—" }, fontSize = 12.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun AuthRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.width(64.dp),
        )
        val v = value.ifBlank { "—" }
        val ok = v.equals("pass", true) || v.equals("valid", true)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(
                    when {
                        ok -> MiuixTheme.colorScheme.primaryContainer
                        v == "—" || v.equals("unknown", true) -> MiuixTheme.colorScheme.surfaceContainer
                        else -> MiuixTheme.colorScheme.errorContainer
                    }
                )
                .padding(horizontal = 9.dp, vertical = 2.dp),
        ) {
            Text(
                v,
                fontSize = 11.sp,
                color = when {
                    ok -> MiuixTheme.colorScheme.onPrimaryContainer
                    v == "—" || v.equals("unknown", true) -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                    else -> MiuixTheme.colorScheme.error
                },
            )
        }
    }
}

private fun formatTime(iso: String): String {
    if (iso.isBlank()) return "—"
    return runCatching {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val d = parser.parse(iso.take(19)) ?: return iso
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(d)
    }.getOrDefault(iso)
}

private fun humanSize(bytes: Long): String = when {
    bytes <= 0 -> "0 B"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
}
