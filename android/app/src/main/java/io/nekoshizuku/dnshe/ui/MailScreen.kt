package io.nekoshizuku.dnshe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.nekoshizuku.dnshe.data.MailApi
import io.nekoshizuku.dnshe.data.MailCompose
import io.nekoshizuku.dnshe.data.MailFolder
import io.nekoshizuku.dnshe.data.MailMessage
import io.nekoshizuku.dnshe.data.MailboxAccount
import io.nekoshizuku.dnshe.i18n.Str
import io.nekoshizuku.dnshe.i18n.t
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Email
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Send

/**
 * 邮箱主界面：收件箱 / 已发送 两个 tab。
 *
 * 数据来自 mail.v.cd 的开放 API（逆向出来的，官方暂无文档）。
 */
@Composable
fun MailScreen(
    mailbox: MailboxAccount?,
    api: MailApi,
    onBack: () -> Unit,
    onCompose: () -> Unit,
    onOpenMessage: (MailMessage) -> Unit,
    onToast: (String) -> Unit,
    onNeedAccount: () -> Unit,
) {
    var folder by remember { mutableStateOf(MailFolder.INBOX) }
    var messages by remember { mutableStateOf<List<MailMessage>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(mailbox?.id, folder, tick) {
        val mb = mailbox ?: return@LaunchedEffect
        if (mb.token.isBlank() || mb.mailboxId.isBlank()) return@LaunchedEffect
        loading = true
        error = null
        runCatching {
            api.listMessages(mb.token, mb.mailboxId, folder = folder, limit = 40)
        }.onSuccess {
            messages = it.items
            loading = false
        }.onFailure {
            error = it.message
            loading = false
        }
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = t(Str.MailTitle),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = t(Str.Back), modifier = Modifier.size(24.dp))
                    }
                },
                actions = {
                    IconButton(onClick = { tick++ }) {
                        Icon(MiuixIcons.Refresh, contentDescription = t(Str.Refresh), modifier = Modifier.size(24.dp))
                    }
                    IconButton(onClick = { if (mailbox == null) onNeedAccount() else onCompose() }) {
                        Icon(MiuixIcons.Send, contentDescription = t(Str.MailCompose), modifier = Modifier.size(24.dp))
                    }
                },
                color = MiuixTheme.colorScheme.background,
            )
        },
    ) { pad ->
        if (mailbox == null) {
            Column(modifier = Modifier.fillMaxSize().padding(pad)) {
                EmptyState(
                    text = t(Str.MailNoAccount),
                    actionLabel = t(Str.MailGoAdd),
                    onAction = onNeedAccount,
                )
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            // 顶部账号信息 + 文件夹切换
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = mailbox.address,
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.weight(1f),
                )
                FolderChip(
                    label = t(Str.MailInbox),
                    selected = folder == MailFolder.INBOX,
                    onClick = { folder = MailFolder.INBOX },
                )
                Spacer(Modifier.width(8.dp))
                FolderChip(
                    label = t(Str.MailSent),
                    selected = folder == MailFolder.SENT,
                    onClick = { folder = MailFolder.SENT },
                )
            }

            when {
                loading && messages.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                error != null -> ErrorBox(message = error!!, onRetry = { tick++ })

                messages.isEmpty() -> EmptyState(text = t(Str.MailEmpty))

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(messages, key = { it.id }) { msg ->
                        MailRow(msg) { onOpenMessage(msg) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceContainer
    val fg = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurfaceVariantSummary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal, color = fg)
    }
}

@Composable
private fun MailRow(msg: MailMessage, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            // 未读小圆点
            Box(
                modifier = Modifier
                    .padding(top = 6.dp, end = 8.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(
                        if (msg.isRead) Color.Transparent else MiuixTheme.colorScheme.primary
                    ),
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (msg.isSent) msg.to.firstOrNull().orEmpty() else msg.displayFrom,
                        fontSize = 14.sp,
                        fontWeight = if (msg.isRead) FontWeight.Normal else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (msg.hasAttachments) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            MiuixIcons.Email,
                            contentDescription = t(Str.MailHasAttachment),
                            modifier = Modifier.size(14.dp),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = shortTime(msg.sentAt.ifBlank { msg.receivedAt }),
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = msg.subject.ifBlank { t(Str.MailNoSubject) },
                    fontSize = 14.sp,
                    fontWeight = if (msg.isRead) FontWeight.Normal else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (msg.snippet.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = msg.snippet,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 把 ISO 时间裁成 MM-dd HH:mm */
private fun shortTime(iso: String): String {
    if (iso.length < 16) return iso
    return runCatching {
        // 2026-10-17T15:52:50Z
        val date = iso.substring(5, 10)      // 10-17
        val time = iso.substring(11, 16)     // 15:52
        "$date $time"
    }.getOrDefault(iso)
}
