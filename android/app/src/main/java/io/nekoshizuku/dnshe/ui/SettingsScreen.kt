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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.nekoshizuku.dnshe.AppLinks
import io.nekoshizuku.dnshe.BuildConfig
import io.nekoshizuku.dnshe.R
import io.nekoshizuku.dnshe.data.AppContainer
import io.nekoshizuku.dnshe.data.AppSettings
import io.nekoshizuku.dnshe.data.Lang
import io.nekoshizuku.dnshe.data.ThemeMode
import io.nekoshizuku.dnshe.i18n.Str
import io.nekoshizuku.dnshe.i18n.t
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onOpenLogs: () -> Unit,
    onOpenAbout: () -> Unit,
    onToast: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val update: ((AppSettings) -> AppSettings) -> Unit = { AppContainer.settings.update(it) }

    var themeDialog by remember { mutableStateOf(false) }
    var langDialog by remember { mutableStateOf(false) }
    var baseUrlDialog by remember { mutableStateOf(false) }
    var timeoutDialog by remember { mutableStateOf(false) }
    var perPageDialog by remember { mutableStateOf(false) }
    var rootDialog by remember { mutableStateOf(false) }
    var ttlDialog by remember { mutableStateOf(false) }
    var confirmClearLogs by remember { mutableStateOf(false) }
    var confirmClearAll by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(t(Str.SettingsTitle)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            SectionTitle(t(Str.SettingsAppearance))
            AppCard {
                Column {
                    SettingClickRow(
                        title = t(Str.Theme),
                        value = when (settings.themeMode) {
                            ThemeMode.SYSTEM -> t(Str.ThemeSystem)
                            ThemeMode.LIGHT -> t(Str.ThemeLight)
                            ThemeMode.DARK -> t(Str.ThemeDark)
                        },
                        onClick = { themeDialog = true },
                    )
                    SettingSwitchRow(
                        title = t(Str.DynamicColor),
                        desc = t(Str.DynamicColorDesc),
                        checked = settings.dynamicColor,
                        onCheckedChange = { v -> update { it.copy(dynamicColor = v) } },
                    )
                }
            }

            SectionTitle(t(Str.SettingsLanguage))
            AppCard {
                SettingClickRow(
                    title = t(Str.Language),
                    value = when (settings.lang) {
                        Lang.SYSTEM -> t(Str.LangSystem)
                        Lang.ZH -> t(Str.LangZh)
                        Lang.EN -> t(Str.LangEn)
                    },
                    onClick = { langDialog = true },
                )
            }

            SectionTitle(t(Str.SettingsNetwork))
            AppCard {
                Column {
                    SettingClickRow(
                        title = t(Str.BaseUrl),
                        value = settings.baseUrl,
                        onClick = { baseUrlDialog = true },
                    )
                    SettingClickRow(
                        title = t(Str.Timeout),
                        value = t(Str.TimeoutFmt, settings.timeoutSeconds),
                        onClick = { timeoutDialog = true },
                    )
                    SettingClickRow(
                        title = t(Str.PerPage),
                        value = t(Str.PerPageFmt, settings.perPage),
                        onClick = { perPageDialog = true },
                    )
                    SettingSwitchRow(
                        title = t(Str.AutoRefresh),
                        desc = t(Str.AutoRefreshDesc),
                        checked = settings.autoRefresh,
                        onCheckedChange = { v -> update { it.copy(autoRefresh = v) } },
                    )
                }
            }

            SectionTitle(t(Str.SettingsDefaults))
            AppCard {
                Column {
                    SettingClickRow(
                        title = t(Str.DefaultRootDomain),
                        value = settings.defaultRootDomain.ifBlank { t(Str.NotSet) },
                        onClick = { rootDialog = true },
                    )
                    SettingClickRow(
                        title = t(Str.DefaultTtl),
                        value = "${settings.defaultTtl}",
                        onClick = { ttlDialog = true },
                    )
                }
            }

            SectionTitle(t(Str.SettingsSecurity))
            AppCard {
                Column {
                    SettingSwitchRow(
                        title = t(Str.ClipboardDetect),
                        desc = t(Str.ClipboardDetectDesc),
                        checked = settings.clipboardDetect,
                        onCheckedChange = { v -> update { it.copy(clipboardDetect = v) } },
                    )
                    Text(
                        t(Str.KeystoreNote),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionTitle(t(Str.SettingsData))
            AppCard {
                Column {
                    SettingClickRow(
                        title = t(Str.Logs),
                        desc = t(Str.LogsDesc),
                        onClick = onOpenLogs,
                    )
                    SettingClickRow(
                        title = t(Str.ClearLogs),
                        onClick = { confirmClearLogs = true },
                    )
                    SettingClickRow(
                        title = t(Str.ClearAll),
                        onClick = { confirmClearAll = true },
                    )
                }
            }

            SectionTitle(t(Str.SettingsAbout))
            AppCard {
                Column {
                    SettingClickRow(
                        title = t(Str.AboutTitle),
                        desc = "v${BuildConfig.VERSION_NAME}",
                        onClick = onOpenAbout,
                    )
                    SettingClickRow(
                        title = t(Str.ResetDefaults),
                        onClick = { confirmReset = true },
                    )
                    SettingClickRow(
                        title = t(Str.OpenOfficialSite),
                        onClick = { ctx.openUrl(AppLinks.SITE) },
                    )
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    if (themeDialog) {
        ChoiceDialog(
            title = t(Str.Theme),
            options = listOf(
                ThemeMode.SYSTEM.name to t(Str.ThemeSystem),
                ThemeMode.LIGHT.name to t(Str.ThemeLight),
                ThemeMode.DARK.name to t(Str.ThemeDark),
            ),
            selected = settings.themeMode.name,
            onSelect = { v -> update { it.copy(themeMode = ThemeMode.valueOf(v)) } },
            onDismiss = { themeDialog = false },
        )
    }

    if (langDialog) {
        ChoiceDialog(
            title = t(Str.Language),
            options = listOf(
                Lang.SYSTEM.name to t(Str.LangSystem),
                Lang.ZH.name to t(Str.LangZh),
                Lang.EN.name to t(Str.LangEn),
            ),
            selected = settings.lang.name,
            onSelect = { v -> update { it.copy(lang = Lang.valueOf(v)) } },
            onDismiss = { langDialog = false },
        )
    }

    if (baseUrlDialog) {
        InputDialog(
            title = t(Str.BaseUrl),
            label = t(Str.BaseUrl),
            hint = t(Str.BaseUrlHint),
            initial = settings.baseUrl,
            onDismiss = { baseUrlDialog = false },
            onConfirm = { v ->
                update { it.copy(baseUrl = v.trim().ifBlank { AppLinks.API_BASE }) }
            },
        )
    }

    if (timeoutDialog) {
        InputDialog(
            title = t(Str.Timeout),
            label = t(Str.Timeout),
            hint = "5 ~ 180",
            initial = settings.timeoutSeconds.toString(),
            onDismiss = { timeoutDialog = false },
            onConfirm = { v ->
                v.trim().toIntOrNull()?.let { n -> update { it.copy(timeoutSeconds = n.coerceIn(5, 180)) } }
            },
        )
    }

    if (perPageDialog) {
        ChoiceDialog(
            title = t(Str.PerPage),
            options = listOf("20", "50", "100", "200", "500").map { it to t(Str.PerPageFmt, it.toInt()) },
            selected = settings.perPage.toString(),
            onSelect = { v -> v.toIntOrNull()?.let { n -> update { it.copy(perPage = n) } } },
            onDismiss = { perPageDialog = false },
        )
    }

    if (rootDialog) {
        InputDialog(
            title = t(Str.DefaultRootDomain),
            label = t(Str.RootDomain),
            hint = t(Str.RootDomainHint),
            initial = settings.defaultRootDomain,
            onDismiss = { rootDialog = false },
            onConfirm = { v -> update { it.copy(defaultRootDomain = v.trim()) } },
        )
    }

    if (ttlDialog) {
        InputDialog(
            title = t(Str.DefaultTtl),
            label = t(Str.RecordTtl),
            hint = "600",
            initial = settings.defaultTtl.toString(),
            onDismiss = { ttlDialog = false },
            onConfirm = { v ->
                v.trim().toIntOrNull()?.let { n -> update { it.copy(defaultTtl = n.coerceIn(60, 86400)) } }
            },
        )
    }

    if (confirmClearLogs) {
        ConfirmDialog(
            title = t(Str.ClearLogs),
            text = t(Str.ClearLogsConfirm),
            destructive = true,
            onConfirm = {
                AppContainer.logs.clear()
                onToast(t(Str.Success))
            },
            onDismiss = { confirmClearLogs = false },
        )
    }

    if (confirmClearAll) {
        ConfirmDialog(
            title = t(Str.ClearAll),
            text = t(Str.ClearAllConfirm),
            destructive = true,
            onConfirm = {
                AppContainer.accounts.removeAll()
                AppContainer.logs.clear()
                AppContainer.settings.update { AppSettings() }
                onToast(t(Str.AllCleared))
            },
            onDismiss = { confirmClearAll = false },
        )
    }

    if (confirmReset) {
        ConfirmDialog(
            title = t(Str.ResetDefaults),
            text = t(Str.ResetDefaultsConfirm),
            onConfirm = {
                AppContainer.settings.update { AppSettings() }
                onToast(t(Str.ResetDone))
            },
            onDismiss = { confirmReset = false },
        )
    }
}

/* ============================== 日志页 ============================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    onBack: () -> Unit,
    onToast: (String) -> Unit,
) {
    val logs by AppContainer.logs.entries.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(t(Str.Logs)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = t(Str.Back)) }
                },
                actions = {
                    IconButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = t(Str.ClearLogs))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Box(modifier = Modifier.fillMaxSize().padding(pad)) {
            if (logs.isEmpty()) {
                EmptyState(text = t(Str.LogsEmpty), icon = { StateIcon(Icons.Filled.List) })
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(logs.size) { idx ->
                        val e = logs[idx]
                        AppCard {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        e.method,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (e.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.width(52.dp),
                                    )
                                    Text(
                                        "${e.endpoint}${e.action?.let { "/$it" } ?: ""}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        "${e.httpStatus}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (e.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${fmt.format(Date(e.time))} · ${e.ms} ms" + (e.errorCode?.let { " · $it" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                e.message?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = t(Str.ClearLogs),
            text = t(Str.ClearLogsConfirm),
            destructive = true,
            onConfirm = {
                AppContainer.logs.clear()
                onToast(t(Str.Success))
            },
            onDismiss = { confirmClear = false },
        )
    }
}

/* ============================== 关于页 ============================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(t(Str.AboutTitle)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = t(Str.Back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(18.dp))
            AppLogo(72.dp)
            Spacer(Modifier.height(10.dp))
            Text(t(Str.AppName), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            AppCard {
                Column {
                    Text(t(Str.AboutDesc), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    KeyValueRow(t(Str.Version), BuildConfig.VERSION_NAME)
                    KeyValueRow(t(Str.PackageName), BuildConfig.APPLICATION_ID, mono = true)
                    KeyValueRow(t(Str.BasedOn), "V2.0")
                }
            }
            Spacer(Modifier.height(12.dp))
            AppCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painterResource(R.drawable.ic_open_in_new),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(t(Str.OpenOfficialSite), modifier = Modifier.weight(1f))
                        TextButton(onClick = { ctx.openUrl(AppLinks.SITE) }) { Text(t(Str.Ok)) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painterResource(R.drawable.ic_globe),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("API 文档", modifier = Modifier.weight(1f))
                        TextButton(onClick = { ctx.openUrl(AppLinks.DOC) }) { Text(t(Str.Ok)) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(t(Str.OpenRegisterPage), modifier = Modifier.weight(1f))
                        TextButton(onClick = { ctx.openUrl(AppLinks.REGISTER) }) { Text(t(Str.Ok)) }
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
