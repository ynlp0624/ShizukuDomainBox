package io.nekoshizuku.dnshe.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.nekoshizuku.dnshe.core.Account
import io.nekoshizuku.dnshe.core.AppCore
import io.nekoshizuku.dnshe.core.AppSettings
import io.nekoshizuku.dnshe.core.DnsRecord
import io.nekoshizuku.dnshe.core.Quota
import io.nekoshizuku.dnshe.core.RootDomains
import io.nekoshizuku.dnshe.core.Subdomain
import io.nekoshizuku.dnshe.core.apiErrorMessage
import io.nekoshizuku.dnshe.desktop.Sys
import io.nekoshizuku.dnshe.desktop.i18n.LocalLang
import io.nekoshizuku.dnshe.desktop.i18n.Str
import io.nekoshizuku.dnshe.desktop.i18n.t
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ============================ 页面外壳 ============================== */

@Composable
fun PageHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shadowElevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = t(Str.Back))
                }
                Spacer(Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

/* ============================ 域名列表 ============================== */

@Composable
fun DomainsView(
    account: Account?,
    settings: AppSettings,
    refreshTick: Int,
    onOpenRegister: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    onGoAccounts: () -> Unit,
    onToast: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val lang = LocalLang.current

    var items by remember { mutableStateOf<List<Subdomain>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableStateOf(1) }
    var hasMore by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var quota by remember { mutableStateOf<Quota?>(null) }

    suspend fun loadQuota() {
        val acc = account ?: return
        runCatching { AppCore.api.quota(acc.creds, settings.baseUrl, settings.timeoutSeconds) }
            .onSuccess { quota = it.quota }
    }

    suspend fun load(reset: Boolean) {
        val acc = account ?: return
        if (acc.apiKey.isBlank()) return
        if (reset) loading = items.isEmpty() else loadingMore = true
        error = null
        runCatching {
            AppCore.api.listSubdomains(
                creds = acc.creds,
                baseUrl = settings.baseUrl,
                timeoutSeconds = settings.timeoutSeconds,
                page = if (reset) 1 else page + 1,
                perPage = settings.perPage,
                search = search.ifBlank { null },
                sortBy = "id",
                sortDir = "desc",
            )
        }.onSuccess { resp ->
            items = if (reset) resp.subdomains else items + resp.subdomains
            hasMore = resp.pagination?.hasMore ?: (resp.subdomains.size >= settings.perPage)
            if (!reset) page += 1 else page = 1
            loading = false
            loadingMore = false
        }.onFailure { e ->
            error = apiErrorMessage(lang, e)
            loading = false
            loadingMore = false
        }
    }

    LaunchedEffect(account?.id, search, refreshTick) {
        if (search.isNotEmpty()) delay(220)
        load(reset = true)
        if (search.isEmpty()) loadQuota()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(
            title = t(Str.DomainsTitle),
            subtitle = account?.let { t(Str.CurrentAccount) + ": " + it.name.ifBlank { it.maskedKey } },
        ) {
            ToolbarButton(t(Str.Refresh)) { scope.launch { load(true); loadQuota(); onToast(t(Str.Refreshed)) } }
            Button(onClick = onOpenRegister, enabled = account != null) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(t(Str.RegisterDomain))
            }
        }

        if (account == null) {
            EmptyState(
                text = t(Str.DomainsEmptyNoAccount),
                actionLabel = t(Str.GoAddAccount),
                onAction = onGoAccounts,
            )
            return@Column
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatTile(t(Str.CurrentAccount), account.name.ifBlank { account.maskedKey }, Modifier.width(240.dp))
            StatTile(
                t(Str.QuotaAvailable),
                quota?.let { "${it.available} / ${it.total}" } ?: "—",
                Modifier.width(180.dp),
            )
            Spacer(Modifier.weight(1f))
            Box(modifier = Modifier.width(300.dp), contentAlignment = Alignment.Center) {
                AppTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = t(Str.SearchDomainHint),
                    singleLine = true,
                )
            }
        }

        when {
            loading -> LoadingBox()
            error != null -> ErrorBox(error!!, onRetry = { scope.launch { load(true) } })
            items.isEmpty() -> EmptyState(
                text = if (search.isBlank()) t(Str.DomainsEmpty) else t(Str.Empty),
                actionLabel = if (search.isBlank()) t(Str.RegisterDomain) else null,
                onAction = if (search.isBlank()) onOpenRegister else null,
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        t(Str.StatTotal, items.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(items, key = { it.id }) { d ->
                    DomainRow(domain = d, onClick = { onOpenDetail(d.id) })
                }
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        if (hasMore) {
                            OutlinedButton(onClick = { scope.launch { load(false) } }, enabled = !loadingMore) {
                                Text(if (loadingMore) t(Str.Loading) else t(Str.LoadMore))
                            }
                        } else {
                            Text(
                                t(Str.NoMoreData),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, accent: Color? = null) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = accent ?: MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DomainRow(domain: Subdomain, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = domain.displayDomain,
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (RootDomains.isExternal(domain.rootdomain)) {
                        Spacer(Modifier.width(8.dp))
                        ExternalMiniTag()
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    Text(
                        "${t(Str.ExpiresAt)}: " + if (domain.neverExpiresBool) t(Str.NeverExpires) else (domain.expiresAt ?: "-"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${t(Str.CreatedAt)}: ${domain.createdAt ?: "-"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            StatusPill(domain.status)
        }
    }
}

/* ============================ 注册子域名 ============================ */

@Composable
fun RegisterView(
    account: Account?,
    settings: AppSettings,
    onBack: () -> Unit,
    onRegistered: (String) -> Unit,
    onToast: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val host = LocalOverlayHost.current
    val lang = LocalLang.current

    var sub by remember { mutableStateOf("") }
    var root by remember { mutableStateOf(account?.defaultRootDomain?.takeIf { it.isNotBlank() } ?: settings.defaultRootDomain) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(title = t(Str.RegisterDomainTitle), onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppTextField(
                value = sub,
                onValueChange = { sub = it.trim() },
                label = t(Str.SubdomainPrefix),
                placeholder = t(Str.SubdomainPrefixHint),
                hint = t(Str.Required),
                modifier = Modifier.width(520.dp),
            )

            AppCard(modifier = Modifier.width(520.dp), onClick = {
                host.show {
                    RootDomainPickerDialog(
                        current = root,
                        onSelect = {
                            root = it
                            host.dismiss()
                            if (RootDomains.isExternal(it)) onToast(t(Str.ExternalWarnShort))
                        },
                        onDismiss = { host.dismiss() },
                    )
                }
            }) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            t(Str.RootDomain),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        RootDomainBadge(root)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = root.ifBlank { t(Str.ChooseRootDomain) },
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                        color = when {
                            root.isBlank() -> MaterialTheme.colorScheme.onSurfaceVariant
                            RootDomains.isExternal(root) -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }

            Box(modifier = Modifier.width(520.dp)) { RootDomainNotice(root) }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    host.show {
                        ManualRootDialog(
                            initial = root,
                            onConfirm = { root = it.lowercase(); host.dismiss() },
                            onDismiss = { host.dismiss() },
                        )
                    }
                }) { Text(t(Str.RootDomainManualInput)) }
                if (account?.defaultRootDomain?.isNotBlank() == true) {
                    OutlinedButton(onClick = { root = account.defaultRootDomain }) {
                        Text(t(Str.UseDefaultRoot, account.defaultRootDomain))
                    }
                }
            }

            if (sub.isNotBlank() && root.isNotBlank()) {
                AppCard(modifier = Modifier.width(520.dp)) {
                    Column {
                        Text(
                            t(Str.FullDomainPreview),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "$sub.$root",
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Box(modifier = Modifier.width(520.dp)) { InlineMessage(message, messageIsError) }

            Button(
                enabled = !busy,
                modifier = Modifier.width(520.dp),
                onClick = {
                    if (sub.isBlank()) { onToast(t(Str.SubdomainRequired)); return@Button }
                    if (root.isBlank()) { onToast(t(Str.RootDomainRequired)); return@Button }
                    val acc = account ?: return@Button
                    busy = true
                    message = null
                    scope.launch {
                        runCatching {
                            AppCore.api.registerSubdomain(acc.creds, settings.baseUrl, settings.timeoutSeconds, sub, root)
                        }.onSuccess { r ->
                            busy = false
                            val full = r.fullDomain ?: "$sub.$root"
                            message = t(Str.RegisterSuccess, full)
                            messageIsError = false
                            onToast(t(Str.RegisterSuccess, full))
                            onRegistered(full)
                        }.onFailure { e ->
                            busy = false
                            val msg = apiErrorMessage(lang, e)
                            message = msg
                            messageIsError = true
                            onToast(msg)
                        }
                    }
                },
            ) { Text(if (busy) t(Str.Loading) else t(Str.RegisterDomain)) }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ManualRootDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(initial) }
    OverlayDialog(title = t(Str.RootDomainManualInput), onDismiss = onDismiss, width = 460.dp) {
        AppTextField(
            value = value,
            onValueChange = { value = it },
            label = t(Str.RootDomain),
            hint = t(Str.RootDomainManualHint),
        )
        DialogButtons(
            onCancel = onDismiss,
            onConfirm = { onConfirm(value.trim()) },
            confirmEnabled = value.isNotBlank(),
        )
    }
}

/* ============================ 域名详情 ============================== */

@Composable
fun DomainDetailView(
    subdomainId: Long,
    account: Account?,
    settings: AppSettings,
    onBack: () -> Unit,
    onToast: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val host = LocalOverlayHost.current
    val lang = LocalLang.current

    var detail by remember { mutableStateOf<Subdomain?>(null) }
    var records by remember { mutableStateOf<List<DnsRecord>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }

    suspend fun load() {
        val acc = account ?: return
        loading = detail == null
        error = null
        runCatching {
            AppCore.api.getSubdomain(acc.creds, settings.baseUrl, settings.timeoutSeconds, subdomainId)
        }.onSuccess {
            detail = it.subdomain
            records = it.dnsRecords
            loading = false
        }.onFailure {
            error = apiErrorMessage(lang, it)
            loading = false
        }
    }

    LaunchedEffect(subdomainId, tick) { load() }

    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(
            title = detail?.displayDomain ?: t(Str.DomainDetailTitle),
            onBack = onBack,
        ) {
            ToolbarButton(t(Str.Refresh)) { scope.launch { tick++ } }
            ToolbarButton(t(Str.AddRecord)) {
                host.show {
                    RecordEditorDialog(
                        initial = null,
                        subdomainId = subdomainId,
                        account = account,
                        settings = settings,
                        defaultTtl = settings.defaultTtl,
                        onSaved = { msg ->
                            onToast(msg)
                            host.dismiss()
                            tick++
                        },
                        onDismiss = { host.dismiss() },
                        onToast = onToast,
                    )
                }
            }
            Box {
                OutlinedButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = t(Str.More), modifier = Modifier.size(18.dp)) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(t(Str.Renew)) },
                        onClick = {
                            menuOpen = false
                            host.show {
                                ConfirmOverlay(
                                    title = t(Str.Renew),
                                    text = t(Str.RenewConfirm, detail?.displayDomain ?: ""),
                                    onConfirm = {
                                        host.dismiss()
                                        val acc = account ?: return@ConfirmOverlay
                                        scope.launch {
                                            runCatching {
                                                AppCore.api.renewSubdomain(acc.creds, settings.baseUrl, settings.timeoutSeconds, subdomainId)
                                            }.onSuccess { r ->
                                                onToast(t(Str.RenewSuccess, r.newExpiresAt ?: "-"))
                                                tick++
                                            }.onFailure { onToast(apiErrorMessage(lang, it)) }
                                        }
                                    },
                                    onDismiss = { host.dismiss() },
                                )
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(t(Str.DeleteDomain), color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuOpen = false
                            host.show {
                                ConfirmOverlay(
                                    title = t(Str.DeleteDomain),
                                    text = t(Str.DeleteDomainConfirm, detail?.displayDomain ?: ""),
                                    destructive = true,
                                    onConfirm = {
                                        host.dismiss()
                                        val acc = account ?: return@ConfirmOverlay
                                        scope.launch {
                                            runCatching {
                                                AppCore.api.deleteSubdomain(acc.creds, settings.baseUrl, settings.timeoutSeconds, subdomainId)
                                            }.onSuccess {
                                                onToast(t(Str.DomainDeleted, detail?.displayDomain ?: ""))
                                                onBack()
                                            }.onFailure { onToast(apiErrorMessage(lang, it)) }
                                        }
                                    },
                                    onDismiss = { host.dismiss() },
                                )
                            }
                        },
                    )
                }
            }
        }

        when {
            loading -> LoadingBox()
            error != null -> ErrorBox(error!!, onRetry = { scope.launch { tick++ } })
            else -> {
                val d = detail ?: return@Column
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
                ) {
                    Spacer(Modifier.height(10.dp))
                    AppCard {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    d.displayDomain,
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                                    modifier = Modifier.weight(1f),
                                )
                                if (RootDomains.isExternal(d.rootdomain)) {
                                    ExternalMiniTag()
                                    Spacer(Modifier.width(8.dp))
                                }
                                StatusPill(d.status)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row {
                                Column(modifier = Modifier.weight(1f)) {
                                    InfoRow(t(Str.ExpiresAt), if (d.neverExpiresBool) t(Str.NeverExpires) else d.expiresAt)
                                    InfoRow(t(Str.CreatedAt), d.createdAt)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    InfoRow(t(Str.UpdatedAt), d.updatedAt)
                                    InfoRow("ID", d.id.toString(), mono = true)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    RootDomainNotice(d.rootdomain)

                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            t(Str.DnsRecords),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            t(Str.DnsRecordCount, records.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(10.dp))
                        Button(onClick = {
                            host.show {
                                RecordEditorDialog(
                                    initial = null,
                                    subdomainId = subdomainId,
                                    account = account,
                                    settings = settings,
                                    defaultTtl = settings.defaultTtl,
                                    onSaved = { msg -> onToast(msg); host.dismiss(); tick++ },
                                    onDismiss = { host.dismiss() },
                                    onToast = onToast,
                                )
                            }
                        }) { Text(t(Str.AddRecord)) }
                    }
                    Spacer(Modifier.height(10.dp))

                    if (records.isEmpty()) {
                        EmptyState( text = t(Str.RecordsEmpty))
                    } else {
                        records.forEach { rec ->
                            RecordRow(
                                record = rec,
                                onEdit = {
                                    host.show {
                                        RecordEditorDialog(
                                            initial = rec,
                                            subdomainId = subdomainId,
                                            account = account,
                                            settings = settings,
                                            defaultTtl = settings.defaultTtl,
                                            onSaved = { msg -> onToast(msg); host.dismiss(); tick++ },
                                            onDismiss = { host.dismiss() },
                                            onToast = onToast,
                                        )
                                    }
                                },
                                onDelete = {
                                    host.show {
                                        ConfirmOverlay(
                                            title = t(Str.ConfirmDeleteTitle),
                                            text = t(Str.DeleteRecordConfirm, rec.type),
                                            destructive = true,
                                            onConfirm = {
                                                host.dismiss()
                                                val acc = account ?: return@ConfirmOverlay
                                                scope.launch {
                                                    runCatching {
                                                        AppCore.api.deleteDnsRecord(acc.creds, settings.baseUrl, settings.timeoutSeconds, rec.id, rec.recordId)
                                                    }.onSuccess { onToast(t(Str.RecordDeleted)); tick++ }
                                                        .onFailure { onToast(apiErrorMessage(lang, it)) }
                                                }
                                            },
                                            onDismiss = { host.dismiss() },
                                        )
                                    }
                                },
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun ConfirmOverlay(
    title: String,
    text: String,
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayDialog(title = title, onDismiss = onDismiss, width = 460.dp) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
        DialogButtons(
            onCancel = onDismiss,
            onConfirm = onConfirm,
            destructive = destructive,
            confirmLabel = if (destructive) t(Str.Delete) else t(Str.Confirm),
        )
    }
}

@Composable
private fun RecordRow(record: DnsRecord, onEdit: () -> Unit, onDelete: () -> Unit) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(58.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(vertical = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    record.type,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    record.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    record.content,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    record.ttl?.let {
                        Text("TTL $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    record.priority?.let {
                        Text("PRI $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!record.line.isNullOrBlank()) {
                        Text("LINE ${record.line}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    record.status?.let { StatusPill(it) }
                }
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

/* ========================= DNS 记录编辑框 =========================== */

private val RECORD_TYPES = listOf("A", "AAAA", "CNAME", "MX", "TXT", "NS", "SRV", "CAA")

@Composable
fun RecordEditorDialog(
    initial: DnsRecord?,
    subdomainId: Long,
    account: Account?,
    settings: AppSettings,
    defaultTtl: Int,
    onSaved: (String) -> Unit,
    onDismiss: () -> Unit,
    onToast: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val lang = LocalLang.current
    val isNew = initial == null

    var type by remember { mutableStateOf(initial?.type ?: "A") }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var content by remember { mutableStateOf(initial?.content ?: "") }
    var ttl by remember { mutableStateOf((initial?.ttl ?: defaultTtl).toString()) }
    var priority by remember { mutableStateOf(initial?.priority?.toString() ?: "") }
    var line by remember { mutableStateOf(initial?.line ?: "") }
    var weight by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var caaFlag by remember { mutableStateOf("0") }
    var caaTag by remember { mutableStateOf("issue") }
    var caaValue by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    OverlayDialog(
        title = if (isNew) t(Str.AddRecord) else t(Str.EditRecord),
        onDismiss = onDismiss,
        width = 620.dp,
        maxHeight = 660.dp,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TypeDropdown(
                label = t(Str.RecordType),
                options = RECORD_TYPES,
                selected = type,
                onSelect = { type = it },
                modifier = Modifier.width(160.dp),
            )
            AppTextField(
                value = ttl,
                onValueChange = { ttl = it.filter { c -> c.isDigit() } },
                label = t(Str.RecordTtl),
                hint = "600",
                modifier = Modifier.width(140.dp),
            )
        }

        AppTextField(
            value = name,
            onValueChange = { name = it },
            label = t(Str.RecordName),
            placeholder = t(Str.RecordNameHint),
            hint = t(Str.NameHintAdvanced),
        )

        when (type) {
            "SRV" -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppTextField(priority, { priority = it.filter { c -> c.isDigit() } }, t(Str.RecordPriority), modifier = Modifier.width(120.dp))
                    AppTextField(weight, { weight = it.filter { c -> c.isDigit() } }, t(Str.RecordWeight), modifier = Modifier.width(120.dp))
                    AppTextField(port, { port = it.filter { c -> c.isDigit() } }, t(Str.RecordPort), modifier = Modifier.width(120.dp))
                }
                AppTextField(target, { target = it }, t(Str.RecordTarget), placeholder = "example.com")
            }
            "CAA" -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppTextField(caaFlag, { caaFlag = it.filter { c -> c.isDigit() } }, t(Str.CaaFlag), modifier = Modifier.width(120.dp))
                    AppTextField(caaTag, { caaTag = it }, t(Str.CaaTag), modifier = Modifier.width(160.dp))
                }
                AppTextField(caaValue, { caaValue = it }, t(Str.CaaValue))
            }
            else -> {
                AppTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = t(Str.RecordContent),
                    placeholder = t(Str.RecordContentHint),
                )
                if (type == "MX") {
                    AppTextField(
                        priority,
                        { priority = it.filter { c -> c.isDigit() } },
                        t(Str.RecordPriority),
                        hint = "10",
                        modifier = Modifier.width(160.dp),
                    )
                }
            }
        }

        AppTextField(line, { line = it }, t(Str.RecordLine), hint = t(Str.Optional))

        InlineMessage(message)

        DialogButtons(
            onCancel = onDismiss,
            confirmEnabled = !busy,
            confirmLabel = if (busy) t(Str.Loading) else t(Str.Save),
            onConfirm = {
                val acc = account ?: return@DialogButtons
                if (type != "SRV" && type != "CAA" && content.isBlank()) {
                    message = t(Str.ContentRequired)
                    return@DialogButtons
                }
                if (type == "SRV" && target.isBlank()) {
                    message = t(Str.ContentRequired)
                    return@DialogButtons
                }
                if (type == "CAA" && caaValue.isBlank()) {
                    message = t(Str.ContentRequired)
                    return@DialogButtons
                }
                busy = true
                message = null
                scope.launch {
                    runCatching {
                        if (isNew) {
                            AppCore.api.createDnsRecord(
                                creds = acc.creds, baseUrl = settings.baseUrl, timeoutSeconds = settings.timeoutSeconds,
                                subdomainId = subdomainId, type = type, name = name.ifBlank { null },
                                content = content.ifBlank { null }, ttl = ttl.toIntOrNull(),
                                priority = priority.toIntOrNull(), line = line.ifBlank { null },
                                weight = weight.toIntOrNull(), port = port.toIntOrNull(), target = target.ifBlank { null },
                                caaFlag = if (type == "CAA") caaFlag.toIntOrNull() else null,
                                caaTag = if (type == "CAA") caaTag.ifBlank { null } else null,
                                caaValue = if (type == "CAA") caaValue.ifBlank { null } else null,
                            )
                        } else {
                            AppCore.api.updateDnsRecord(
                                creds = acc.creds, baseUrl = settings.baseUrl, timeoutSeconds = settings.timeoutSeconds,
                                id = initial?.id, recordId = initial?.recordId,
                                type = type, name = name.ifBlank { null },
                                content = content.ifBlank { null }, ttl = ttl.toIntOrNull(),
                                priority = priority.toIntOrNull(), line = line.ifBlank { null },
                                weight = weight.toIntOrNull(), port = port.toIntOrNull(), target = target.ifBlank { null },
                                caaFlag = if (type == "CAA") caaFlag.toIntOrNull() else null,
                                caaTag = if (type == "CAA") caaTag.ifBlank { null } else null,
                                caaValue = if (type == "CAA") caaValue.ifBlank { null } else null,
                            )
                        }
                    }.onSuccess {
                        busy = false
                        onSaved(t(Str.RecordSaved))
                    }.onFailure { e ->
                        busy = false
                        message = apiErrorMessage(lang, e)
                    }
                }
            },
        )
    }
}
