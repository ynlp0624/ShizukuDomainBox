package io.nekoshizuku.dnshe.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.nekoshizuku.dnshe.data.ApiException
import io.nekoshizuku.dnshe.data.MailApi
import io.nekoshizuku.dnshe.data.MailAttachmentUpload
import io.nekoshizuku.dnshe.data.MailCompose
import io.nekoshizuku.dnshe.data.MailboxAccount
import io.nekoshizuku.dnshe.i18n.Str
import io.nekoshizuku.dnshe.i18n.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Base64
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back

/**
 * 写邮件界面。
 *
 * 注意 mail.v.cd 的限流是 **每分钟 5 封**，且服务端不返回 Retry-After，
 * 所以这里用 [MailApi.remainingQuota] 做本地计数提示。
 */
@Composable
fun MailComposeScreen(
    mailbox: MailboxAccount,
    api: MailApi,
    onBack: () -> Unit,
    onSent: () -> Unit,
    onToast: (String) -> Unit,
) {
    var to by remember { mutableStateOf("") }
    var cc by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var html by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var remaining by remember { mutableStateOf(api.remainingQuota()) }
    var attachments by remember { mutableStateOf<List<MailAttachmentUpload>>(emptyList()) }
    var loadingFiles by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    // 选文件 → 读成 base64（官方支持 attachments[].contentBase64，上限 20 个）
    val pickFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        loadingFiles = true
        scope.launch {
            val added = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri -> readAttachment(ctx, uri) }
            }
            attachments = (attachments + added).take(20)
            loadingFiles = false
            if (added.isEmpty()) onToast(t(Str.MailAttachFailed))
        }
    }

    fun parseAddrs(raw: String): List<String> =
        raw.split(',', ';', '\n', ' ')
            .map { it.trim() }
            .filter { it.contains("@") }
            .distinct()

    fun doSend() {
        val recipients = parseAddrs(to)
        if (recipients.isEmpty()) {
            onToast(t(Str.MailNoRecipient))
            return
        }
        if (api.remainingQuota() <= 0) {
            onToast(t(Str.MailRateLimited, api.secondsUntilNextSlot()))
            return
        }
        sending = true
        scope.launch {
            runCatching {
                api.send(
                    mailbox.token,
                    MailCompose(
                        mailboxId = mailbox.mailboxId,
                        to = recipients,
                        cc = parseAddrs(cc),
                        subject = subject.trim(),
                        text = text,
                        html = html.trim(),
                        attachments = attachments,
                    ),
                )
            }.onSuccess { r ->
                sending = false
                remaining = api.remainingQuota()
                onToast(if (r.isDelivered) t(Str.MailSentOk) else t(Str.MailQueued))
                onSent()
            }.onFailure { e ->
                sending = false
                remaining = api.remainingQuota()
                if (e is ApiException && e.httpStatus == 429) {
                    onToast(t(Str.MailRateLimited, api.secondsUntilNextSlot().coerceAtLeast(1)))
                } else {
                    onToast(t(Str.MailSendFailed) + ": " + (e.message ?: ""))
                }
            }
        }
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = t(Str.MailCompose),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = t(Str.Back), modifier = Modifier.size(24.dp))
                    }
                },
                actions = {
                    if (sending) {
                        Box(modifier = Modifier.padding(end = 14.dp).size(22.dp)) {
                            CircularProgressIndicator(strokeWidth = 2.dp, size = 22.dp)
                        }
                    } else {
                        TextButton(
                            text = t(Str.MailSend),
                            onClick = { doSend() },
                        )
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
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            // 发件人（只读展示）
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    t(Str.MailFrom),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.width(64.dp),
                )
                Text(mailbox.address, fontSize = 14.sp)
            }

            // 收件人
            AppTextField(
                value = to,
                onValueChange = { raw ->
                    // 收件人里可能混入全角 @ . ，统一转半角
                    to = raw.map { ch ->
                        when (ch) {
                            '\uFF20' -> '@'
                            '\u3002' -> '.'
                            '\uFF0E' -> '.'
                            '\uFF0C' -> ','
                            '\u3001' -> ','
                            '\uFF1B' -> ';'
                            '\uFF0F' -> '/'
                            '\uFF0B' -> '+'
                            '\uFF0D' -> '-'
                            '\uFF3F' -> '_'
                            else -> ch
                        }
                    }.joinToString("")
                },
                label = t(Str.MailTo),
                hint = t(Str.MailToHint),
            )

            Spacer(Modifier.height(10.dp))

            if (showAdvanced) {
                AppTextField(
                    value = cc,
                    onValueChange = { raw ->
                        cc = raw.map { ch ->
                            when (ch) {
                                '\uFF20' -> '@'
                                '\u3002' -> '.'
                                '\uFF0E' -> '.'
                                '\uFF0C' -> ','
                                '\u3001' -> ','
                                '\uFF1B' -> ';'
                                else -> ch
                            }
                        }.joinToString("")
                    },
                    label = t(Str.MailCc),
                    hint = t(Str.MailToHint),
                )
                Spacer(Modifier.height(10.dp))
            }

            AppTextField(
                value = subject,
                onValueChange = { subject = it },
                label = t(Str.MailSubject),
            )

            Spacer(Modifier.height(10.dp))

            AppTextField(
                value = text,
                onValueChange = { text = it },
                label = t(Str.MailBody),
                singleLine = false,
                maxLines = 12,
            )

            Spacer(Modifier.height(8.dp))

            // 附件
            AttachmentSection(
                attachments = attachments,
                loading = loadingFiles,
                onPick = { pickFiles.launch(arrayOf("*/*")) },
                onRemove = { i -> attachments = attachments.filterIndexed { idx, _ -> idx != i } },
            )

            Spacer(Modifier.height(4.dp))

            if (showAdvanced) {
                AppTextField(
                    value = html,
                    onValueChange = { html = it },
                    label = t(Str.MailBodyHtml),
                    singleLine = false,
                    maxLines = 6,
                )
                Spacer(Modifier.height(10.dp))
            }

            // 高级选项开关 + 限流提示
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    text = if (showAdvanced) t(Str.MailCollapseAdvanced) else t(Str.MailExpandAdvanced),
                    onClick = { showAdvanced = !showAdvanced },
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = t(Str.MailQuotaFmt, remaining),
                    fontSize = 11.sp,
                    color = if (remaining > 0) {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    } else {
                        MiuixTheme.colorScheme.error
                    },
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = t(Str.MailRateHint),
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { doSend() },
                enabled = !sending && to.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (sending) t(Str.MailSending) else t(Str.MailSend))
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}


