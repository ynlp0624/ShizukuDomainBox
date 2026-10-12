package io.nekoshizuku.dnshe.desktop.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.nekoshizuku.dnshe.core.Account
import io.nekoshizuku.dnshe.core.ApiKeyInfo
import io.nekoshizuku.dnshe.core.AppCore
import io.nekoshizuku.dnshe.core.AppSettings
import io.nekoshizuku.dnshe.core.Quota
import io.nekoshizuku.dnshe.core.WhoisResponse
import io.nekoshizuku.dnshe.core.apiErrorMessage
import io.nekoshizuku.dnshe.desktop.Sys
import io.nekoshizuku.dnshe.desktop.i18n.LocalLang
import io.nekoshizuku.dnshe.desktop.i18n.Str
import io.nekoshizuku.dnshe.desktop.i18n.of
import io.nekoshizuku.dnshe.desktop.i18n.t
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/* ============================ 工具箱首页 ============================ */

@Composable
fun ToolsView(
    hasAccount: Boolean,
    onOpenQuota: () -> Unit,
    onOpenKeys: () -> Unit,
    onOpenWhois: () -> Unit,
    onOpenUpgrade: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(title = t(Str.ToolsTitle))
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val entries = listOf(
                Quad(Icons.Filled.Dns, Str.QuotaTitle, Str.ToolQuotaDesc, onOpenQuota, true),
                Quad(Icons.Filled.VpnKey, Str.KeysTitle, Str.ToolKeysDesc, onOpenKeys, true),
                Quad(Icons.Filled.Public, Str.WhoisTitle, Str.ToolWhoisDesc, onOpenWhois, false),
                Quad(Icons.Filled.RocketLaunch, Str.UpgradeTitle, Str.ToolUpgradeDesc, onOpenUpgrade, true),
            )
            entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { e ->
                        AppCard(modifier = Modifier.weight(1f), onClick = e.action) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        e.icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Column {
                                    Text(e.title.of(LocalLang.current), style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        e.desc.of(LocalLang.current),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (e.needsAccount && !hasAccount) {
                                        Spacer(Modifier.height(5.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Filled.Lock,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(12.dp),
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                t(Str.NoAccountForTool),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private data class Quad(
    val icon: ImageVector,
    val title: Str,
    val desc: Str,
    val action: () -> Unit,
    val needsAccount: Boolean,
)

/* ============================ 配额查询 ============================== */

@Composable
fun QuotaView(account: Account?, settings: AppSettings, onBack: () -> Unit) {
    val lang = LocalLang.current
    var quota by remember { mutableStateOf<Quota?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(account?.id, tick) {
        val acc = account ?: return@LaunchedEffect
        loading = quota == null
        error = null
        runCatching { AppCore.api.quota(acc.creds, settings.baseUrl, settings.timeoutSeconds) }
            .onSuccess { quota = it.quota; loading = false }
            .onFailure { error = apiErrorMessage(lang, it); loading = false }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(title = t(Str.QuotaTitle), onBack = onBack) {
            ToolbarButton(t(Str.Refresh)) { tick++ }
        }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                account == null -> EmptyState( text = t(Str.NoAccountForTool))
                loading -> LoadingBox()
                error != null -> ErrorBox(error!!, onRetry = { tick++ })
                quota == null -> EmptyState( text = t(Str.Empty))
                else -> {
                    val q = quota!!
                    val ratio = if (q.total > 0) q.used.toFloat() / q.total.toFloat() else 0f
                    Spacer(Modifier.height(10.dp))
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { ratio.coerceIn(0f, 1f) },
                            strokeWidth = 12.dp,
                            modifier = Modifier.size(170.dp),
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${q.available}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                            Text(t(Str.QuotaAvailable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile(t(Str.QuotaUsed), "${q.used}", Modifier.width(160.dp))
                        StatTile(t(Str.QuotaTotal), "${q.total}", Modifier.width(160.dp))
                        StatTile(t(Str.QuotaBase), "${q.base}", Modifier.width(160.dp))
                        StatTile(t(Str.QuotaBonus), "${q.inviteBonus}", Modifier.width(160.dp))
                    }
                }
            }
        }
    }
}

/* =========================== API 密钥管理 ============================ */

@Composable
fun ApiKeysView(
    account: Account?,
    settings: AppSettings,
    onBack: () -> Unit,
    onSaveAsAccount: (String, String, String) -> Unit,
    onToast: (String) -> Unit,
) {
    val lang = LocalLang.current
    val host = LocalOverlayHost.current
    val scope = rememberCoroutineScope()

    var keys by remember { mutableStateOf<List<ApiKeyInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(account?.id, tick) {
        val acc = account ?: return@LaunchedEffect
        loading = keys.isEmpty()
        error = null
        runCatching { AppCore.api.listKeys(acc.creds, settings.baseUrl, settings.timeoutSeconds) }
            .onSuccess { keys = it.keys; loading = false }
            .onFailure { error = apiErrorMessage(lang, it); loading = false }
    }

    fun showSecret(title: String, apiKey: String, apiSecret: String) {
        host.show {
            OverlayDialog(title = title, onDismiss = { host.dismiss() }, width = 620.dp) {
                Text(t(Str.KeyCreatedWarning), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                InfoRow(t(Str.ApiKey), apiKey, mono = true) { Sys.copyToClipboard(apiKey); onToast(t(Str.Copied)) }
                InfoRow(t(Str.ApiSecret), apiSecret, mono = true) { Sys.copyToClipboard(apiSecret); onToast(t(Str.Copied)) }
                DialogButtons(
                    onCancel = null,
                    onConfirm = { host.dismiss() },
                    confirmLabel = t(Str.Close),
                    extra = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { Sys.copyToClipboard("$apiKey\n$apiSecret"); onToast(t(Str.Copied)) }) {
                                Text(t(Str.Copy))
                            }
                            OutlinedButton(onClick = {
                                onSaveAsAccount(apiKey, apiSecret, t(Str.KeysTitle))
                                host.dismiss()
                            }) { Text(t(Str.SaveAsAccount)) }
                        }
                    },
                )
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(title = t(Str.KeysTitle), onBack = onBack) {
            ToolbarButton(t(Str.Refresh)) { tick++ }
            Button(
                enabled = account != null,
                onClick = {
                    host.show {
                        CreateKeyDialog(
                            account = account,
                            settings = settings,
                            onCreated = { k, s -> host.dismiss(); tick++; showSecret(t(Str.KeyCreatedTitle), k, s) },
                            onDismiss = { host.dismiss() },
                            onToast = onToast,
                        )
                    }
                },
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(t(Str.CreateKey))
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                account == null -> EmptyState( text = t(Str.NoAccountForTool))
                loading -> LoadingBox()
                error != null -> ErrorBox(error!!, onRetry = { tick++ })
                keys.isEmpty() -> EmptyState( text = t(Str.KeysEmpty))
                else -> keys.forEach { k ->
                    AppCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        k.keyName.ifBlank { "(unnamed)" },
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    StatusPill(k.status)
                                }
                                Spacer(Modifier.height(6.dp))
                                InfoRow(t(Str.ApiKey), k.apiKey, mono = true, onCopy = {
                                    Sys.copyToClipboard(k.apiKey); onToast(t(Str.Copied))
                                })
                                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                                    Text(
                                        "${t(Str.RequestCount)}: ${k.requestCount ?: 0}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        "${t(Str.LastUsed)}: ${k.lastUsedAt ?: "-"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        "${t(Str.CreatedAt)}: ${k.createdAt ?: "-"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            TextButton(onClick = {
                                host.show {
                                    ConfirmOverlay(
                                        title = t(Str.SecretRegenerate),
                                        text = t(Str.RegenerateConfirm, k.keyName),
                                        destructive = true,
                                        onConfirm = {
                                            host.dismiss()
                                            val acc = account ?: return@ConfirmOverlay
                                            scope.launch {
                                                runCatching {
                                                    AppCore.api.regenerateKey(acc.creds, settings.baseUrl, settings.timeoutSeconds, k.id)
                                                }.onSuccess { r ->
                                                    tick++
                                                    showSecret(t(Str.KeyRegeneratedTitle), r.apiKey ?: k.apiKey, r.apiSecret ?: "")
                                                }.onFailure { onToast(apiErrorMessage(lang, it)) }
                                            }
                                        },
                                        onDismiss = { host.dismiss() },
                                    )
                                }
                            }) { Text(t(Str.SecretRegenerate)) }
                            IconButton(onClick = {
                                host.show {
                                    ConfirmOverlay(
                                        title = t(Str.ConfirmDeleteTitle),
                                        text = t(Str.DeleteKeyConfirm, k.keyName),
                                        destructive = true,
                                        onConfirm = {
                                            host.dismiss()
                                            val acc = account ?: return@ConfirmOverlay
                                            scope.launch {
                                                runCatching {
                                                    AppCore.api.deleteKey(acc.creds, settings.baseUrl, settings.timeoutSeconds, k.id)
                                                }.onSuccess { onToast(t(Str.Success)); tick++ }
                                                    .onFailure { onToast(apiErrorMessage(lang, it)) }
                                            }
                                        },
                                        onDismiss = { host.dismiss() },
                                    )
                                }
                            }) {
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
            }
        }
    }
}

@Composable
private fun CreateKeyDialog(
    account: Account?,
    settings: AppSettings,
    onCreated: (String, String) -> Unit,
    onDismiss: () -> Unit,
    onToast: (String) -> Unit,
) {
    val lang = LocalLang.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var whitelist by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    OverlayDialog(title = t(Str.CreateKey), onDismiss = onDismiss, width = 560.dp) {
        AppTextField(name, { name = it }, t(Str.KeyName), placeholder = t(Str.KeyNameHint), hint = t(Str.Required))
        AppTextField(
            whitelist, { whitelist = it }, t(Str.IpWhitelist),
            placeholder = "192.168.1.1, 10.0.0.0/8",
            hint = t(Str.IpWhitelistHint) + " · " + t(Str.Optional),
            singleLine = false, maxLines = 3,
        )
        InlineMessage(message)
        DialogButtons(
            onCancel = onDismiss,
            confirmEnabled = !busy && name.isNotBlank(),
            confirmLabel = if (busy) t(Str.Loading) else t(Str.CreateKey),
            onConfirm = {
                val acc = account ?: return@DialogButtons
                busy = true
                message = null
                scope.launch {
                    runCatching {
                        AppCore.api.createKey(acc.creds, settings.baseUrl, settings.timeoutSeconds, name.trim(), whitelist.trim().ifBlank { null })
                    }.onSuccess { r ->
                        busy = false
                        val k = r.apiKey
                        val s = r.apiSecret
                        if (k != null && s != null) onCreated(k, s)
                        else { onToast(r.message ?: t(Str.Success)); onDismiss() }
                    }.onFailure { e ->
                        busy = false
                        message = apiErrorMessage(lang, e)
                    }
                }
            },
        )
    }
}

/* ============================= WHOIS =============================== */

@Composable
fun WhoisView(
    account: Account?,
    settings: AppSettings,
    onBack: () -> Unit,
    onToast: (String) -> Unit,
) {
    val lang = LocalLang.current
    val scope = rememberCoroutineScope()
    var domain by remember { mutableStateOf(account?.defaultRootDomain.orEmpty()) }
    var useAccount by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<WhoisResponse?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(title = t(Str.WhoisTitle), onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppTextField(
                value = domain,
                onValueChange = { domain = it.trim() },
                label = t(Str.WhoisDomainHint),
                modifier = Modifier.width(520.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.width(360.dp)) {
                    Text(t(Str.WhoisUseAccount), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        t(Str.WhoisPublicMode),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = useAccount, onCheckedChange = { useAccount = it })
            }
            if (account == null) {
                Text(t(Str.WhoisNeedKey), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Button(
                enabled = !busy && domain.isNotBlank(),
                onClick = {
                    busy = true
                    error = null
                    result = null
                    scope.launch {
                        runCatching {
                            AppCore.api.whois(
                                creds = if (useAccount) account?.creds else null,
                                baseUrl = settings.baseUrl,
                                timeoutSeconds = settings.timeoutSeconds,
                                domain = domain,
                            )
                        }.onSuccess { result = it; busy = false }
                            .onFailure { error = apiErrorMessage(lang, it); busy = false }
                    }
                },
            ) { Text(if (busy) t(Str.Loading) else t(Str.Query)) }

            if (busy) LoadingBox()
            error?.let { Box(modifier = Modifier.width(620.dp)) { ErrorBox(it) } }
            result?.let { r ->
                AppCard(modifier = Modifier.width(620.dp)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                r.domain ?: domain,
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.weight(1f),
                            )
                            StatusPill(r.status)
                        }
                        Spacer(Modifier.height(8.dp))
                        val registered = r.registered ?: (r.status != "unregistered")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (registered) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                                contentDescription = null,
                                tint = if (registered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (registered) t(Str.WhoisRegistered) else t(Str.WhoisUnregistered),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (registered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        InfoRow(t(Str.CreatedAt), r.registeredAt)
                        InfoRow(t(Str.ExpiresAt), r.expiresAt)
                        InfoRow(t(Str.WhoisRegistrant), r.registrantEmail)
                        if (r.nsList.isNotEmpty()) InfoRow(t(Str.WhoisNameservers), r.nsList.joinToString("\n"))
                        r.message?.let { InfoRow(t(Str.Details), it) }
                    }
                }
            }
        }
    }
}

/* ========================== 永久升级中心 ============================= */

@Composable
fun UpgradeView(
    account: Account?,
    settings: AppSettings,
    onBack: () -> Unit,
    onToast: (String) -> Unit,
) {
    val lang = LocalLang.current
    val host = LocalOverlayHost.current
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<JsonObject?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(account?.id, tick) {
        val acc = account ?: return@LaunchedEffect
        loading = state == null
        error = null
        runCatching { AppCore.api.upgradeList(acc.creds, settings.baseUrl, settings.timeoutSeconds) }
            .onSuccess { state = it.state as? JsonObject; loading = false }
            .onFailure { error = apiErrorMessage(lang, it); loading = false }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(title = t(Str.UpgradeTitle), onBack = onBack) {
            ToolbarButton(t(Str.Refresh)) { tick++ }
            Button(
                enabled = account != null,
                onClick = {
                    host.show {
                        InputOverlay(
                            title = t(Str.UpgradeApplyTitle),
                            label = t(Str.UpgradeSubdomainId),
                            onConfirm = { v ->
                                host.dismiss()
                                val acc = account ?: return@InputOverlay
                                val id = v.trim().toLongOrNull()
                                if (id == null) { onToast(t(Str.UpgradeSubdomainId) + " ?"); return@InputOverlay }
                                scope.launch {
                                    runCatching { AppCore.api.upgradeCreate(acc.creds, settings.baseUrl, settings.timeoutSeconds, id) }
                                        .onSuccess { onToast(it.message ?: t(Str.UpgradeSubmitted)); tick++ }
                                        .onFailure { onToast(apiErrorMessage(lang, it)) }
                                }
                            },
                            onDismiss = { host.dismiss() },
                        )
                    }
                },
            ) { Text(t(Str.UpgradeApplyTitle)) }
            OutlinedButton(onClick = {
                host.show {
                    InputOverlay(
                        title = t(Str.UpgradeAssistTitle),
                        label = t(Str.UpgradeAssistTitle),
                        hint = t(Str.UpgradeAssistHint),
                        onConfirm = { v ->
                            host.dismiss()
                            val acc = account ?: return@InputOverlay
                            scope.launch {
                                runCatching { AppCore.api.upgradeAssist(acc.creds, settings.baseUrl, settings.timeoutSeconds, v.trim()) }
                                    .onSuccess { onToast(it.message ?: t(Str.UpgradeAssistDone)); tick++ }
                                    .onFailure { onToast(apiErrorMessage(lang, it)) }
                            }
                        },
                        onDismiss = { host.dismiss() },
                    )
                }
            }) { Text(t(Str.UpgradeAssistTitle)) }
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                account == null -> EmptyState( text = t(Str.NoAccountForTool))
                loading -> LoadingBox()
                error != null -> ErrorBox(error!!, onRetry = { tick++ })
                state == null -> EmptyState( text = t(Str.UpgradeEmpty))
                else -> {
                    val s = state
                    val assistRequired = s.intOf("assist_required")
                    val assistCount = s.intOf("helper_assist_count")
                    val assistLimit = s.intOf("helper_assist_limit")
                    val assistRemaining = s.intOf("helper_assist_remaining")
                    val pending = s.intOf("pending_count")
                    val upgraded = s.intOf("upgraded_count")

                    if (assistRequired != null || assistLimit != null || pending != null) {
                        AppCard {
                            Column {
                                if (assistRequired != null && assistCount != null) {
                                    Text(t(Str.UpgradeAssistQuota, assistCount, assistRequired), style = MaterialTheme.typography.bodyMedium)
                                }
                                if (assistLimit != null && assistRemaining != null) {
                                    Text(
                                        t(Str.UpgradeHelperQuota, (assistLimit - assistRemaining).coerceAtLeast(0), assistLimit),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (pending != null && upgraded != null) {
                                    Text(
                                        t(Str.UpgradePending, pending, upgraded),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    JsonSection(t(Str.UpgradeRequests), s.listOf("requests"), onCancel = { rid ->
                        host.show {
                            ConfirmOverlay(
                                title = t(Str.UpgradeCancel),
                                text = t(Str.UpgradeCancelConfirm, rid),
                                destructive = true,
                                onConfirm = {
                                    host.dismiss()
                                    val acc = account ?: return@ConfirmOverlay
                                    scope.launch {
                                        runCatching { AppCore.api.upgradeCancel(acc.creds, settings.baseUrl, settings.timeoutSeconds, rid) }
                                            .onSuccess { onToast(it.message ?: t(Str.UpgradeCancelled)); tick++ }
                                            .onFailure { onToast(apiErrorMessage(lang, it)) }
                                    }
                                },
                                onDismiss = { host.dismiss() },
                            )
                        }
                    })
                    JsonSection(t(Str.UpgradeEligible), s.listOf("eligible_domains"))
                    JsonSection(t(Str.UpgradeLogs), s.listOf("assist_logs"))
                    JsonSection(t(Str.UpgradeFeed), s.listOf("recent_success_feed"))
                }
            }
        }
    }
}

@Composable
private fun InputOverlay(
    title: String,
    label: String,
    hint: String? = null,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf("") }
    OverlayDialog(title = title, onDismiss = onDismiss, width = 460.dp) {
        AppTextField(value, { value = it }, label, hint = hint)
        DialogButtons(onCancel = onDismiss, onConfirm = { onConfirm(value) })
    }
}

private fun JsonObject?.intOf(key: String): Int? =
    (this?.get(key) as? JsonPrimitive)?.content?.toIntOrNull()

private fun JsonObject?.listOf(key: String): List<JsonElement> =
    (this?.get(key) as? JsonArray)?.toList() ?: emptyList()

private fun JsonElement.pick(vararg keys: String): String? {
    val o = this as? JsonObject ?: return null
    keys.forEach { k ->
        val v = o[k] ?: return@forEach
        val s = if (v is JsonPrimitive) v.content else v.toString()
        if (s.isNotBlank()) return s
    }
    return null
}

@Composable
private fun JsonSection(title: String, items: List<JsonElement>, onCancel: ((Long) -> Unit)? = null) {
    SectionTitle(title)
    if (items.isEmpty()) {
        AppCard {
            Text(t(Str.UpgradeEmpty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    items.forEach { el ->
        AppCard {
            Column {
                val idText = el.pick("request_id", "id")
                val subId = el.pick("subdomain_id", "subdomain")
                val status = el.pick("status", "state")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        buildString {
                            if (idText != null) append("#").append(idText)
                            if (subId != null) {
                                if (isNotEmpty()) append(" · ")
                                append(subId)
                            }
                            if (isEmpty()) append(t(Str.Details))
                        },
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (status != null) StatusPill(status)
                    if (onCancel != null && idText != null) {
                        val rid = idText.toLongOrNull()
                        if (rid != null && status?.lowercase() in setOf("pending", "waiting", "processing", "queued", "reviewing")) {
                            TextButton(onClick = { onCancel(rid) }) {
                                Text(t(Str.UpgradeCancel), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(5.dp))
                if (el is JsonObject) {
                    el.forEach { (k, v) ->
                        if (k in setOf("request_id", "id", "subdomain_id", "status", "state")) return@forEach
                        val text = when (v) {
                            is JsonPrimitive -> v.content
                            is JsonArray -> v.joinToString(", ") { it.jsonPrimitive.content }
                            else -> v.toString()
                        }
                        InfoRow(k, text, mono = text.length < 64)
                    }
                }
            }
        }
    }
}
