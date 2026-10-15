package io.nekoshizuku.dnshe.desktop.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.nekoshizuku.dnshe.core.AppCore
import io.nekoshizuku.dnshe.core.AppLinks
import io.nekoshizuku.dnshe.core.AppPaths
import io.nekoshizuku.dnshe.core.AppSettings
import io.nekoshizuku.dnshe.core.Lang
import io.nekoshizuku.dnshe.core.ThemeMode
import io.nekoshizuku.dnshe.core.WinPaths
import io.nekoshizuku.dnshe.desktop.Sys
import io.nekoshizuku.dnshe.desktop.i18n.Str
import io.nekoshizuku.dnshe.desktop.i18n.t
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 版本号只从 [WinPaths.VERSION] 取，**不要再在这里写死**。
 * 之前这里是个 `const val APP_VERSION = "1.0.0"`，发版时忘了改，
 * 结果「关于」页一直显示旧版本（侧栏却是新的）。
 */
private val APP_VERSION: String get() = WinPaths.VERSION

@Composable
fun SettingsView(
    settings: AppSettings,
    onOpenLogs: () -> Unit,
    onOpenAbout: () -> Unit,
    onToast: (String) -> Unit,
) {
    val host = LocalOverlayHost.current
    val update: ((AppSettings) -> AppSettings) -> Unit = { AppCore.settings.update(it) }

    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(title = t(Str.SettingsTitle))
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
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
                        onClick = {
                            host.show {
                                ChoiceDialog(
                                    title = t(Str.Theme),
                                    options = listOf(
                                        ThemeMode.SYSTEM.name to t(Str.ThemeSystem),
                                        ThemeMode.LIGHT.name to t(Str.ThemeLight),
                                        ThemeMode.DARK.name to t(Str.ThemeDark),
                                    ),
                                    selected = settings.themeMode.name,
                                    onSelect = { v -> update { it.copy(themeMode = ThemeMode.valueOf(v)) } },
                                    onDismiss = { host.dismiss() },
                                )
                            }
                        },
                    )
                    SettingClickRow(
                        title = t(Str.Language),
                        value = when (settings.lang) {
                            Lang.SYSTEM -> t(Str.LangSystem)
                            Lang.ZH -> t(Str.LangZh)
                            Lang.EN -> t(Str.LangEn)
                        },
                        onClick = {
                            host.show {
                                ChoiceDialog(
                                    title = t(Str.Language),
                                    options = listOf(
                                        Lang.SYSTEM.name to t(Str.LangSystem),
                                        Lang.ZH.name to t(Str.LangZh),
                                        Lang.EN.name to t(Str.LangEn),
                                    ),
                                    selected = settings.lang.name,
                                    onSelect = { v -> update { it.copy(lang = Lang.valueOf(v)) } },
                                    onDismiss = { host.dismiss() },
                                )
                            }
                        },
                    )
                }
            }

            SectionTitle(t(Str.SettingsNetwork))
            AppCard {
                Column {
                    SettingClickRow(
                        title = t(Str.BaseUrl),
                        value = settings.baseUrl,
                        onClick = {
                            host.show {
                                InputOverlay2(
                                    title = t(Str.BaseUrl),
                                    label = t(Str.BaseUrl),
                                    hint = t(Str.BaseUrlHint),
                                    initial = settings.baseUrl,
                                    onConfirm = { v -> update { it.copy(baseUrl = v.trim().ifBlank { AppLinks.API_BASE }) } },
                                    onDismiss = { host.dismiss() },
                                )
                            }
                        },
                    )
                    SettingClickRow(
                        title = t(Str.Timeout),
                        value = t(Str.TimeoutFmt, settings.timeoutSeconds),
                        onClick = {
                            host.show {
                                InputOverlay2(
                                    title = t(Str.Timeout),
                                    label = t(Str.Timeout),
                                    hint = "5 ~ 180",
                                    initial = settings.timeoutSeconds.toString(),
                                    onConfirm = { v ->
                                        v.trim().toIntOrNull()?.let { n -> update { it.copy(timeoutSeconds = n.coerceIn(5, 180)) } }
                                    },
                                    onDismiss = { host.dismiss() },
                                )
                            }
                        },
                    )
                    SettingClickRow(
                        title = t(Str.PerPage),
                        value = t(Str.PerPageFmt, settings.perPage),
                        onClick = {
                            host.show {
                                ChoiceDialog(
                                    title = t(Str.PerPage),
                                    options = listOf("20", "50", "100", "200", "500").map { it to t(Str.PerPageFmt, it.toInt()) },
                                    selected = settings.perPage.toString(),
                                    onSelect = { v -> v.toIntOrNull()?.let { n -> update { it.copy(perPage = n) } } },
                                    onDismiss = { host.dismiss() },
                                )
                            }
                        },
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
                        onClick = {
                            host.show {
                                InputOverlay2(
                                    title = t(Str.DefaultRootDomain),
                                    label = t(Str.RootDomain),
                                    hint = t(Str.RootDomainHint),
                                    initial = settings.defaultRootDomain,
                                    onConfirm = { v -> update { it.copy(defaultRootDomain = v.trim()) } },
                                    onDismiss = { host.dismiss() },
                                )
                            }
                        },
                    )
                    SettingClickRow(
                        title = t(Str.DefaultTtl),
                        value = "${settings.defaultTtl}",
                        onClick = {
                            host.show {
                                InputOverlay2(
                                    title = t(Str.DefaultTtl),
                                    label = t(Str.RecordTtl),
                                    hint = "60 ~ 86400",
                                    initial = settings.defaultTtl.toString(),
                                    onConfirm = { v ->
                                        v.trim().toIntOrNull()?.let { n -> update { it.copy(defaultTtl = n.coerceIn(60, 86400)) } }
                                    },
                                    onDismiss = { host.dismiss() },
                                )
                            }
                        },
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
                        title = t(Str.OpenDataFolder),
                        desc = AppPaths.dataDir.absolutePath,
                        onClick = { Sys.openFolder(AppPaths.dataDir) },
                    )
                    SettingClickRow(
                        title = t(Str.ClearLogs),
                        onClick = {
                            host.show {
                                ConfirmOverlay(
                                    title = t(Str.ClearLogs),
                                    text = t(Str.ClearLogsConfirm),
                                    destructive = true,
                                    onConfirm = { AppCore.logs.clear(); onToast(t(Str.Success)); host.dismiss() },
                                    onDismiss = { host.dismiss() },
                                )
                            }
                        },
                    )
                    SettingClickRow(
                        title = t(Str.ClearAll),
                        onClick = {
                            host.show {
                                ConfirmOverlay(
                                    title = t(Str.ClearAll),
                                    text = t(Str.ClearAllConfirm),
                                    destructive = true,
                                    onConfirm = {
                                        AppCore.accounts.removeAll()
                                        AppCore.logs.clear()
                                        AppCore.settings.update { AppSettings() }
                                        onToast(t(Str.AllCleared))
                                        host.dismiss()
                                    },
                                    onDismiss = { host.dismiss() },
                                )
                            }
                        },
                    )
                }
            }

            SectionTitle(t(Str.SettingsAbout))
            AppCard {
                Column {
                    SettingClickRow(title = t(Str.AboutTitle), desc = "PC v$APP_VERSION", onClick = onOpenAbout)
                    SettingClickRow(title = t(Str.OpenOfficialSite), onClick = { Sys.openUrl(AppLinks.SITE) })
                    SettingClickRow(title = "API 文档", onClick = { Sys.openUrl(AppLinks.DOC) })
                }
            }

            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun InputOverlay2(
    title: String,
    label: String,
    hint: String? = null,
    initial: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    OverlayDialog(title = title, onDismiss = onDismiss, width = 480.dp) {
        AppTextField(value, { value = it }, label, hint = hint)
        DialogButtons(onCancel = onDismiss, onConfirm = { onConfirm(value) })
    }
}