/**
 * 把选中的文件读成 base64。
 *
 * 官方限制：单个附件走 JSON 内联，总大小受账户限制；这里保守限制单文件 8 MB。
 */
private fun readAttachment(ctx: android.content.Context, uri: Uri): MailAttachmentUpload? =
    runCatching {
        val resolver = ctx.contentResolver
        val name = queryDisplayName(ctx, uri) ?: "attachment"
        val type = resolver.getType(uri) ?: "application/octet-stream"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
        if (bytes.isEmpty() || bytes.size > 8 * 1024 * 1024) return@runCatching null
        MailAttachmentUpload(
            filename = name,
            contentBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
            contentType = type,
        )
    }.getOrNull()

private fun queryDisplayName(ctx: android.content.Context, uri: Uri): String? =
    runCatching {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    }.getOrNull()

/**
 * 附件区：加附件按钮 + 已选列表（可删）。
 */
@Composable
private fun AttachmentSection(
    attachments: List<MailAttachmentUpload>,
    loading: Boolean,
    onPick: () -> Unit,
    onRemove: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                text = if (loading) t(Str.MailAttaching) else t(Str.MailAddAttachment),
                onClick = onPick,
                enabled = !loading && attachments.size < 20,
            )
            Spacer(Modifier.weight(1f))
            if (attachments.isNotEmpty()) {
                Text(
                    text = t(Str.MailAttachedCount, attachments.size),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        attachments.forEachIndexed { i, a ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = a.filename, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(
                        text = humanSize(a.contentBase64),
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                TextButton(text = t(Str.Delete), onClick = { onRemove(i) })
            }
        }
    }
}

/** base64 长度换算回原始字节数，用于显示 */
private fun humanSize(b64: String): String {
    val bytes = (b64.length / 4) * 3 -
        (if (b64.endsWith("==")) 2 else if (b64.endsWith("=")) 1 else 0)
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / 1048576.0)
    }
}
