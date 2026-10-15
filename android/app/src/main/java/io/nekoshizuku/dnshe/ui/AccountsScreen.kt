package io.nekoshizuku.dnshe.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.nekoshizuku.dnshe.AppLinks
import io.nekoshizuku.dnshe.R
import io.nekoshizuku.dnshe.data.Account
import io.nekoshizuku.dnshe.data.AppContainer
import io.nekoshizuku.dnshe.data.Creds
import io.nekoshizuku.dnshe.data.apiErrorMessage
import io.nekoshizuku.dnshe.i18n.LocalLang
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import io.nekoshizuku.dnshe.i18n.Str
import io.nekoshizuku.dnshe.i18n.fmt
import io.nekoshizuku.dnshe.i18n.t
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ========================= 剪贴板密钥识别 ========================= */

private val KEY_REGEX = Regex("""(cfsd_[A-Za-z0-9_\-]{4,})""")
private val JSON_KEY = Regex(""""api_key"\s*:\s*"([^"]+)"""")
private val JSON_SECRET = Regex(""""api_secret"\s*:\s*"([^"]+)"""")
private val HDR_KEY = Regex("""X-API-Key\s*:\s*(\S+)""", RegexOption.IGNORE_CASE)
private val HDR_SECRET = Regex("""X-API-Secret\s*:\s*(\S+)""", RegexOption.IGNORE_CASE)
private val BARE_SECRET = Regex("""^[A-Za-z0-9]{12,80}$""")

data class DetectedCredential(val key: String, val secret: String?)

/** 从任意文本里认 API Key / Secret，认不出来就返回 null */
fun detectCredential(raw: String?): DetectedCredential? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return null

    val key = JSON_KEY.find(text)?.groupValues?.get(1)
        ?: HDR_KEY.find(text)?.groupValues?.get(1)
        ?: KEY_REGEX.find(text)?.groupValues?.get(1)
        ?: return null

    val secret = JSON_SECRET.find(text)?.groupValues?.get(1)
        ?: HDR_SECRET.find(text)?.groupValues?.get(1)

    val resolvedSecret = secret ?: text.lines()
        .map { it.trim().trim(',', '"', '\'') }
        .firstOrNull { line ->
            line != key && !line.contains("cfsd_") && BARE_SECRET.matches(line) &&
                !line.startsWith("{") && !line.startsWith("X-API", ignoreCase = true) &&
                !line.contains("api_key", ignoreCase = true)
        }
        ?: text.split(Regex("""[\s,;"']+"""))
            .firstOrNull { it != key && !it.contains("cfsd_") && BARE_SECRET.matches(it) }

    return DetectedCredential(key.trim(), resolvedSecret?.trim())
}

private fun Context.readClipboard(): String? = runCatching {
    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = cm.primaryClip ?: return@runCatching null
    if (clip.itemCount == 0) return@runCatching null
    clip.getItemAt(0).coerceToText(this)?.toString()
}.getOrNull()

fun Context.copyToClipboard(label: String, text: String) {
    runCatching {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}

fun Context.openUrl(url: String) {
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/* ============================ 账号页 ============================== */

@Composable
fun AccountsScreen(
    accounts: List<Account>,
    currentId: String?,
    brokenIds: Set<String>,
    clipboardDetect: Boolean,
    onSelect: (String) -> Unit,
    onSave: (Account) -> Unit,
    onDelete: (Account) -> Unit,
    onToast: (String) -> Unit,
) {
    val ctx = LocalContext.current
    var editing by remember { mutableStateOf<Account?>(null) }
    var creating by remember { mutableStateOf(false) }
    var pendingImportText by remember { mutableStateOf<String?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportPasswordDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val payload = runCatching {
            AppContainer.accounts.exportWithPassword(passwordInput.ifBlank { null })
        }.getOrElse {
            onToast(t(Str.ExportFailed, it.message ?: ""))
            return@rememberLauncherForActivityResult
        }
        val ok = runCatching {
            ctx.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(payload.toByteArray(Charsets.UTF_8))
            } != null
        }.getOrDefault(false)
        onToast(if (ok) t(Str.ExportDone) else t(Str.ExportFailed, "write failed"))
        passwordInput = ""
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (text.isNullOrBlank()) {
            onToast(t(Str.ImportFailed, "empty file"))
            return@rememberLauncherForActivityResult
        }
        if (AppContainer.accounts.looksEncrypted(text)) {
            pendingImportText = text
            passwordInput = ""
            showImportPasswordDialog = true
        } else {
            val r = AppContainer.accounts.importJson(text, null)
            onToast(r.message?.let { t(Str.ImportFailed, it) } ?: t(Str.ImportDone, r.added, r.updated, r.failed))
        }
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = t(Str.AccountsTitle),
                color = MiuixTheme.colorScheme.background,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Filled.Add, contentDescription = t(Str.AddAccount), modifier = Modifier.size(24.dp))
            }
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 14.dp, end = 14.dp, top = 4.dp, bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (accounts.isEmpty()) {
                item {
                    EmptyState(
                        text = t(Str.AccountsEmpty),
                        actionLabel = t(Str.OpenRegisterPage),
                        onAction = { ctx.openUrl(AppLinks.REGISTER) },
                    )
                }
            }

            items(accounts, key = { it.id }) { acc ->
                AccountCard(
                    account = acc,
                    isCurrent = acc.id == currentId,
                    broken = acc.id in brokenIds,
                    onSelect = { onSelect(acc.id) },
                    onEdit = { editing = acc },
                    onDelete = { onDelete(acc) },
                )
            }

            item {
                SectionTitle(t(Str.BackupTitle))
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            t(Str.KeystoreNote),
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { passwordInput = ""; showExportDialog = true },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_download),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(t(Str.ExportBackup))
                            }
                            Button(
                                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_upload),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(t(Str.ImportBackup))
                            }
                        }
                    }
                }
            }

            if (clipboardDetect) {
                item {
                    AppCard {
                        Column {
                            Text(
                                t(Str.PasteFromClipboard),
                                fontSize = 14.sp,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                t(Str.ClipboardDetectDesc),
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val det = detectCredential(ctx.readClipboard())
                                    if (det == null) {
                                        onToast(t(Str.ClipboardNoKey))
                                    } else if (det.secret != null) {
                                        onSave(
                                            Account(
                                                name = rec1(det.key),
                                                apiKey = det.key,
                                                apiSecret = det.secret,
                                            )
                                        )
                                        onToast(t(Str.ClipboardFound, rec1(det.key)))
                                    } else {
                                        editing = Account(name = "", apiKey = det.key, apiSecret = "")
                                        onToast(t(Str.ClipboardFound, rec1(det.key)))
                                    }
                                },
                            ) { Text(t(Str.ReadClipboard)) }
                        }
                    }
                }
            }

            item {
                AppCard {
                    Column {
                        Text(t(Str.NoAccountHint), fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(text = t(Str.OpenRegisterPage), onClick = { ctx.openUrl(AppLinks.REGISTER) })
                            TextButton(text = t(Str.OpenOfficialSite), onClick = { ctx.openUrl(AppLinks.SITE) })
                        }
                    }
                }
            }
        }
    }

    // 新增 / 编辑
    val target = editing
    if (creating || target != null) {
        AccountEditorDialog(
            initial = target ?: Account(),
            isNew = creating,
            onDismiss = { creating = false; editing = null },
            onSave = { acc ->
                onSave(acc)
                creating = false
                editing = null
            },
            onToast = onToast,
        )
    }

    if (showExportDialog) {
        InputDialog(
            title = t(Str.ExportBackup),
            label = t(Str.BackupPassword),
            hint = t(Str.BackupPasswordHint),
            initial = "",
            isSecret = true,
            onDismiss = { showExportDialog = false },
            onConfirm = { pwd ->
                passwordInput = pwd
                showExportDialog = false
                val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                exportLauncher.launch("dnshe-accounts-$stamp.json")
            },
        )
    }

    if (showImportPasswordDialog) {
        InputDialog(
            title = t(Str.ImportNeedPasswordShort),
            label = t(Str.BackupPassword),
            hint = t(Str.ImportNeedPassword),
            initial = "",
            isSecret = true,
            onDismiss = { showImportPasswordDialog = false; pendingImportText = null },
            onConfirm = { pwd ->
                val text = pendingImportText
                showImportPasswordDialog = false
                pendingImportText = null
                if (text != null) {
                    val r = AppContainer.accounts.importJson(text, pwd)
                    onToast(r.message?.let { t(Str.ImportFailed, it) } ?: t(Str.ImportDone, r.added, r.updated, r.failed))
                }
            },
        )
    }
}

