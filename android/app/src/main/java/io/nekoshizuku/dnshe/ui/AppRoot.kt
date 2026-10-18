package io.nekoshizuku.dnshe.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import io.nekoshizuku.dnshe.data.Account
import io.nekoshizuku.dnshe.data.MailApi
import io.nekoshizuku.dnshe.data.MailMessage
import io.nekoshizuku.dnshe.data.MailboxAccount
import io.nekoshizuku.dnshe.data.AppContainer
import io.nekoshizuku.dnshe.data.Lang
import io.nekoshizuku.dnshe.i18n.ProvideStrings
import io.nekoshizuku.dnshe.i18n.Str
import io.nekoshizuku.dnshe.i18n.t
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Email
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost
import java.util.Locale

/** 导航目标 */
sealed interface Dest {
    data object TabDomains : Dest
    data object TabAccounts : Dest
    data object TabTools : Dest
    data object TabMail : Dest
    data object TabSettings : Dest

    data class DomainDetail(val id: Long) : Dest
    data object Register : Dest
    data object Quota : Dest
    data object ApiKeys : Dest
    data object Whois : Dest
    data object Upgrade : Dest
    data object Logs : Dest
    data object About : Dest
    data object ReleaseHistory : Dest
    data object MailCompose : Dest
    data object MailAccounts : Dest
    data class MailDetail(val messageId: String) : Dest
}

fun resolveLang(l: Lang): Lang = when (l) {
    Lang.SYSTEM -> {
        val tag = Locale.getDefault().language.lowercase()
        if (tag.startsWith("zh")) Lang.ZH else Lang.EN
    }
    else -> l
}

