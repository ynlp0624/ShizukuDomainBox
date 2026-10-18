package io.nekoshizuku.dnshe.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.nekoshizuku.dnshe.data.ApiException
import io.nekoshizuku.dnshe.data.MailApi
import io.nekoshizuku.dnshe.data.MailCompose
import io.nekoshizuku.dnshe.data.MailboxAccount
import io.nekoshizuku.dnshe.i18n.Str
import io.nekoshizuku.dnshe.i18n.t
import kotlinx.coroutines.launch
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

    val scope = rememberCoroutineScope()

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
                        to = recipients,
                        cc = parseAddrs(cc),
                        from = mailbox.address,
                        subject = subject.trim(),
                        text = text,
                        html = html.trim(),
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