private fun rec1(key: String): String = if (key.length <= 14) key else key.take(14) + "…"

@Composable
private fun AccountCard(
    account: Account,
    isCurrent: Boolean,
    broken: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    AppCard(
        onClick = onSelect,
        modifier = if (isCurrent) Modifier else Modifier,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = if (isCurrent) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = account.name.ifBlank { account.apiKey.take(12) },
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (isCurrent) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = t(Str.CurrentAccount),
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        t(Str.CurrentAccount),
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = account.maskedKey,
                fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )

            if (account.defaultRootDomain.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "@" + account.defaultRootDomain,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.secondary,
                )
            }

            if (broken) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = t(Str.AccountKeyBroken),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.error,
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isCurrent) {
                    TextButton(text = t(Str.SwitchTo), onClick = onSelect)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = t(Str.Edit), modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = t(Str.Delete),
                        tint = MiuixTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountEditorDialog(
    initial: Account,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (Account) -> Unit,
    onToast: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var key by remember { mutableStateOf(initial.apiKey) }
    var secret by remember { mutableStateOf(initial.apiSecret) }
    var root by remember { mutableStateOf(initial.defaultRootDomain) }
    var memo by remember { mutableStateOf(initial.memo) }
    var reveal by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val settings = AppContainer.settings.current
    val lang = LocalLang.current

    FullScreenDialog(
        title = if (isNew) t(Str.AddAccount) else t(Str.EditAccount),
        onDismiss = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppTextField(
                value = name,
                onValueChange = { name = it },
                label = t(Str.AccountName),
                placeholder = t(Str.AccountNameHint),
            )
            AppTextField(
                value = key,
                onValueChange = { key = it },
                label = t(Str.ApiKey),
                placeholder = t(Str.ApiKeyHint),
                hint = t(Str.Required),
                keyboardType = KeyboardType.Text,
            )
            AppTextField(
                value = secret,
                onValueChange = { secret = it },
                label = t(Str.ApiSecret),
                placeholder = t(Str.ApiSecretHint),
                hint = t(Str.Required),
                isSecret = true,
                revealSecret = reveal,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                               text = if (reveal) t(Str.HideSecret) else t(Str.ShowSecret),
                               onClick = { reveal = !reveal },
                               modifier = Modifier.weight(1f),
                )
                TextButton(
                               text = if (testing) t(Str.Loading) else t(Str.TestConnection),
                               onClick = {
                        if (key.isBlank() || secret.isBlank()) {
                            onToast(t(Str.ApiKeyRequired))
                            return@TextButton
                        }
                        testing = true
                        scope.launch {
                            val r = runCatching {
                                AppContainer.api.quota(
                                    io.nekoshizuku.dnshe.data.Creds(key.trim(), secret.trim()),
                                    settings.baseUrl,
                                    settings.timeoutSeconds,
                                )
                            }
                            testing = false
                            r.onSuccess { resp ->
                                val msg = t(Str.ConnectionOk, resp.quota?.available ?: 0)
                                message = msg
                                messageIsError = false
                                onToast(msg)
                            }.onFailure { e ->
                                val msg = Str.ConnectionFailed.fmt(lang, apiErrorMessage(lang, e))
                                message = msg
                                messageIsError = true
                                onToast(msg)
                            }
                        }
                    },
                               enabled = !testing,
                               modifier = Modifier.weight(1f),
                )
            }
            AppTextField(
                value = root,
                onValueChange = { root = it },
                label = t(Str.DefaultRootDomain),
                hint = t(Str.Optional),
            )
            AppTextField(
                value = memo,
                onValueChange = { memo = it },
                label = t(Str.Memo),
                hint = t(Str.Optional),
                singleLine = false,
                maxLines = 3,
            )

            Spacer(Modifier.height(4.dp))
        }
        InlineMessage(message, messageIsError, modifier = Modifier.padding(horizontal = 20.dp))
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
                TextButton(text = t(Str.Cancel), onClick = onDismiss, modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        if (key.isBlank() || secret.isBlank()) {
                            onToast(t(Str.ApiKeyRequired))
                            return@Button
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
                        onToast(t(Str.AccountSaved))
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(t(Str.Save)) }
        }
        Spacer(Modifier.height(6.dp))
        }
    }
}

/** 通用的「输入一行内容」对话框 */
@Composable
fun InputDialog(
    title: String,
    label: String,
    initial: String = "",
    hint: String? = null,
    isSecret: Boolean = false,
    confirmLabel: String = t(Str.Confirm),
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    var dlgShow by remember { mutableStateOf(true) }
    OverlayDialog(
        show = dlgShow,
        title = title,
        renderInRootScaffold = false,
        onDismissRequest = { dlgShow = false; onDismiss() },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            AppTextField(
                value = value,
                onValueChange = { value = it },
                label = label,
                hint = hint,
                isSecret = isSecret,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(
                    text = t(Str.Cancel),
                    onClick = { dlgShow = false; onDismiss() },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = confirmLabel,
                    onClick = { dlgShow = false; onConfirm(value) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
