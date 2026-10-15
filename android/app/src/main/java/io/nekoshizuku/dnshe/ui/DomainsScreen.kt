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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.nekoshizuku.dnshe.R
import io.nekoshizuku.dnshe.data.Account
import io.nekoshizuku.dnshe.data.ApiException
import io.nekoshizuku.dnshe.data.AppContainer
import io.nekoshizuku.dnshe.data.AppSettings
import io.nekoshizuku.dnshe.data.DnsRecord
import io.nekoshizuku.dnshe.data.Quota
import io.nekoshizuku.dnshe.data.RootDomains
import io.nekoshizuku.dnshe.data.Subdomain
import io.nekoshizuku.dnshe.data.apiErrorMessage
import io.nekoshizuku.dnshe.i18n.LocalLang
import top.yukonga.miuix.kmp.theme.MiuixTheme
import io.nekoshizuku.dnshe.i18n.Str
import io.nekoshizuku.dnshe.i18n.t
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ============================ 域名列表 ============================= */

@Composable
fun DomainsScreen(
    account: Account?,
    settings: AppSettings,
    refreshTick: Int,
    onOpenRegister: () -> Unit,
    onOpenDetail: (Subdomain) -> Unit,
    onGoAccounts: () -> Unit,
    onToast: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val lang = LocalLang.current

    var items by remember { mutableStateOf<List<Subdomain>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableStateOf(1) }
    var hasMore by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var quota by remember { mutableStateOf<Quota?>(null) }
    var loadingMore by remember { mutableStateOf(false) }

    suspend fun loadQuota() {
        val acc = account ?: return
        runCatching {
            AppContainer.api.quota(acc.creds, settings.baseUrl, settings.timeoutSeconds)
        }.onSuccess { quota = it.quota }
    }

    suspend fun load(reset: Boolean) {
        val acc = account ?: return
        if (acc.apiKey.isBlank()) return
        if (reset) {
            loading = items.isEmpty()
            page = 1
        } else {
            loadingMore = true
        }
        error = null
        runCatching {
            AppContainer.api.listSubdomains(
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
            if (!reset) page += 1
            if (reset) page = 1
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

    top.yukonga.miuix.kmp.basic.Scaffold(
        containerColor = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.background,
        topBar = {
            top.yukonga.miuix.kmp.basic.TopAppBar(
                title = t(Str.DomainsTitle),
                color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.background,
                actions = {
                    top.yukonga.miuix.kmp.basic.IconButton(
                        onClick = { scope.launch { load(true); loadQuota(); onToast(t(Str.Refreshed)) } },
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = t(Str.Refresh),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (account != null) {
                top.yukonga.miuix.kmp.basic.FloatingActionButton(onClick = onOpenRegister) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = t(Str.RegisterDomain),
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {

            if (account == null) {
                EmptyState(
                    text = t(Str.DomainsEmptyNoAccount),
                    actionLabel = t(Str.GoAddAccount),
                    onAction = onGoAccounts,
                )
                return@Column
            }

            // 当前账号 + 配额
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InfoTile(label = t(Str.CurrentAccount), value = account.name.ifBlank { account.maskedKey }, modifier = Modifier.weight(1f))
                val q = quota
                InfoTile(
                    label = t(Str.QuotaAvailable),
                    value = if (q != null) "${q.available} / ${q.total}" else "—",
                    modifier = Modifier.weight(1f),
                )
            }

            Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                ClearableField(
                    value = search,
                    onValueChange = { search = it },
                    label = t(Str.SearchDomainHint),
                )
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
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text(
                            t(Str.StatTotal, items.size),
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    items(items, key = { it.id }) { d ->
                        DomainCard(domain = d, onClick = { onOpenDetail(d) })
                    }
                    if (hasMore) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                if (loadingMore) {
                                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                                } else {
                                    TextButton(text = t(Str.LoadMore), onClick = { scope.launch { load(false) } })
                                }
                            }
                        }
                    } else {
                        item {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(
                                    t(Str.NoMoreData),
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
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
private fun DomainCard(domain: Subdomain, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_dns),
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = domain.displayDomain,
                    fontSize = 16.sp, fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (RootDomains.isExternal(domain.rootdomain)) {
                    ExternalMiniTag()
                    Spacer(Modifier.width(6.dp))
                }
                StatusPill(domain.status)
            }
            Spacer(Modifier.height(8.dp))
            Row {
                Column(modifier = Modifier.weight(1f)) {
                    KeyValueRow(t(Str.ExpiresAt), if (domain.neverExpiresBool) t(Str.NeverExpires) else domain.expiresAt)
                    KeyValueRow(t(Str.CreatedAt), domain.createdAt)
                }
            }
        }
    }
}

/* ============================ 注册子域名 ============================ */

@Composable
fun RegisterDomainScreen(
    account: Account?,
    settings: AppSettings,
    onBack: () -> Unit,
    onRegistered: (String) -> Unit,
    onToast: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val lang = LocalLang.current
    var sub by remember { mutableStateOf("") }
    var root by remember { mutableStateOf(
        account?.defaultRootDomain?.takeIf { it.isNotBlank() } ?: settings.defaultRootDomain
    ) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    var showRootPicker by remember { mutableStateOf(false) }
    var manualRoot by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = t(Str.RegisterDomainTitle),
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
                value = sub,
                onValueChange = { sub = it.trim() },
                label = t(Str.SubdomainPrefix),
                placeholder = t(Str.SubdomainPrefixHint),
                hint = t(Str.Required),
            )

            // 根域名：点开选择器，官方/外部分组显示
            AppCard(onClick = { showRootPicker = true }) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            t(Str.RootDomain),
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.weight(1f),
                        )
                        RootDomainBadge(root)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = root.ifBlank { t(Str.ChooseRootDomain) },
                        fontSize = 16.sp, fontFamily = FontFamily.Monospace,
                        color = if (root.isBlank()) {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                        } else if (RootDomains.isExternal(root)) {
                            MiuixTheme.colorScheme.error
                        } else {
                            MiuixTheme.colorScheme.onSurface
                        },
                    )
                }
            }

            // 外部域名 / 未知根域名警告
            RootDomainNotice(root)

            // 手动输入（官网新上线的根域名用）
            if (manualRoot) {
                AppTextField(
                    value = root,
                    onValueChange = { root = it.trim().lowercase() },
                    label = t(Str.RootDomainManualInput),
                    placeholder = t(Str.RootDomainHint),
                    hint = t(Str.RootDomainManualHint),
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(text = t(Str.RootDomainManualInput), onClick = { manualRoot = true })
                    if (account?.defaultRootDomain?.isNotBlank() == true) {
                        TextButton(
                                   text = t(Str.UseDefaultRoot, account.defaultRootDomain),
                                   onClick = { root = account.defaultRootDomain },
                        )
                    }
                }
            }

            // 完整域名预览
            if (sub.isNotBlank() && root.isNotBlank()) {
                AppCard {
                    Column {
                        Text(
                            t(Str.FullDomainPreview),
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "$sub.$root",
                            fontSize = 16.sp, fontFamily = FontFamily.Monospace,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    }
                }
            }

            InlineMessage(message, messageIsError)

            Button(
                enabled = !busy,
                onClick = {
                    if (sub.isBlank()) { onToast(t(Str.SubdomainRequired)); return@Button }
                    if (root.isBlank()) { onToast(t(Str.RootDomainRequired)); return@Button }
                    val acc = account ?: return@Button
                    busy = true
                    message = null
                    scope.launch {
                        runCatching {
                            AppContainer.api.registerSubdomain(
                                acc.creds, settings.baseUrl, settings.timeoutSeconds, sub, root,
                            )
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
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (busy) t(Str.Loading) else t(Str.RegisterDomain))
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showRootPicker) {
        RootDomainPickerDialog(
            current = root,
            onSelect = {
                root = it
                showRootPicker = false
                if (RootDomains.isExternal(it)) onToast(t(Str.ExternalWarnShort))
            },
            onDismiss = { showRootPicker = false },
        )
    }
}

/* ============================ 域名详情 ============================= */

@Composable
fun DomainDetailScreen(
    subdomainId: Long,
    account: Account?,
    settings: AppSettings,
    onBack: () -> Unit,
    onToast: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val lang = LocalLang.current

    var detail by remember { mutableStateOf<Subdomain?>(null) }
    var records by remember { mutableStateOf<List<DnsRecord>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var editingRecord by remember { mutableStateOf<DnsRecord?>(null) }
    var creatingRecord by remember { mutableStateOf(false) }
    var confirmRenew by remember { mutableStateOf(false) }
    var confirmDeleteDomain by remember { mutableStateOf(false) }
    var deletingRecord by remember { mutableStateOf<DnsRecord?>(null) }
    var tick by remember { mutableStateOf(0) }

    suspend fun load() {
        val acc = account ?: return
        loading = detail == null
        error = null
        runCatching {
            AppContainer.api.getSubdomain(acc.creds, settings.baseUrl, settings.timeoutSeconds, subdomainId)
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

    Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = detail?.displayDomain ?: t(Str.DomainDetailTitle),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = t(Str.Back), modifier = Modifier.size(24.dp)) }
                },
                actions = {
                    IconButton(onClick = { scope.launch { tick++; } }) {
                        Icon(Icons.Filled.Refresh, contentDescription = t(Str.Refresh), modifier = Modifier.size(24.dp))
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = t(Str.More), modifier = Modifier.size(24.dp))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(t(Str.Renew)) },
                                onClick = { menuOpen = false; confirmRenew = true },
                            )
                            DropdownMenuItem(
                                text = { Text(t(Str.DeleteDomain), color = MiuixTheme.colorScheme.error) },
                                onClick = { menuOpen = false; confirmDeleteDomain = true },
                            )
                        }
                    }
                },
                color = MiuixTheme.colorScheme.background,
            )
        },
        floatingActionButton = {
            if (detail != null) {
                FloatingActionButton(onClick = { creatingRecord = true }) {
                    Icon(Icons.Filled.Add, contentDescription = t(Str.AddRecord), modifier = Modifier.size(24.dp))
                }
            }
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            when {
                loading -> LoadingBox()
                error != null -> ErrorBox(error!!, onRetry = { scope.launch { tick++ } })
                else -> {
                    val d = detail ?: return@Column
                    AppCard(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    d.displayDomain,
                                    fontSize = 16.sp, fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f),
                                )
                                if (RootDomains.isExternal(d.rootdomain)) {
                                    ExternalMiniTag()
                                    Spacer(Modifier.width(6.dp))
                                }
                                StatusPill(d.status)
                            }
                            Spacer(Modifier.height(6.dp))
                            KeyValueRow(t(Str.ExpiresAt), if (d.neverExpiresBool) t(Str.NeverExpires) else d.expiresAt)
                            KeyValueRow(t(Str.CreatedAt), d.createdAt)
                            KeyValueRow(t(Str.UpdatedAt), d.updatedAt)
                            KeyValueRow("ID", d.id.toString(), mono = true)
                        }
                    }

                    RootDomainNotice(
                        d.rootdomain,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            t(Str.DnsRecords),
                            fontSize = 16.sp,
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            t(Str.DnsRecordCount, records.size),
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }

                    if (records.isEmpty()) {
                        EmptyState(
                            text = t(Str.RecordsEmpty),
                            actionLabel = t(Str.AddRecord),
                            onAction = { creatingRecord = true },
                            icon = { StateIcon(painterResource(R.drawable.ic_dns)) },
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(records, key = { it.id ?: it.recordId.hashCode() }) { rec ->
                                RecordCard(
                                    record = rec,
                                    onEdit = { editingRecord = rec },
                                    onDelete = { deletingRecord = rec },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (creatingRecord || editingRecord != null) {
        RecordEditorDialog(
            initial = editingRecord,
            isNew = creatingRecord,
            subdomainId = subdomainId,
            account = account,
            settings = settings,
            defaultTtl = settings.defaultTtl,
            onDismiss = { creatingRecord = false; editingRecord = null },
            onSaved = { msg ->
                onToast(msg)
                creatingRecord = false
                editingRecord = null
                tick++
            },
            onToast = onToast,
        )
    }

    deletingRecord?.let { rec ->
        ConfirmDialog(
            title = t(Str.ConfirmDeleteTitle),
            text = t(Str.DeleteRecordConfirm, rec.type),
            destructive = true,
            onConfirm = {
                val acc = account ?: return@ConfirmDialog
                scope.launch {
                    runCatching {
                        AppContainer.api.deleteDnsRecord(
                            acc.creds, settings.baseUrl, settings.timeoutSeconds, rec.id, rec.recordId,
                        )
                    }.onSuccess { onToast(t(Str.RecordDeleted)); tick++ }
                        .onFailure { onToast(apiErrorMessage(lang, it)) }
                }
            },
            onDismiss = { deletingRecord = null },
        )
    }

    if (confirmRenew) {
        ConfirmDialog(
            title = t(Str.Renew),
            text = t(Str.RenewConfirm, detail?.displayDomain ?: ""),
            onConfirm = {
                val acc = account ?: return@ConfirmDialog
                scope.launch {
                    runCatching {
                        AppContainer.api.renewSubdomain(acc.creds, settings.baseUrl, settings.timeoutSeconds, subdomainId)
                    }.onSuccess { r ->
                        onToast(t(Str.RenewSuccess, r.newExpiresAt ?: "-"))
                        tick++
                    }.onFailure { onToast(apiErrorMessage(lang, it)) }
                }
            },
            onDismiss = { confirmRenew = false },
        )
    }

    if (confirmDeleteDomain) {
        ConfirmDialog(
            title = t(Str.DeleteDomain),
            text = t(Str.DeleteDomainConfirm, detail?.displayDomain ?: ""),
            destructive = true,
            onConfirm = {
                val acc = account ?: return@ConfirmDialog
                scope.launch {
                    runCatching {
                        AppContainer.api.deleteSubdomain(acc.creds, settings.baseUrl, settings.timeoutSeconds, subdomainId)
                    }.onSuccess {
                        onToast(t(Str.DomainDeleted, detail?.displayDomain ?: ""))
                        onBack()
                    }.onFailure { onToast(apiErrorMessage(lang, it)) }
                }
            },
            onDismiss = { confirmDeleteDomain = false },
        )
    }
}

@Composable
private fun RecordCard(record: DnsRecord, onEdit: () -> Unit, onDelete: () -> Unit) {
    AppCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp),
                ) {
                    Text(
                        text = record.type,
                        fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = record.name,
                    fontSize = 14.sp, fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
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
            Text(
                text = record.content,
                fontSize = 14.sp, fontFamily = FontFamily.Monospace,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Row {
                if (record.ttl != null) {
                    Text(
                        "TTL ${record.ttl}",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                if (record.priority != null) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "PRI ${record.priority}",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                if (!record.line.isNullOrBlank()) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "LINE ${record.line}",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                record.status?.let {
                    Spacer(Modifier.width(10.dp))
                    StatusPill(it)
                }
            }
        }
    }
}

/* ========================= DNS 记录编辑框 =========================== */

private val RECORD_TYPES = listOf("A", "AAAA", "CNAME", "MX", "TXT", "NS", "SRV", "CAA")

@Composable
private fun RecordEditorDialog(
    initial: DnsRecord?,
    isNew: Boolean,
    subdomainId: Long,
    account: Account?,
    settings: AppSettings,
    defaultTtl: Int,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
    onToast: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val lang = LocalLang.current

    var type by remember { mutableStateOf(initial?.type ?: "A") }
    var name by remember { mutableStateOf(initial?.name?.takeIf { isNew.not() } ?: "") }
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
    var typeMenu by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    FullScreenDialog(
        title = if (isNew) t(Str.AddRecord) else t(Str.EditRecord),
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
            // 类型
            Box {
                TextButton(
                               text = "${t(Str.RecordType)}: $type",
                               onClick = { typeMenu = true },
                               modifier = Modifier.fillMaxWidth(),
                )
                DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                    RECORD_TYPES.forEach { ty ->
                        DropdownMenuItem(
                            text = { Text(ty) },
                            onClick = { type = ty; typeMenu = false },
                        )
                    }
                }
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
                    AppTextField(
                        value = priority, onValueChange = { priority = it.filter { c -> c.isDigit() } },
                        label = t(Str.RecordPriority), hint = "0", keyboardType = KeyboardType.Number,
                    )
                    AppTextField(
                        value = weight, onValueChange = { weight = it.filter { c -> c.isDigit() } },
                        label = t(Str.RecordWeight), keyboardType = KeyboardType.Number,
                    )
                    AppTextField(
                        value = port, onValueChange = { port = it.filter { c -> c.isDigit() } },
                        label = t(Str.RecordPort), keyboardType = KeyboardType.Number,
                    )
                    AppTextField(
                        value = target, onValueChange = { target = it },
                        label = t(Str.RecordTarget), placeholder = "example.com",
                    )
                }
                "CAA" -> {
                    AppTextField(
                        value = caaFlag, onValueChange = { caaFlag = it.filter { c -> c.isDigit() } },
                        label = t(Str.CaaFlag), keyboardType = KeyboardType.Number,
                    )
                    AppTextField(value = caaTag, onValueChange = { caaTag = it }, label = t(Str.CaaTag))
                    AppTextField(value = caaValue, onValueChange = { caaValue = it }, label = t(Str.CaaValue))
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
                            value = priority, onValueChange = { priority = it.filter { c -> c.isDigit() } },
                            label = t(Str.RecordPriority), hint = "10", keyboardType = KeyboardType.Number,
                        )
                    }
                }
            }

            AppTextField(
                value = ttl,
                onValueChange = { ttl = it.filter { c -> c.isDigit() } },
                label = t(Str.RecordTtl),
                hint = "600",
                keyboardType = KeyboardType.Number,
            )
            AppTextField(
                value = line,
                onValueChange = { line = it },
                label = t(Str.RecordLine),
                hint = t(Str.Optional),
            )

            Spacer(Modifier.height(4.dp))
        }
        InlineMessage(message, modifier = Modifier.padding(horizontal = 20.dp))
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
                TextButton(text = t(Str.Cancel), onClick = onDismiss, modifier = Modifier.weight(1f))
                Button(
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val acc = account ?: return@Button
                        if (type != "SRV" && type != "CAA" && content.isBlank()) {
                            onToast(t(Str.ContentRequired))
                            return@Button
                        }
                        if (type == "SRV" && target.isBlank()) {
                            onToast(t(Str.ContentRequired))
                            return@Button
                        }
                        if (type == "CAA" && caaValue.isBlank()) {
                            onToast(t(Str.ContentRequired))
                            return@Button
                        }
                        busy = true
                        scope.launch {
                            val ttlInt = ttl.toIntOrNull()
                            val priInt = priority.toIntOrNull()
                            val wInt = weight.toIntOrNull()
                            val pInt = port.toIntOrNull()
                            val fInt = caaFlag.toIntOrNull()
                            runCatching {
                                if (isNew) {
                                    AppContainer.api.createDnsRecord(
                                        creds = acc.creds, baseUrl = settings.baseUrl, timeoutSeconds = settings.timeoutSeconds,
                                        subdomainId = subdomainId, type = type, name = name.ifBlank { null },
                                        content = content.ifBlank { null }, ttl = ttlInt,
                                        priority = priInt, line = line.ifBlank { null },
                                        weight = wInt, port = pInt, target = target.ifBlank { null },
                                        caaFlag = if (type == "CAA") fInt else null,
                                        caaTag = if (type == "CAA") caaTag.ifBlank { null } else null,
                                        caaValue = if (type == "CAA") caaValue.ifBlank { null } else null,
                                    )
                                } else {
                                    AppContainer.api.updateDnsRecord(
                                        creds = acc.creds, baseUrl = settings.baseUrl, timeoutSeconds = settings.timeoutSeconds,
                                        id = initial?.id, recordId = initial?.recordId,
                                        type = type, name = name.ifBlank { null },
                                        content = content.ifBlank { null }, ttl = ttlInt,
                                        priority = priInt, line = line.ifBlank { null },
                                        weight = wInt, port = pInt, target = target.ifBlank { null },
                                        caaFlag = if (type == "CAA") fInt else null,
                                        caaTag = if (type == "CAA") caaTag.ifBlank { null } else null,
                                        caaValue = if (type == "CAA") caaValue.ifBlank { null } else null,
                                    )
                                }
                            }.onSuccess {
                                busy = false
                                onSaved(t(Str.RecordSaved))
                            }.onFailure { e ->
                                busy = false
                                val msg = apiErrorMessage(lang, e)
                                message = msg
                                onToast(msg)
                            }
                        }
                    },
                ) { Text(if (busy) t(Str.Loading) else t(Str.Save)) }
        }
        Spacer(Modifier.height(6.dp))
        }
    }
}