/* ============================== 日志页 ============================== */

@Composable
fun LogsView(onBack: () -> Unit, onToast: (String) -> Unit) {
    val logs by AppCore.logs.entries.collectAsState()
    val host = LocalOverlayHost.current
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(title = t(Str.Logs), subtitle = t(Str.LogsDesc), onBack = onBack) {
            ToolbarButton(t(Str.ClearLogs)) {
                host.show {
                    ConfirmOverlay(
                        title = t(Str.ClearLogs),
                        text = t(Str.ClearLogsConfirm),
                        destructive = true,
                        onConfirm = { AppCore.logs.clear(); onToast(t(Str.Success)); host.dismiss() },
                        onDismiss = { host.dismiss() },
                    )
                }
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            if (logs.isEmpty()) {
                EmptyState( text = t(Str.LogsEmpty))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(22.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(logs.size) { idx ->
                        val e = logs[idx]
                        AppCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    e.method,
                                    fontSize = 14.sp,
                                    color = if (e.ok) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.error,
                                    modifier = Modifier.width(58.dp),
                                )
                                Text(
                                    "${e.endpoint}${e.action?.let { "/$it" } ?: ""}",
                                    fontSize = 14.sp, fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "${e.httpStatus}",
                                    fontSize = 14.sp,
                                    color = if (e.ok) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.error,
                                    modifier = Modifier.width(50.dp),
                                )
                                Text(
                                    "${fmt.format(Date(e.time))} · ${e.ms} ms" + (e.errorCode?.let { " · $it" } ?: ""),
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.width(260.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ============================== 关于页 ============================== */

@Composable
fun AboutView(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(title = t(Str.AboutTitle), onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(6.dp))
            AppLogo(72.dp)
            Spacer(Modifier.height(10.dp))
            Text(t(Str.AppName) + "  PC", fontSize = 22.sp, color = MiuixTheme.colorScheme.primary)
            Text("v$APP_VERSION · Windows", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Spacer(Modifier.height(18.dp))
            AppCard(modifier = Modifier.width(680.dp)) {
                Column {
                    Text(t(Str.AboutDesc), fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    InfoRow(t(Str.Version), APP_VERSION)
                    InfoRow(t(Str.PackageName), "io.nekoshizuku.dnshe.desktop", mono = true)
                    InfoRow(t(Str.BasedOn), "V2.0")
                    InfoRow(t(Str.DataFolderDesc), AppPaths.dataDir.absolutePath, mono = true)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        t(Str.JsonInteropNote),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            AppCard(modifier = Modifier.width(680.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(text = t(Str.OpenOfficialSite), onClick = { Sys.openUrl(AppLinks.SITE) })
                    TextButton(text = t(Str.OpenRegisterPage), onClick = { Sys.openUrl(AppLinks.REGISTER) })
                    TextButton(text = "API 文档", onClick = { Sys.openUrl(AppLinks.DOC) })
                    TextButton(text = t(Str.OpenDataFolder), onClick = { Sys.openFolder(AppPaths.dataDir) })
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}
