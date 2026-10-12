package io.nekoshizuku.dnshe.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.nekoshizuku.dnshe.core.Account
import io.nekoshizuku.dnshe.core.AppCore
import io.nekoshizuku.dnshe.core.AppLinks
import io.nekoshizuku.dnshe.core.Creds
import io.nekoshizuku.dnshe.core.Dpapi
import io.nekoshizuku.dnshe.core.apiErrorMessage
import io.nekoshizuku.dnshe.desktop.Sys
import io.nekoshizuku.dnshe.desktop.detectCredential
import io.nekoshizuku.dnshe.desktop.i18n.LocalLang
import io.nekoshizuku.dnshe.desktop.i18n.Str
import io.nekoshizuku.dnshe.desktop.i18n.fmt
import io.nekoshizuku.dnshe.desktop.i18n.t
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AccountsView(
    accounts: List<Account>,
    currentId: String?,
    brokenIds: Set<String>,
    onToast: (String) -> Unit,
) {
    val host = LocalOverlayHost.current
    val settings by AppCore.settings.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(
            title = t(Str.AccountsTitle),
            subtitle = if (Dpapi.available) {
                t(Str.KeystoreNote)
            } else {
                t(Str.DpapiUnavailable)
            },
        ) {
            ToolbarButton(t(Str.PasteFromClipboard)) {
                val det = detectCredential(Sys.readClipboard())
                when {
                    det == null -> onToast(t(Str.ClipboardNoKey))
                    det.secret != null -> {
                        AppCore.accounts.add(Account(name = det.key.take(14), apiKey = det.key, apiSecret = det.secret))
                        onToast(t(Str.ClipboardFound, det.key.take(14)))
                    }
                    else -> host.show {
                        AccountEditorDialog(
                            initial = Account(apiKey = det.key),
                            isNew = true,
                            onSave = { acc -> AppCore.accounts.add(acc); onToast(t(Str.AccountSaved)); host.dismiss() },
                            onDismiss = { host.dismiss() },
                            onToast = onToast,
                        )
                    }
                }
            }
            ToolbarButton(t(Str.ExportBackup)) { doExport(accounts, onToast) }
            ToolbarButton(t(Str.ImportBackup)) {
                val f = Sys.pickOpenFile(t(Str.ImportBackup)) ?: return@ToolbarButton
                val text = runCatching { f.readText() }.getOrNull()
                if (text.isNullOrBlank()) {
                    onToast(t(Str.ImportFailed, "empty file"))
                    return@ToolbarButton
                }
                if (AppCore.accounts.looksEncrypted(text)) {
                    host.show {
                        PasswordDialog(
                            title = t(Str.ImportNeedPasswordShort),
                            hint = t(Str.ImportNeedPassword),
                            onConfirm = { pwd ->
                                val r = AppCore.accounts.importJson(text, pwd)
                                onToast(r.message?.let { t(Str.ImportFailed, it) } ?: t(Str.ImportDone, r.added, r.updated, r.failed))
                                host.dismiss()
                            },
                            onDismiss = { host.dismiss() },
                        )
                    }
                } else {
                    val r = AppCore.accounts.importJson(text, null)
                    onToast(r.message?.let { t(Str.ImportFailed, it) } ?: t(Str.ImportDone, r.added, r.updated, r.failed))
                }
            }
            Button(onClick = {
                host.show {
                    AccountEditorDialog(
                        initial = Account(defaultRootDomain = settings.defaultRootDomain),
                        isNew = true,
                        onSave = { acc -> AppCore.accounts.add(acc); onToast(t(Str.AccountSaved)); host.dismiss() },
                        onDismiss = { host.dismiss() },
                        onToast = onToast,
                    )
                }
            }) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(t(Str.AddAccount))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (accounts.isEmpty()) {
                EmptyState(
                    text = t(Str.AccountsEmpty),
                    actionLabel = t(Str.OpenRegisterPage),
                    onAction = { Sys.openUrl(AppLinks.REGISTER) },
                )
            }

            accounts.forEach { acc ->
                AccountRow(
                    account = acc,
                    isCurrent = acc.id == currentId,
                    broken = acc.id in brokenIds,
                    onSelect = { AppCore.accounts.select(acc.id) },
                    onEdit = {
                        host.show {
                            AccountEditorDialog(
                                initial = acc,
                                isNew = false,
                                onSave = { AppCore.accounts.update(it); onToast(t(Str.AccountSaved)); host.dismiss() },
                                onDismiss = { host.dismiss() },
                                onToast = onToast,
                            )
                        }
                    },
                    onDelete = {
                        host.show {
                            ConfirmOverlay(
                                title = t(Str.ConfirmDeleteTitle),
                                text = t(Str.DeleteAccountConfirm, acc.name.ifBlank { acc.maskedKey }),
                                destructive = true,
                                onConfirm = {
                                    AppCore.accounts.remove(acc.id)
                                    onToast(t(Str.AccountDeleted))
                                    host.dismiss()
                                },
                                onDismiss = { host.dismiss() },
                            )
                        }
                    },
                )
            }

            Spacer(Modifier.height(10.dp))
            SectionTitle(t(Str.BackupTitle))
            AppCard {
                Column {
                    Text(
                        t(Str.KeystoreNote) + "\n" + t(Str.JsonInteropNote),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { doExport(accounts, onToast) }) { Text(t(Str.ExportBackup)) }
                        OutlinedButton(onClick = {
                            val f = Sys.pickOpenFile(t(Str.ImportBackup)) ?: return@OutlinedButton
                            val text = runCatching { f.readText() }.getOrNull() ?: return@OutlinedButton
                            if (AppCore.accounts.looksEncrypted(text)) {
                                host.show {
                                    PasswordDialog(
                                        title = t(Str.ImportNeedPasswordShort),
                                        hint = t(Str.ImportNeedPassword),
                                        onConfirm = { pwd ->
                                            val r = AppCore.accounts.importJson(text, pwd)
                                            onToast(r.message?.let { t(Str.ImportFailed, it) } ?: t(Str.ImportDone, r.added, r.updated, r.failed))
                                            host.dismiss()
                                        },
                                        onDismiss = { host.dismiss() },
                                    )
                                }
                            } else {
                                val r = AppCore.accounts.importJson(text, null)
                                onToast(r.message?.let { t(Str.ImportFailed, it) } ?: t(Str.ImportDone, r.added, r.updated, r.failed))
                            }
                        }) { Text(t(Str.ImportBackup)) }
                        OutlinedButton(onClick = { Sys.openUrl(AppLinks.REGISTER) }) { Text(t(Str.OpenRegisterPage)) }
                        OutlinedButton(onClick = { Sys.openUrl(AppLinks.SITE) }) { Text(t(Str.OpenOfficialSite)) }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

private fun doExport(accounts: List<Account>, onToast: (String) -> Unit) {
    if (accounts.isEmpty()) {
        onToast(t(Str.AccountsEmpty))
        return
    }
    // 先问口令：简化起见，直接明文导出，想加密就用下面的按钮
    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
    val f = Sys.pickSaveFile("dnshe-accounts-$stamp.json", t(Str.ExportBackup)) ?: return
    val payload = runCatching { AppCore.accounts.exportWithPassword(null) }
        .getOrElse { onToast(t(Str.ExportFailed, it.message ?: "")); return }
    runCatching { f.writeText(payload) }
        .onSuccess { onToast(t(Str.ExportDone) + "  " + f.absolutePath) }
        .onFailure { onToast(t(Str.ExportFailed, it.message ?: "")) }
}

@Composable
private fun PasswordDialog(
    title: String,
    hint: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pwd by remember { mutableStateOf("") }
    OverlayDialog(title = title, onDismiss = onDismiss, width = 460.dp) {
        AppTextField(value = pwd, onValueChange = { pwd = it }, label = t(Str.BackupPassword), hint = hint, isSecret = true)
        DialogButtons(onCancel = onDismiss, onConfirm = { onConfirm(pwd) })
    }
}

@Composable
private fun AccountRow(
    account: Account,
    isCurrent: Boolean,
    broken: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    AppCard(
        onClick = onSelect,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = account.name.ifBlank { "?" }.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        account.name.ifBlank { account.apiKey.take(12) },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isCurrent) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            t(Str.CurrentAccount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    account.maskedKey,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (account.defaultRootDomain.isNotBlank()) {
                    Text(
                        "@" + account.defaultRootDomain,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                if (broken) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            t(Str.AccountKeyBroken),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            if (!isCurrent) {
                TextButton(onClick = onSelect) { Text(t(Str.SwitchTo)) }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = t(Str.Edit), modifier = Modifier.size(17.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = t(Str.Delete),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

@Composable
private fun AccountEditorDialog(
    initial: Account,
    isNew: Boolean,
    onSave: (Account) -> Unit,
    onDismiss: () -> Unit,
    onToast: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val lang = LocalLang.current
    val settings by AppCore.settings.state.collectAsState()

    var name by remember { mutableStateOf(initial.name) }
    var key by remember { mutableStateOf(initial.apiKey) }
    var secret by remember { mutableStateOf(initial.apiSecret) }
    var root by remember { mutableStateOf(initial.defaultRootDomain) }
    var memo by remember { mutableStateOf(initial.memo) }
    var reveal by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(true) }

    OverlayDialog(
        title = if (isNew) t(Str.AddAccount) else t(Str.EditAccount),
        onDismiss = onDismiss,
        width = 620.dp,
        maxHeight = 660.dp,
    ) {
        AppTextField(name, { name = it }, t(Str.AccountName), placeholder = t(Str.AccountNameHint))
        AppTextField(key, { key = it }, t(Str.ApiKey), placeholder = t(Str.ApiKeyHint), hint = t(Str.Required))
        AppTextField(
            secret, { secret = it }, t(Str.ApiSecret),
            placeholder = t(Str.ApiSecretHint), hint = t(Str.Required),
            isSecret = true, revealSecret = reveal,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { reveal = !reveal }, modifier = Modifier.weight(1f)) {
                Text(if (reveal) t(Str.HideSecret) else t(Str.ShowSecret))
            }
            OutlinedButton(
                onClick = {
                    if (key.isBlank() || secret.isBlank()) {
                        message = t(Str.ApiKeyRequired); messageIsError = true; return@OutlinedButton
                    }
                    testing = true
                    message = null
                    scope.launch {
                        val r = runCatching {
                            AppCore.api.quota(Creds(key.trim(), secret.trim()), settings.baseUrl, settings.timeoutSeconds)
                        }
                        testing = false
                        r.onSuccess {
                            message = t(Str.ConnectionOk, it.quota?.available ?: 0)
                            messageIsError = false
                        }.onFailure {
                            message = Str.ConnectionFailed.fmt(lang, apiErrorMessage(lang, it))
                            messageIsError = true
                        }
                    }
                },
                enabled = !testing,
                modifier = Modifier.weight(1f),
            ) { Text(if (testing) t(Str.Loading) else t(Str.TestConnection)) }
        }
        AppTextField(root, { root = it }, t(Str.DefaultRootDomain), hint = t(Str.Optional))
        AppTextField(memo, { memo = it }, t(Str.Memo), hint = t(Str.Optional), singleLine = false, maxLines = 3)
        InlineMessage(message, messageIsError)
        DialogButtons(
            onCancel = onDismiss,
            onConfirm = {
                if (key.isBlank() || secret.isBlank()) {
                    message = t(Str.ApiKeyRequired); messageIsError = true; return@DialogButtons
                }
                onSave(
                    initial.copy(
                        name = name.trim(),
                        apiKey = key.trim(),
                        apiSecret = secret.trim(),
                        defaultRootDomain = root.trim(),
                        memo = memo.trim(),
                    )
                )
            },
        )
    }
}
