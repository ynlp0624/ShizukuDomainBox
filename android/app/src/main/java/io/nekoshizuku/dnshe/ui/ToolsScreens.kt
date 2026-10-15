package io.nekoshizuku.dnshe.ui

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import io.nekoshizuku.dnshe.R
import io.nekoshizuku.dnshe.data.Account
import io.nekoshizuku.dnshe.data.ApiKeyInfo
import io.nekoshizuku.dnshe.data.AppContainer
import io.nekoshizuku.dnshe.data.AppSettings
import io.nekoshizuku.dnshe.data.Creds
import io.nekoshizuku.dnshe.data.Quota
import io.nekoshizuku.dnshe.data.WhoisResponse
import io.nekoshizuku.dnshe.data.apiErrorMessage
import io.nekoshizuku.dnshe.i18n.LocalLang
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import io.nekoshizuku.dnshe.i18n.Str
import io.nekoshizuku.dnshe.i18n.of
import io.nekoshizuku.dnshe.i18n.t
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

private val prettyJson = Json { prettyPrint = true }

fun JsonElement.pretty(): String = runCatching { prettyJson.encodeToString(JsonElement.serializer(), this) }.getOrDefault(toString())

fun JsonElement.pick(vararg keys: String): String? {
    val o = this as? JsonObject ?: return null
    keys.forEach { k ->
        val v = o[k] ?: return@forEach
        val s = when (v) {
            is JsonPrimitive -> if (v.isString) v.content else v.content
            else -> v.toString()
        }
        if (s.isNotBlank()) return s
    }
    return null
}

/** permanent_upgrade 的 state 里取数组 */
fun JsonObject?.listOf(key: String): List<JsonElement> =
    (this?.get(key) as? JsonArray)?.toList() ?: emptyList()

/** permanent_upgrade 的 state 里取数字 */
fun JsonObject?.intOf(key: String): Int? =
    (this?.get(key) as? JsonPrimitive)?.content?.toIntOrNull()

/* ============================ 工具箱首页 ============================ */