@Composable
fun AppRoot() {
    val settings by AppContainer.settings.state.collectAsState()
    val accounts by AppContainer.accounts.accounts.collectAsState()
    val currentId by AppContainer.accounts.currentId.collectAsState()
    val brokenIds by AppContainer.accounts.brokenIds.collectAsState()

    val lang = resolveLang(settings.lang)
    val dark = themeIsDark(settings.themeMode)

    ProvideStrings(lang) {
        ShizukuTheme(dark = dark, dynamic = settings.dynamicColor) {
            val snackbar = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            val toast: (String) -> Unit = { msg ->
                scope.launch {
                    snackbar.showSnackbar(message = msg, duration = SnackbarDuration.Short)
                }
            }

            val mailboxes by AppContainer.mailboxes.mailboxes.collectAsState()
            val mailCurrentId by AppContainer.mailboxes.currentId.collectAsState()
            var editingMailbox by remember { mutableStateOf<MailboxAccount?>(null) }
            var openedMail by remember { mutableStateOf<MailMessage?>(null) }
            // 更新说明：上次看到的版本 != 当前版本就弹
            var showReleaseNotes by remember {
                mutableStateOf(
                    settings.lastSeenVersion.isBlank() ||
                        settings.lastSeenVersion != LATEST_VERSION
                )
            }
            var tab by remember { mutableStateOf<Dest>(Dest.TabDomains) }
            val stack = remember { mutableStateListOf<Dest>() }
            var refreshTick by remember { mutableIntStateOf(0) }

            val account: Account? = accounts.firstOrNull { it.id == currentId } ?: accounts.firstOrNull()

            BackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }

            val current: Dest = stack.lastOrNull() ?: tab

            fun push(d: Dest) = stack.add(d)
            fun back() {
                if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
            }

            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        when (current) {
                            Dest.TabDomains -> DomainsScreen(
                                account = account,
                                settings = settings,
                                refreshTick = refreshTick,
                                onOpenRegister = { push(Dest.Register) },
                                onOpenDetail = { push(Dest.DomainDetail(it.id)) },
                                onGoAccounts = { tab = Dest.TabAccounts },
                                onToast = toast,
                            )

                            Dest.TabAccounts -> AccountsScreen(
                                accounts = accounts,
                                currentId = currentId,
                                brokenIds = brokenIds,
                                clipboardDetect = settings.clipboardDetect,
                                onSelect = { AppContainer.accounts.select(it); toast(t(Str.SwitchTo)) },
                                onSave = { acc ->
                                    if (accounts.any { it.id == acc.id }) AppContainer.accounts.update(acc)
                                    else AppContainer.accounts.add(acc)
                                },
                                onDelete = { acc ->
                                    AppContainer.accounts.remove(acc.id)
                                    toast(t(Str.AccountDeleted))
                                },
                                onToast = toast,
                            )

                            Dest.TabTools -> ToolsScreen(
                                hasAccount = account != null,
                                onOpenQuota = { push(Dest.Quota) },
                                onOpenKeys = { push(Dest.ApiKeys) },
                                onOpenWhois = { push(Dest.Whois) },
                                onOpenUpgrade = { push(Dest.Upgrade) },
                            )

                            Dest.TabSettings -> SettingsScreen(
                                settings = settings,
                                onOpenLogs = { push(Dest.Logs) },
                                onOpenAbout = { push(Dest.About) },
                                onOpenReleaseNotes = { push(Dest.ReleaseHistory) },
                                onToast = toast,
                            )

                            is Dest.DomainDetail -> DomainDetailScreen(
                                subdomainId = (current as Dest.DomainDetail).id,
                                account = account,
                                settings = settings,
                                onBack = { back(); refreshTick++ },
                                onToast = toast,
                            )

                            Dest.Register -> RegisterDomainScreen(
                                account = account,
                                settings = settings,
                                onBack = { back() },
                                onRegistered = {
                                    back()
                                    refreshTick++
                                    tab = Dest.TabDomains
                                },
                                onToast = toast,
                            )

                            Dest.Quota -> QuotaScreen(
                                account = account,
                                settings = settings,
                                onBack = { back() },
                                onToast = toast,
                            )

                            Dest.ApiKeys -> ApiKeysScreen(
                                account = account,
                                settings = settings,
                                onBack = { back() },
                                onSaveAsAccount = { key, secret, label ->
                                    AppContainer.accounts.add(
                                        Account(
                                            name = label + " · " + key.take(8),
                                            apiKey = key,
                                            apiSecret = secret,
                                            defaultRootDomain = settings.defaultRootDomain,
                                        )
                                    )
                                    toast(t(Str.AccountSaved))
                                },
                                onToast = toast,
                            )

                            Dest.Whois -> WhoisScreen(
                                account = account,
                                settings = settings,
                                onBack = { back() },
                                onToast = toast,
                            )

                            Dest.Upgrade -> UpgradeScreen(
                                account = account,
                                settings = settings,
                                onBack = { back() },
                                onToast = toast,
                            )

                            Dest.Logs -> LogsScreen(onBack = { back() }, onToast = toast)
                            Dest.About -> AboutScreen(onBack = { back() })
                            Dest.ReleaseHistory -> ReleaseHistoryScreen(lang = lang, onBack = { back() })

                            /* ---------------- 邮箱 ---------------- */
                            Dest.TabMail -> MailScreen(
                                mailbox = mailboxes.firstOrNull { it.id == mailCurrentId }
                                    ?: mailboxes.firstOrNull(),
                                api = AppContainer.mailApi,
                                onBack = { tab = Dest.TabDomains },
                                onCompose = { push(Dest.MailCompose) },
                                onOpenMessage = { openedMail = it; push(Dest.MailDetail(it.id)) },
                                onToast = toast,
                                onNeedAccount = { editingMailbox = MailboxAccount(); push(Dest.MailAccounts) },
                            )

                            Dest.MailAccounts -> MailAccountsScreen(
                                mailboxes = mailboxes,
                                currentId = mailCurrentId,
                                api = AppContainer.mailApi,
                                onBack = { back() },
                                onSelect = {
                                    AppContainer.mailboxes.select(it.id)
                                    toast(t(Str.SwitchTo))
                                    back()
                                },
                                onEdit = { editingMailbox = it ?: MailboxAccount() },
                                onDelete = {
                                    AppContainer.mailboxes.remove(it.id)
                                    toast(t(Str.Delete))
                                },
                                onToast = toast,
                            )

                            Dest.MailCompose -> {
                                val mb = mailboxes.firstOrNull { it.id == mailCurrentId }
                                    ?: mailboxes.firstOrNull()
                                if (mb == null) {
                                    MailAccountsScreen(
                                        mailboxes = mailboxes,
                                        currentId = mailCurrentId,
                                        api = AppContainer.mailApi,
                                        onBack = { back() },
                                        onSelect = { AppContainer.mailboxes.select(it.id); back() },
                                        onEdit = { editingMailbox = it ?: MailboxAccount() },
                                        onDelete = { AppContainer.mailboxes.remove(it.id) },
                                        onToast = toast,
                                    )
                                } else {
                                    MailComposeScreen(
                                        mailbox = mb,
                                        api = AppContainer.mailApi,
                                        onBack = { back() },
                                        onSent = { back(); tab = Dest.TabMail },
                                        onToast = toast,
                                    )
                                }
                            }

                            is Dest.MailDetail -> {
                                val mb = mailboxes.firstOrNull { it.id == mailCurrentId }
                                    ?: mailboxes.firstOrNull()
                                if (mb != null) {
                                    MailDetailScreen(
                                        message = openedMail ?: MailMessage(),
                                        token = mb.token,
                                        api = AppContainer.mailApi,
                                        onBack = { back() },
                                        onToast = toast,
                                    )
                                }
                            }
                        }
                    }

                    if (stack.isEmpty()) {
                        // Miuix 的底部导航：图标来自 miuix-icons，选中态是 HyperOS 的高亮胶囊
                        NavigationBar {
                            NavigationBarItem(
                                selected = tab == Dest.TabDomains,
                                onClick = { tab = Dest.TabDomains },
                                icon = MiuixIcons.Layers,
                                label = t(Str.TabDomains),
                            )
                            NavigationBarItem(
                                selected = tab == Dest.TabAccounts,
                                onClick = { tab = Dest.TabAccounts },
                                icon = MiuixIcons.Contacts,
                                label = t(Str.TabAccounts),
                            )
                            NavigationBarItem(
                                selected = tab == Dest.TabTools,
                                onClick = { tab = Dest.TabTools },
                                icon = MiuixIcons.GridView,
                                label = t(Str.TabTools),
                            )
                            NavigationBarItem(
                                selected = tab == Dest.TabMail,
                                onClick = { tab = Dest.TabMail },
                                icon = MiuixIcons.Email,
                                label = t(Str.MailTitle),
                            )
                            NavigationBarItem(
                                selected = tab == Dest.TabSettings,
                                onClick = { tab = Dest.TabSettings },
                                icon = MiuixIcons.Settings,
                                label = t(Str.TabSettings),
                            )
                        }
                    }
                }

                // 邮箱账号编辑对话框（全局挂载，任何入口都能唤起）
                                editingMailbox?.let { target ->
                                        androidx.compose.runtime.key(target.id) {
                    MailboxEditorDialog(
                        initial = target.takeIf { it.id.isNotBlank() },
                        api = AppContainer.mailApi,
                        onSave = { saved ->
                            if (mailboxes.any { it.id == saved.id }) {
                                AppContainer.mailboxes.update(saved)
                            } else {
                                AppContainer.mailboxes.add(saved)
                            }
                            editingMailbox = null
                            toast(t(Str.Save))
                        },
                        onDismiss = { editingMailbox = null },
                        onToast = toast,
                    )
                    }
                }
                // 更新说明弹窗
                if (showReleaseNotes) {
                    noteFor(LATEST_VERSION)?.let { note ->
                        ReleaseNotesDialog(
                            note = note,
                            lang = lang,
                            onDismiss = {
                                showReleaseNotes = false
                                AppContainer.settings.update { it.copy(lastSeenVersion = LATEST_VERSION) }
                            },
                            hasHistory = RELEASE_NOTES.size > 1,
                            onOpenHistory = {
                                showReleaseNotes = false
                                AppContainer.settings.update { it.copy(lastSeenVersion = LATEST_VERSION) }
                                push(Dest.ReleaseHistory)
                            },
                        )
                    }
                }
                SnackbarHost(
                    state = snackbar,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (stack.isEmpty()) 96.dp else 18.dp),
                )

                // Miuix 的弹窗宿主：确认框 / 选择框（OverlayDialog）靠它渲染
                MiuixPopupHost()
            }
        }
    }
}
