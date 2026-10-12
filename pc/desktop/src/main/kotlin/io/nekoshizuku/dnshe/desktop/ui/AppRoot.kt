package io.nekoshizuku.dnshe.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.nekoshizuku.dnshe.core.Account
import io.nekoshizuku.dnshe.core.AppCore
import io.nekoshizuku.dnshe.core.Lang
import io.nekoshizuku.dnshe.desktop.i18n.LocalLang
import io.nekoshizuku.dnshe.desktop.i18n.ProvideStrings
import io.nekoshizuku.dnshe.desktop.i18n.Str
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import io.nekoshizuku.dnshe.desktop.i18n.of
import io.nekoshizuku.dnshe.desktop.i18n.t
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

sealed interface Dest {
    data object Domains : Dest
    data object Accounts : Dest
    data object Tools : Dest
    data object Settings : Dest

    data class DomainDetail(val id: Long) : Dest
    data object Register : Dest
    data object Quota : Dest
    data object ApiKeys : Dest
    data object Whois : Dest
    data object Upgrade : Dest
    data object Logs : Dest
    data object About : Dest
}

fun resolveLang(l: Lang): Lang = when (l) {
    Lang.SYSTEM -> if (Locale.getDefault().language.lowercase().startsWith("zh")) Lang.ZH else Lang.EN
    else -> l
}