@Composable
fun ToolsScreen(
    hasAccount: Boolean,
    onOpenQuota: () -> Unit,
    onOpenKeys: () -> Unit,
    onOpenWhois: () -> Unit,
    onOpenUpgrade: () -> Unit,
) {
    val entries = listOf(
        Triple(R.drawable.ic_chart, Str.QuotaTitle, Str.ToolQuotaDesc) to onOpenQuota,
        Triple(R.drawable.ic_key, Str.KeysTitle, Str.ToolKeysDesc) to onOpenKeys,
        Triple(R.drawable.ic_globe, Str.WhoisTitle, Str.ToolWhoisDesc) to onOpenWhois,
        Triple(R.drawable.ic_upgrade, Str.UpgradeTitle, Str.ToolUpgradeDesc) to onOpenUpgrade,
    )

    Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = t(Str.ToolsTitle),
                color = MiuixTheme.colorScheme.background,
            )
        },
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(entries) { (data, action) ->
                val (icon, title, desc) = data
                AppCard(onClick = action) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Icon(
                            painterResource(icon),
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(title.of(LocalLang.current), fontSize = 16.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            desc.of(LocalLang.current),
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        if (!hasAccount && action != onOpenWhois) {
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.error,
                                    modifier = Modifier.size(12.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    t(Str.NoAccountForTool),
                                    fontSize = 11.sp,
                                    color = MiuixTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ============================ 配额查询 ============================== */

@Composable
fun QuotaScreen(
    account: Account?,
    settings: AppSettings,
    onBack: () -> Unit,
    onToast: (String) -> Unit,
) {
    val lang = LocalLang.current
    var quota by remember { mutableStateOf<Quota?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(account?.id, tick) {
        val acc = account ?: return@LaunchedEffect
        loading = quota == null
        error = null
        runCatching { AppContainer.api.quota(acc.creds, settings.baseUrl, settings.timeoutSeconds) }
            .onSuccess { quota = it.quota; loading = false }
            .onFailure { error = apiErrorMessage(lang, it); loading = false }
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = t(Str.QuotaTitle),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = t(Str.Back), modifier = Modifier.size(24.dp)) }
                },
                actions = {
                    IconButton(onClick = { tick++ }) { Icon(Icons.Filled.Refresh, contentDescription = t(Str.Refresh), modifier = Modifier.size(24.dp)) }
                },
                color = MiuixTheme.colorScheme.background,
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 22.dp),
        ) {
            when {
                loading -> LoadingBox()
                error != null -> ErrorBox(error!!, onRetry = { tick++ })
                quota == null -> EmptyState(text = t(Str.Empty), icon = { StateIcon(painterResource(R.drawable.ic_chart)) })
                else -> {
                    val q = quota!!
                    val ratio = if (q.total > 0) q.used.toFloat() / q.total.toFloat() else 0f
                    Spacer(Modifier.height(24.dp))
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator(
                            progress = ratio.coerceIn(0f, 1f),
                            strokeWidth = 12.dp,
                            modifier = Modifier.size(168.dp),
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${q.available}",
                                fontSize = 22.sp,
                                color = MiuixTheme.colorScheme.primary,
                            )
                            Text(
                                t(Str.QuotaAvailable),
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        InfoTile(t(Str.QuotaUsed), "${q.used}", modifier = Modifier.weight(1f))
                        InfoTile(t(Str.QuotaTotal), "${q.total}", modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        InfoTile(t(Str.QuotaBase), "${q.base}", modifier = Modifier.weight(1f))
                        InfoTile(t(Str.QuotaBonus), "${q.inviteBonus}", modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/* =========================== API 密钥管理 ============================ */

@Composable
fun ApiKeysScreen(
    account: Account?,
    settings: AppSettings,
    onBack: () -> Unit,
    onSaveAsAccount: (String, String, String) -> Unit,
    onToast: (String) -> Unit,
) {
    val lang = LocalLang.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var keys by remember { mutableStateOf<List<ApiKeyInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ApiKeyInfo?>(null) }
    var regenTarget by remember { mutableStateOf<ApiKeyInfo?>(null) }
    var secretResult by remember { mutableStateOf<Triple<String, String, String>?>(null) } // key, secret, title

    LaunchedEffect(account?.id, tick) {
        val acc = account ?: return@LaunchedEffect
        loading = keys.isEmpty()
        error = null
        runCatching { AppContainer.api.listKeys(acc.creds, settings.baseUrl, settings.timeoutSeconds) }
            .onSuccess { keys = it.keys; loading = false }
            .onFailure { error = apiErrorMessage(lang, it); loading = false }
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = t(Str.KeysTitle),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = t(Str.Back), modifier = Modifier.size(24.dp)) }
                },
                actions = {
                    IconButton(onClick = { tick++ }) { Icon(Icons.Filled.Refresh, contentDescription = t(Str.Refresh), modifier = Modifier.size(24.dp)) }
                },
                color = MiuixTheme.colorScheme.background,
            )
        },
        floatingActionButton = {
            if (account != null) {
                FloatingActionButton(onClick = { creating = true }) {
                    Icon(Icons.Filled.Add, contentDescription = t(Str.CreateKey), modifier = Modifier.size(24.dp))
                }
            }
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            if (account == null) {
                EmptyState(text = t(Str.NoAccountForTool), icon = { StateIcon(Icons.Filled.Lock) })
            } else when {
                loading -> LoadingBox()
                error != null -> ErrorBox(error!!, onRetry = { tick++ })
                keys.isEmpty() -> EmptyState(
                    text = t(Str.KeysEmpty),
                    actionLabel = t(Str.CreateKey),
                    onAction = { creating = true },
                    icon = { StateIcon(painterResource(R.drawable.ic_key)) },
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(keys, key = { it.id }) { k ->
                        AppCard {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        k.keyName.ifBlank { "(unnamed)" },
                                        fontSize = 16.sp,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    StatusPill(k.status)
                                }
                                Spacer(Modifier.height(6.dp))
                                KeyValueRow(t(Str.ApiKey), k.apiKey, mono = true) {
                                    ctx.copyToClipboard("api_key", k.apiKey)
                                    onToast(t(Str.Copied))
                                }
                                KeyValueRow(t(Str.RequestCount), k.requestCount?.toString())
                                KeyValueRow(t(Str.LastUsed), k.lastUsedAt)
                                KeyValueRow(t(Str.CreatedAt), k.createdAt)
                                Spacer(Modifier.height(6.dp))
                                Row {
                                    TextButton(text = t(Str.SecretRegenerate), onClick = { regenTarget = k })
                                    Spacer(Modifier.weight(1f))
                                    IconButton(onClick = { deleteTarget = k }) {
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
                }
            }
        }
    }

    if (creating) {
        CreateKeyDialog(
            account = account,
            settings = settings,
            onDismiss = { creating = false },
            onCreated = { key, secret ->
                creating = false
                secretResult = Triple(key, secret, t(Str.KeyCreatedTitle))
                tick++
            },
            onToast = onToast,
        )
    }

    deleteTarget?.let { k ->
        ConfirmDialog(
            title = t(Str.ConfirmDeleteTitle),
            text = t(Str.DeleteKeyConfirm, k.keyName),
            destructive = true,
            onConfirm = {
                val acc = account ?: return@ConfirmDialog
                scope.launch {
                    runCatching { AppContainer.api.deleteKey(acc.creds, settings.baseUrl, settings.timeoutSeconds, k.id) }
                        .onSuccess { onToast(t(Str.Success)); tick++ }
                        .onFailure { onToast(apiErrorMessage(lang, it)) }
                }
            },
            onDismiss = { deleteTarget = null },
        )
    }

    regenTarget?.let { k ->
        ConfirmDialog(
            title = t(Str.SecretRegenerate),
            text = t(Str.RegenerateConfirm, k.keyName),
            destructive = true,
            onConfirm = {
                val acc = account ?: return@ConfirmDialog
                scope.launch {
                    runCatching { AppContainer.api.regenerateKey(acc.creds, settings.baseUrl, settings.timeoutSeconds, k.id) }
                        .onSuccess { r ->
                            secretResult = Triple(r.apiKey ?: k.apiKey, r.apiSecret ?: "", t(Str.KeyRegeneratedTitle))
                            tick++
                        }
                        .onFailure { onToast(apiErrorMessage(lang, it)) }
                }
            },
            onDismiss = { regenTarget = null },
        )
    }

    secretResult?.let { (key, secret, title) ->
        SecretResultDialog(
            title = title,
            apiKey = key,
            apiSecret = secret,
            onSaveAsAccount = {
                onSaveAsAccount(key, secret, t(Str.KeysTitle))
                secretResult = null
            },
            onDismiss = { secretResult = null },
        )
    }
}

@Composable
private fun CreateKeyDialog(
    account: Account?,
    settings: AppSettings,
    onDismiss: () -> Unit,
    onCreated: (String, String) -> Unit,
    onToast: (String) -> Unit,
) {
    val lang = LocalLang.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var whitelist by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    FullScreenDialog(title = t(Str.CreateKey), onDismiss = onDismiss) {
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
                label = t(Str.KeyName),
                placeholder = t(Str.KeyNameHint),
                hint = t(Str.Required),
            )
            AppTextField(
                value = whitelist,
                onValueChange = { whitelist = it },
                label = t(Str.IpWhitelist),
                placeholder = "192.168.1.1, 10.0.0.0/8",
                hint = t(Str.IpWhitelistHint) + " · " + t(Str.Optional),
                singleLine = false,
                maxLines = 3,
            )
            Spacer(Modifier.height(6.dp))
        }
        InlineMessage(message, modifier = Modifier.padding(horizontal = 20.dp))
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
                TextButton(text = t(Str.Cancel), onClick = onDismiss, modifier = Modifier.weight(1f))
                Button(
                    enabled = !busy && name.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val acc = account ?: return@Button
                        busy = true
                        scope.launch {
                            runCatching {
                                AppContainer.api.createKey(
                                    acc.creds, settings.baseUrl, settings.timeoutSeconds,
                                    name.trim(), whitelist.trim().ifBlank { null },
                                )
                            }.onSuccess { r ->
                                busy = false
                                if (r.apiKey != null && r.apiSecret != null) {
                                    onCreated(r.apiKey, r.apiSecret)
                                } else {
                                    onToast(r.message ?: t(Str.Success))
                                    onDismiss()
                                }
                            }.onFailure { e ->
                                busy = false
                                val msg = apiErrorMessage(lang, e)
                                message = msg
                                onToast(msg)
                            }
                        }
                    },
                ) { Text(if (busy) t(Str.Loading) else t(Str.CreateKey)) }
        }
        Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun SecretResultDialog(
    title: String,
    apiKey: String,
    apiSecret: String,
    onSaveAsAccount: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    var dlgShow by remember { mutableStateOf(true) }
    OverlayDialog(
        show = dlgShow,
        title = title,
        renderInRootScaffold = false,
        onDismissRequest = { dlgShow = false; onDismiss() },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                t(Str.KeyCreatedWarning),
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.error,
            )
            KeyValueRow(t(Str.ApiKey), apiKey, mono = true) {
                ctx.copyToClipboard("api_key", apiKey)
            }
            KeyValueRow(t(Str.ApiSecret), apiSecret, mono = true) {
                ctx.copyToClipboard("api_secret", apiSecret)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    text = t(Str.Copy),
                    onClick = { ctx.copyToClipboard("dnshe", "$apiKey\n$apiSecret") },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = t(Str.SaveKeyAsAccount),
                    onClick = onSaveAsAccount,
                    modifier = Modifier.weight(1f),
                )
            }
            TextButton(
                text = t(Str.Close),
                onClick = { dlgShow = false; onDismiss() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/* ============================= WHOIS =============================== */

@Composable
fun WhoisScreen(
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

    Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = t(Str.WhoisTitle),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = t(Str.Back), modifier = Modifier.size(24.dp)) }
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppTextField(
                value = domain,
                onValueChange = { domain = it.trim() },
                label = t(Str.WhoisTitle),
                placeholder = t(Str.WhoisDomainHint),
                keyboardType = KeyboardType.Uri,
            )

            if (account != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(t(Str.WhoisUseAccount), fontSize = 14.sp)
                        Text(
                            t(Str.WhoisPublicMode),
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Switch(checked = useAccount, onCheckedChange = { useAccount = it })
                }
            } else {
                Text(
                    t(Str.WhoisNeedKey),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.error,
                )
            }

            Button(
                enabled = !busy && domain.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    busy = true
                    error = null
                    result = null
                    scope.launch {
                        runCatching {
                            AppContainer.api.whois(
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
            error?.let { ErrorBox(it) }
            result?.let { r ->
                AppCard {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                r.domain ?: domain,
                                fontSize = 16.sp, fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f),
                            )
                            StatusPill(r.status)
                        }
                        Spacer(Modifier.height(6.dp))
                        val registered = r.registered ?: (r.status != "unregistered")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (registered) Icons.Filled.CheckCircle else Icons.Filled.Close,
                                contentDescription = null,
                                tint = if (registered) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.size(17.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (registered) t(Str.WhoisRegistered) else t(Str.WhoisUnregistered),
                                fontSize = 14.sp,
                                color = if (registered) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        KeyValueRow(t(Str.CreatedAt), r.registeredAt)
                        KeyValueRow(t(Str.ExpiresAt), r.expiresAt)
                        KeyValueRow(t(Str.WhoisRegistrant), r.registrantEmail)
                        if (r.nsList.isNotEmpty()) {
                            KeyValueRow(t(Str.WhoisNameservers), r.nsList.joinToString("\n"))
                        }
                        r.rateLimit?.let { rl ->
                            if (rl.remaining != null && rl.limit != null) {
                                Text(
                                    t(Str.WhoisRateLimit, rl.remaining, rl.limit),
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                        r.message?.let { KeyValueRow(t(Str.Details), it) }
                    }
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

/* ========================== 永久升级中心 ============================= */

@Composable
fun UpgradeScreen(
    account: Account?,
    settings: AppSettings,
    onBack: () -> Unit,
    onToast: (String) -> Unit,
) {
    val lang = LocalLang.current
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<JsonObject?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }
    var showApply by remember { mutableStateOf(false) }
    var showAssist by remember { mutableStateOf(false) }
    var cancelTarget by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(account?.id, tick) {
        val acc = account ?: return@LaunchedEffect
        loading = state == null
        error = null
        runCatching { AppContainer.api.upgradeList(acc.creds, settings.baseUrl, settings.timeoutSeconds) }
            .onSuccess { state = it.state as? JsonObject; loading = false }
            .onFailure { error = apiErrorMessage(lang, it); loading = false }
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = t(Str.UpgradeTitle),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = t(Str.Back), modifier = Modifier.size(24.dp)) }
                },
                actions = {
                    IconButton(onClick = { tick++ }) { Icon(Icons.Filled.Refresh, contentDescription = t(Str.Refresh), modifier = Modifier.size(24.dp)) }
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
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (account == null) {
                EmptyState(text = t(Str.NoAccountForTool), icon = { StateIcon(Icons.Filled.Lock) })
                return@Column
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { showApply = true }, modifier = Modifier.weight(1f)) { Text(t(Str.UpgradeApplyTitle)) }
                TextButton(
                               text = t(Str.UpgradeAssistTitle),
                               onClick = { showAssist = true },
                               modifier = Modifier.weight(1f),
                )
            }

            when {
                loading -> LoadingBox()
                error != null -> ErrorBox(error!!, onRetry = { tick++ })
                state == null -> EmptyState(text = t(Str.UpgradeEmpty), icon = { StateIcon(painterResource(R.drawable.ic_upgrade)) })
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
                                    Text(
                                        t(Str.UpgradeAssistQuota, assistCount, assistRequired),
                                        fontSize = 14.sp,
                                    )
                                }
                                if (assistLimit != null && assistRemaining != null) {
                                    Text(
                                        t(Str.UpgradeHelperQuota, (assistLimit - assistRemaining).coerceAtLeast(0), assistLimit),
                                        fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                                if (pending != null && upgraded != null) {
                                    Text(
                                        t(Str.UpgradePending, pending, upgraded),
                                        fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                            }
                        }
                    }

                    JsonSection(
                        title = t(Str.UpgradeRequests),
                        items = s.listOf("requests"),
                        onCancel = { id -> cancelTarget = id },
                    )
                    JsonSection(title = t(Str.UpgradeEligible), items = s.listOf("eligible_domains"))
                    JsonSection(title = t(Str.UpgradeLogs), items = s.listOf("assist_logs"))
                    JsonSection(title = t(Str.UpgradeFeed), items = s.listOf("recent_success_feed"))
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }

    if (showApply) {
        InputDialog(
            title = t(Str.UpgradeApplyTitle),
            label = t(Str.UpgradeSubdomainId),
            initial = "",
            onDismiss = { showApply = false },
            onConfirm = { v ->
                val acc = account ?: return@InputDialog
                val id = v.trim().toLongOrNull()
                if (id == null) {
                    onToast(t(Str.UpgradeSubdomainId) + " ?")
                    return@InputDialog
                }
                scope.launch {
                    runCatching { AppContainer.api.upgradeCreate(acc.creds, settings.baseUrl, settings.timeoutSeconds, id) }
                        .onSuccess { onToast(it.message ?: t(Str.UpgradeSubmitted)); tick++ }
                        .onFailure { onToast(apiErrorMessage(lang, it)) }
                }
            },
        )
    }

    if (showAssist) {
        InputDialog(
            title = t(Str.UpgradeAssistTitle),
            label = t(Str.UpgradeAssistTitle),
            hint = t(Str.UpgradeAssistHint),
            initial = "",
            onDismiss = { showAssist = false },
            onConfirm = { v ->
                val acc = account ?: return@InputDialog
                scope.launch {
                    runCatching { AppContainer.api.upgradeAssist(acc.creds, settings.baseUrl, settings.timeoutSeconds, v.trim()) }
                        .onSuccess { onToast(it.message ?: t(Str.UpgradeAssistDone)); tick++ }
                        .onFailure { onToast(apiErrorMessage(lang, it)) }
                }
            },
        )
    }

    cancelTarget?.let { rid ->
        ConfirmDialog(
            title = t(Str.UpgradeCancel),
            text = t(Str.UpgradeCancelConfirm, rid),
            destructive = true,
            onConfirm = {
                val acc = account ?: return@ConfirmDialog
                scope.launch {
                    runCatching { AppContainer.api.upgradeCancel(acc.creds, settings.baseUrl, settings.timeoutSeconds, rid) }
                        .onSuccess { onToast(it.message ?: t(Str.UpgradeCancelled)); tick++ }
                        .onFailure { onToast(apiErrorMessage(lang, it)) }
                }
            },
            onDismiss = { cancelTarget = null },
        )
    }
}

@Composable
private fun JsonSection(
    title: String,
    items: List<JsonElement>,
    onCancel: ((Long) -> Unit)? = null,
) {
    SectionTitle(title)
    if (items.isEmpty()) {
        AppCard {
            Text(
                t(Str.UpgradeEmpty),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
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
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (status != null) StatusPill(status)
                }
                Spacer(Modifier.height(6.dp))
                if (el is JsonObject) {
                    el.forEach { (k, v) ->
                        if (k in setOf("request_id", "id", "subdomain_id", "status", "state")) return@forEach
                        val text = when (v) {
                            is JsonPrimitive -> v.content
                            is JsonArray -> v.joinToString(", ") { it.jsonPrimitive.content }
                            else -> v.pretty()
                        }
                        KeyValueRow(k, text, mono = text.length < 64)
                    }
                }
                if (onCancel != null && idText != null) {
                    val rid = idText.toLongOrNull()
                    if (rid != null && status?.lowercase() in setOf("pending", "waiting", "processing", "queued", "reviewing")) {
                        TextButton(
                                   text = t(Str.UpgradeCancel), colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColors(color = MiuixTheme.colorScheme.error),
                                   onClick = { onCancel(rid) },
                        )
                    }
                }
            }
        }
    }
}