@Composable
fun AppRoot() {
    val settings by AppCore.settings.state.collectAsState()
    val accounts by AppCore.accounts.accounts.collectAsState()
    val currentId by AppCore.accounts.currentId.collectAsState()
    val brokenIds by AppCore.accounts.brokenIds.collectAsState()

    val lang = resolveLang(settings.lang)

    ShizukuTheme(settings.themeMode) {
        ProvideStrings(lang) {
            val host = remember { OverlayHost() }
            val scope = rememberCoroutineScope()

            var toast by remember { mutableStateOf<String?>(null) }
            val toastFn: (String) -> Unit = { msg ->
                toast = msg
                scope.launch {
                    delay(3600)
                    if (toast == msg) toast = null
                }
            }

            var tab by remember { mutableStateOf<Dest>(Dest.Domains) }
            val stack = remember { mutableStateListOf<Dest>() }
            var refreshTick by remember { mutableIntStateOf(0) }

            val account: Account? = accounts.firstOrNull { it.id == currentId } ?: accounts.firstOrNull()
            val current: Dest = stack.lastOrNull() ?: tab

            fun push(d: Dest) = stack.add(d)
            fun back() {
                if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
            }

            CompositionLocalProvider(LocalOverlayHost provides host) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MiuixTheme.colorScheme.background),
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Sidebar(
                            current = current,
                            account = account,
                            onSelectTab = { t ->
                                stack.clear()
                                tab = t
                            },
                        )

                        Column(modifier = Modifier.fillMaxSize()) {
                            when (current) {
                                Dest.Domains -> DomainsView(
                                    account = account,
                                    settings = settings,
                                    refreshTick = refreshTick,
                                    onOpenRegister = { push(Dest.Register) },
                                    onOpenDetail = { push(Dest.DomainDetail(it)) },
                                    onGoAccounts = { stack.clear(); tab = Dest.Accounts },
                                    onToast = toastFn,
                                )

                                Dest.Accounts -> AccountsView(
                                    accounts = accounts,
                                    currentId = currentId,
                                    brokenIds = brokenIds,
                                    onToast = toastFn,
                                )

                                Dest.Tools -> ToolsView(
                                    hasAccount = account != null,
                                    onOpenQuota = { push(Dest.Quota) },
                                    onOpenKeys = { push(Dest.ApiKeys) },
                                    onOpenWhois = { push(Dest.Whois) },
                                    onOpenUpgrade = { push(Dest.Upgrade) },
                                )

                                Dest.Settings -> SettingsView(
                                    settings = settings,
                                    onOpenLogs = { push(Dest.Logs) },
                                    onOpenAbout = { push(Dest.About) },
                                    onToast = toastFn,
                                )

                                is Dest.DomainDetail -> DomainDetailView(
                                    subdomainId = (current as Dest.DomainDetail).id,
                                    account = account,
                                    settings = settings,
                                    onBack = { back(); refreshTick++ },
                                    onToast = toastFn,
                                )

                                Dest.Register -> RegisterView(
                                    account = account,
                                    settings = settings,
                                    onBack = { back() },
                                    onRegistered = {
                                        back()
                                        refreshTick++
                                        tab = Dest.Domains
                                    },
                                    onToast = toastFn,
                                )

                                Dest.Quota -> QuotaView(
                                    account = account,
                                    settings = settings,
                                    onBack = { back() },
                                )

                                Dest.ApiKeys -> ApiKeysView(
                                    account = account,
                                    settings = settings,
                                    onBack = { back() },
                                    onSaveAsAccount = { key, secret, label ->
                                        AppCore.accounts.add(
                                            Account(
                                                name = label + " · " + key.take(8),
                                                apiKey = key,
                                                apiSecret = secret,
                                                defaultRootDomain = settings.defaultRootDomain,
                                            )
                                        )
                                        toastFn(t(Str.AccountSaved))
                                    },
                                    onToast = toastFn,
                                )

                                Dest.Whois -> WhoisView(
                                    account = account,
                                    settings = settings,
                                    onBack = { back() },
                                    onToast = toastFn,
                                )

                                Dest.Upgrade -> UpgradeView(
                                    account = account,
                                    settings = settings,
                                    onBack = { back() },
                                    onToast = toastFn,
                                )

                                Dest.Logs -> LogsView(onBack = { back() }, onToast = toastFn)
                                Dest.About -> AboutView(onBack = { back() })
                            }
                        }
                    }

                    // 全局浮层（盖住侧边栏）
                    host.dialog?.let { dialog ->
                        dialog()
                    }

                    // 底部提示条
                    AnimatedVisibility(
                        visible = toast != null,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MiuixTheme.colorScheme.onSurface,
                            shadowElevation = 8.dp,
                        ) {
                            Text(
                                text = toast.orEmpty(),
                                color = MiuixTheme.colorScheme.surface,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Sidebar(
    current: Dest,
    account: Account?,
    onSelectTab: (Dest) -> Unit,
) {
    val items = listOf(
        Triple(Dest.Domains, MiuixIcons.Layers, Str.TabDomains),
        Triple(Dest.Accounts, MiuixIcons.Contacts, Str.TabAccounts),
        Triple(Dest.Tools, MiuixIcons.GridView, Str.TabTools),
        Triple(Dest.Settings, MiuixIcons.Settings, Str.TabSettings),
    )
    val activeTab: Dest = when (current) {
        Dest.Domains, Dest.Register, is Dest.DomainDetail -> Dest.Domains
        Dest.Accounts -> Dest.Accounts
        Dest.Tools, Dest.Quota, Dest.ApiKeys, Dest.Whois, Dest.Upgrade -> Dest.Tools
        Dest.Settings, Dest.Logs, Dest.About -> Dest.Settings
        else -> Dest.Domains
    }

    val c = MiuixTheme.colorScheme

    Box(
        modifier = Modifier.width(212.dp).fillMaxHeight().background(c.surface),
    ) {
        Column(modifier = Modifier.fillMaxHeight().padding(vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppLogo(34.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(t(Str.AppName), color = c.primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("PC v2.0.0", color = c.onSurfaceVariantSummary, fontSize = 11.5.sp)
                }
            }

            Spacer(Modifier.height(14.dp))

            items.forEach { (dest, icon, label) ->
                val selected = activeTab == dest
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) c.primaryContainer else Color.Transparent)
                        .clickable { onSelectTab(dest) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selected) c.onPrimaryContainer else c.onSurfaceVariantSummary,
                        modifier = Modifier.size(19.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = label.of(LocalLang.current),
                        fontSize = 14.5.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) c.onPrimaryContainer else c.onSurface,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Divider()
                Spacer(Modifier.height(10.dp))
                Text(
                    t(Str.CurrentAccount),
                    color = c.onSurfaceVariantSummary,
                    fontSize = 11.5.sp,
                )
                Text(
                    text = account?.name?.ifBlank { account.maskedKey } ?: t(Str.NotSet),
                    color = if (account != null) c.primary else c.onSurfaceVariantSummary,
                    fontSize = 13.sp,
                )
            }
        }
    }
}
