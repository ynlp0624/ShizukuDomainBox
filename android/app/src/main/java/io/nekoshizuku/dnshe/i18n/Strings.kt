package io.nekoshizuku.dnshe.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.staticCompositionLocalOf
import io.nekoshizuku.dnshe.data.Lang

/**
 * 应用内文案表。做成「枚举 + 两套语言」而不是 res/values-xx，
 * 这样在设置里切换语言可以立刻生效，不用重建 Activity。
 *
 * 注意：带参数的文案必须把美元符号转义（%1\$s / %1\$d），
 * 否则 Kotlin 会把它当成字符串模板。
 */
enum class Str(val zh: String, val en: String) {

    AppName("雫的域名盒", "Shizuku Domain Box"),

    /* 通用 */
    Ok("好的", "OK"),
    Cancel("取消", "Cancel"),
    Save("保存", "Save"),
    Delete("删除", "Delete"),
    Edit("编辑", "Edit"),
    Add("添加", "Add"),
    Close("关闭", "Close"),
    Confirm("确定", "Confirm"),
    Retry("重试", "Retry"),
    Refresh("刷新", "Refresh"),
    Search("搜索", "Search"),
    Copy("复制", "Copy"),
    Copied("已复制到剪贴板喵", "Copied"),
    Clear("清空", "Clear"),
    Loading("加载中…", "Loading…"),
    Empty("空空如也", "Nothing here"),
    Error("出错了", "Error"),
    Warning("注意", "Warning"),
    Success("成功", "Success"),
    Back("返回", "Back"),
    All("全部", "All"),
    Unknown("未知", "Unknown"),
    NotSet("未设置", "Not set"),
    Query("查询", "Query"),
    Apply("申请", "Apply"),
    Yes("是", "Yes"),
    No("否", "No"),
    More("更多", "More"),
    Details("详情", "Details"),
    Optional("可选", "optional"),
    Required("必填", "required"),
    NeverExpires("永不过期", "Never expires"),
    ShowSecret("显示密钥", "Show secret"),
    HideSecret("隐藏密钥", "Hide secret"),

    /* 底部导航 */
    TabDomains("域名", "Domains"),
    TabAccounts("账号", "Accounts"),
    TabTools("工具", "Tools"),
    TabSettings("设置", "Settings"),

    /* 账号 */
    AccountsTitle("账号管理", "Accounts"),
    AccountsEmpty("还没有账号喵。点右下角 + 添加，或者先去官网注册一个~", "No accounts yet. Tap + to add one."),
    AddAccount("添加账号", "Add account"),
    EditAccount("编辑账号", "Edit account"),
    AccountName("备注名", "Label"),
    AccountNameHint("例如：主力号", "e.g. main account"),
    ApiKey("API Key", "API Key"),
    ApiKeyHint("cfsd_xxxxxxxxxx", "cfsd_xxxxxxxxxx"),
    ApiSecret("API Secret", "API Secret"),
    ApiSecretHint("创建密钥时显示的那一串", "shown once when created"),
    DefaultRootDomain("常用根域名", "Preferred root domain"),
    Memo("备注", "Memo"),
    CurrentAccount("当前账号", "Current account"),
    SwitchTo("切换到这个账号", "Switch to this account"),
    DeleteAccountConfirm("确定要删掉「%1\$s」吗？只是从本机删掉，服务器上的域名不受影响喵。", "Delete \"%1\$s\" from this device?"),
    AccountSaved("账号已保存喵~", "Account saved"),
    AccountDeleted("账号已删除", "Account deleted"),
    AccountKeyBroken("这个账号的密钥解密失败了（可能系统重置过锁屏密钥），重新填一次就好", "Stored secret can't be decrypted. Please re-enter it."),
    ApiKeyRequired("API Key 和 API Secret 都要填哦", "API Key and API Secret are required"),
    TestConnection("测试连接", "Test connection"),
    ConnectionOk("连接成功喵！配额剩余 %1\$d", "Connected! Quota left: %1\$d"),
    ConnectionFailed("连接失败：%1\$s", "Connection failed: %1\$s"),

    /* 剪贴板 */
    PasteFromClipboard("从剪贴板识别", "Detect from clipboard"),
    ReadClipboard("读取剪贴板", "Read clipboard"),
    ClipboardNoKey("剪贴板里没找到 cfsd_ 开头的密钥喵 QAQ", "No cfsd_ key found in clipboard"),
    ClipboardFound("识别到密钥：%1\$s", "Found key: %1\$s"),

    /* 备份 */
    BackupTitle("备份与恢复", "Backup & Restore"),
    ExportBackup("导出备份", "Export backup"),
    ImportBackup("导入备份", "Import backup"),
    BackupPassword("备份口令", "Backup password"),
    BackupPasswordHint("留空 = 明文导出（不太安全但方便）", "Empty = plain JSON"),
    ExportDone("备份已导出喵~", "Backup exported"),
    ExportFailed("导出失败：%1\$s", "Export failed: %1\$s"),
    ImportDone("导入完成：新增 %1\$d，更新 %2\$d，失败 %3\$d", "Imported: +%1\$d, updated %2\$d, failed %3\$d"),
    ImportFailed("导入失败：%1\$s", "Import failed: %1\$s"),
    ImportNeedPassword("这个备份有口令保护，请输入备份口令", "This backup is password-protected"),
    ImportNeedPasswordShort("需要备份口令", "Password required"),

    /* 注册引导 */
    NoAccountHint("还没有账号？先去官网注册一个，回来再用 API 密钥登录喵~", "No account? Register on the website first."),
    OpenRegisterPage("打开注册页面", "Open register page"),
    OpenOfficialSite("打开官网", "Open website"),

    /* 域名 */
    DomainsTitle("我的域名", "My domains"),
    DomainsEmpty("还没有域名，点右下角 + 注册一个喵~", "No domains yet. Tap + to register one."),
    DomainsEmptyNoAccount("先去「账号」页添加一个账号吧喵", "Add an account first."),
    GoAddAccount("去添加账号", "Add an account"),
    SearchDomainHint("搜索子域名或根域名", "Search subdomain or root"),
    SortBy("排序", "Sort by"),
    SortDir("方向", "Direction"),
    SortCreated("创建时间", "Created"),
    SortExpires("到期时间", "Expires"),
    SortName("子域名", "Subdomain"),
    Asc("升序", "Asc"),
    Desc("降序", "Desc"),
    StatTotal("共 %1\$d 个", "%1\$d total"),
    LoadMore("加载更多", "Load more"),
    NoMoreData("没有更多了喵", "No more data"),
    Refreshed("已刷新喵~", "Refreshed"),

    RegisterDomain("注册子域名", "Register subdomain"),
    RegisterDomainTitle("注册新子域名", "Register a subdomain"),
    SubdomainPrefix("子域名前缀", "Subdomain"),
    SubdomainPrefixHint("例如 myapp", "e.g. myapp"),
    RootDomain("根域名", "Root domain"),
    RootDomainHint("例如 example.com", "e.g. example.com"),
    RootDomainRequired("根域名不能为空哦", "Root domain is required"),
    SubdomainRequired("子域名前缀不能为空哦", "Subdomain is required"),
    RegisterSuccess("注册成功：%1\$s", "Registered: %1\$s"),
    UseDefaultRoot("用常用根域名：%1\$s", "Use preferred root: %1\$s"),

    /* 根域名选择 & 外部域名标注 */
    ChooseRootDomain("点这里选择根域名", "Tap to choose a root domain"),
    RootDomainListTitle("选择根域名", "Choose a root domain"),
    RootDomainOfficialTag("官方", "Official"),
    RootDomainExternalTag("外部", "External"),
    RootDomainExternalFull("外部域名", "External domain"),
    RootDomainManualInput("手动输入根域名", "Enter root domain manually"),
    RootDomainManualHint("官网新上线、清单里还没有的根域名可以在这里填", "For root domains not in the list yet"),
    RootDomainGroupOfficial("官方根域名", "Official root domains"),
    RootDomainGroupExternal("外部根域名（第三方提供）", "External root domains (third-party)"),
    ExternalWarnTitle("你选了外部域名", "External domain selected"),
    ExternalWarnBody("「%1\$s」是第三方提供的根域名，不是 DNSHE 官方运营的。它的稳定性、可用性、安全性和续期政策都无法保证，随时可能失效或停止服务，建议不要用在重要业务上喵。", "\"%1\$s\" is provided by a third party, not operated by DNSHE. Its stability, availability, security and renewal policy are not guaranteed — it may stop working at any time. Do not use it for anything important."),
    ExternalWarnShort("外部域名，稳定性与安全性由第三方提供，请谨慎使用", "Third-party domain — use with caution"),
    UnknownRootWarnTitle("这个根域名不在已知清单里", "Unknown root domain"),
    UnknownRootWarnBody("「%1\$s」不在当前内置的免费根域名清单里，可能是新上线的，也可能已经下线或改名了，注册前先确认一下喵。", "\"%1\$s\" is not in the built-in list. It may be new, or it may be gone. Please double-check before registering."),
    UnknownRootTag("未知", "Unknown"),
    FullDomainPreview("将注册的完整域名", "Domain to register"),
    RootDomainNote("清单来自官网注册页，带「外部」标记的是第三方提供的根域名", "List mirrors the official register page; items marked \"External\" are third-party"),

    DomainStatus("状态", "Status"),
    StatusActive("正常", "Registered"),
    StatusSuspended("已暂停", "Suspended"),
    StatusExpired("已过期", "Expired"),
    ExpiresAt("到期时间", "Expires at"),
    CreatedAt("创建时间", "Created at"),
    UpdatedAt("更新时间", "Updated at"),
    RemainingDays("剩余 %1\$d 天", "%1\$d days left"),

    Renew("续期", "Renew"),
    RenewConfirm("要给「%1\$s」续期吗？可能会扣除账户余额。", "Renew \"%1\$s\"? Balance may be charged."),
    RenewSuccess("续期成功喵~ 新到期时间：%1\$s", "Renewed! New expiry: %1\$s"),
    RenewCharged("扣费 %1\$s", "Charged %1\$s"),

    DeleteDomain("删除域名", "Delete domain"),
    DeleteDomainConfirm("要删掉「%1\$s」吗？它下面的 DNS 记录也会一起消失，而且不可恢复喵 QAQ", "Delete \"%1\$s\" and all its DNS records? This cannot be undone."),
    DomainDeleted("已删除：%1\$s", "Deleted: %1\$s"),

    DomainDetailTitle("域名详情", "Domain detail"),
    DnsRecords("DNS 记录", "DNS records"),
    DnsRecordCount("共 %1\$d 条记录", "%1\$d record(s)"),

    /* DNS 记录 */
    RecordsEmpty("这个域名下还没有 DNS 记录喵", "No DNS records yet"),
    AddRecord("添加记录", "Add record"),
    EditRecord("编辑记录", "Edit record"),
    RecordType("类型", "Type"),
    RecordName("主机记录", "Name"),
    RecordNameHint("@ 或留空 = 子域名本身", "@ or empty = the subdomain itself"),
    RecordContent("记录值", "Value"),
    RecordContentHint("例如 1.2.3.4", "e.g. 1.2.3.4"),
    RecordTtl("TTL", "TTL"),
    RecordPriority("优先级", "Priority"),
    RecordLine("解析线路", "Line"),
    RecordWeight("权重", "Weight"),
    RecordPort("端口", "Port"),
    RecordTarget("目标主机", "Target"),
    CaaFlag("CAA Flag", "CAA flag"),
    CaaTag("CAA Tag", "CAA tag"),
    CaaValue("CAA 值", "CAA value"),
    SrvExtra("SRV 专属参数", "SRV options"),
    CaaExtra("CAA 专属参数", "CAA options"),
    RecordSaved("记录已保存喵~", "Record saved"),
    RecordDeleted("记录已删除", "Record deleted"),
    DeleteRecordConfirm("要删掉这条 %1\$s 记录吗？", "Delete this %1\$s record?"),
    ContentRequired("记录值不能为空哦", "Value is required"),
    NameHintAdvanced("留空代表当前子域名自己", "Empty means the subdomain itself"),
    CustomName("自定义主机记录", "Custom name"),

    /* 工具 */
    ToolsTitle("工具箱", "Tools"),
    ToolQuotaDesc("看看还能注册多少个域名", "How many domains you can still register"),
    ToolKeysDesc("管理 API 密钥，创建/重置/删除", "Manage API keys"),
    ToolWhoisDesc("查任意域名的注册状态", "Look up any domain's status"),
    ToolUpgradeDesc("永久升级中心，申请与邀请码助力", "Permanent upgrade center"),
    NoAccountForTool("这个功能需要先选一个账号喵", "Select an account first"),

    QuotaTitle("配额查询", "Quota"),
    QuotaAvailable("可用", "Available"),
    QuotaUsed("已使用", "Used"),
    QuotaBase("基础额度", "Base"),
    QuotaBonus("邀请奖励", "Invite bonus"),
    QuotaTotal("总额度", "Total"),

    KeysTitle("API 密钥管理", "API keys"),
    KeysEmpty("还没有创建任何 API 密钥", "No API keys"),
    CreateKey("创建密钥", "Create key"),
    KeyName("密钥名称", "Key name"),
    KeyNameHint("例如：手机端", "e.g. phone"),
    IpWhitelist("IP 白名单", "IP whitelist"),
    IpWhitelistHint("逗号/换行分隔，支持单个 IP 或 CIDR", "Comma separated IP or CIDR"),
    KeyCreatedTitle("密钥创建成功喵！", "Key created!"),
    KeyCreatedWarning("API Secret 只会显示这一次，赶紧复制保存！", "The API Secret is shown only once. Save it now!"),
    KeyRegeneratedTitle("密钥已重置喵！", "Key regenerated!"),
    SecretRegenerate("重置 Secret", "Regenerate"),
    RegenerateConfirm("要给「%1\$s」重置 Secret 吗？旧的会立刻失效！", "Regenerate the secret for \"%1\$s\"? The old one dies immediately."),
    DeleteKeyConfirm("要删掉密钥「%1\$s」吗？用它写的程序会立刻失效喵。", "Delete key \"%1\$s\"?"),
    RequestCount("调用次数", "Requests"),
    LastUsed("最近使用", "Last used"),
    KeyStatus("状态", "Status"),
    SaveKeyAsAccount("存为新账号", "Save as account"),

    WhoisTitle("WHOIS 查询", "WHOIS"),
    WhoisDomainHint("例如 foo.example.com", "e.g. foo.example.com"),
    WhoisRegistered("已注册", "Registered"),
    WhoisUnregistered("未注册", "Not registered"),
    WhoisRegistrant("注册邮箱", "Registrant email"),
    WhoisNameservers("Name servers", "Name servers"),
    WhoisRateLimit("本次查询剩余 %1\$d / %2\$d", "Rate limit %1\$d / %2\$d"),
    WhoisPublicMode("不带密钥（部分服务器会拒绝）", "Without API key (may be rejected)"),
    WhoisUseAccount("带上当前账号的密钥", "Use current account's key"),
    WhoisNeedKey("这个服务器要求 WHOIS 也要带密钥，先添加一个账号喵", "This server requires credentials for WHOIS. Add an account first."),

    UpgradeTitle("永久升级中心", "Permanent upgrade"),
    UpgradeRequests("我的申请", "My requests"),
    UpgradeLogs("助力记录", "Assist logs"),
    UpgradeEligible("可升级的域名", "Eligible domains"),
    UpgradeFeed("最近成功升级", "Recent upgrades"),
    UpgradeApplyTitle("提交升级申请", "Submit request"),
    UpgradeAssistTitle("用邀请码助力", "Assist with code"),
    UpgradeAssistHint("朋友分享的邀请码，例如 ABCD1234", "Invitation code, e.g. ABCD1234"),
    UpgradeSubdomainId("域名 ID", "Subdomain ID"),
    UpgradeRequestId("申请 ID", "Request ID"),
    UpgradeCancel("取消申请", "Cancel request"),
    UpgradeCancelConfirm("要取消申请 ID %1\$d 吗？", "Cancel request #%1\$d?"),
    UpgradeAssistDone("助力成功，谢谢喵~", "Assisted, thank you!"),
    UpgradeSubmitted("申请已提交", "Request submitted"),
    UpgradeCancelled("申请已取消", "Request cancelled"),
    UpgradeEmpty("暂无数据", "No data"),
    UpgradeAssistQuota("助力进度 %1\$d / %2\$d", "Assist progress %1\$d / %2\$d"),
    UpgradeHelperQuota("你帮别人助力 %1\$d / %2\$d 次", "You assisted %1\$d / %2\$d"),
    UpgradePending("待处理 %1\$d · 已升级 %2\$d", "Pending %1\$d · Upgraded %2\$d"),

    /* 设置 */
    SettingsTitle("设置", "Settings"),
    SettingsAppearance("外观", "Appearance"),
    Theme("主题", "Theme"),
    ThemeSystem("跟随系统", "Follow system"),
    ThemeLight("浅色", "Light"),
    ThemeDark("深色", "Dark"),
    DynamicColor("动态取色", "Dynamic color"),
    DynamicColorDesc("Android 12+ 用壁纸颜色配色", "Use wallpaper colors on Android 12+"),

    SettingsLanguage("语言", "Language"),
    Language("界面语言", "Language"),
    LangSystem("跟随系统", "Follow system"),
    LangZh("简体中文", "简体中文"),
    LangEn("English", "English"),

    SettingsNetwork("网络", "Network"),
    BaseUrl("API 地址", "API endpoint"),
    BaseUrlHint("https://api005.dnshe.com/index.php", "https://api005.dnshe.com/index.php"),
    Timeout("超时时间", "Timeout"),
    TimeoutFmt("%1\$d 秒", "%1\$d s"),
    PerPage("每页条数", "Items per page"),
    PerPageFmt("%1\$d 条", "%1\$d"),
    AutoRefresh("打开时自动刷新", "Auto refresh on open"),
    AutoRefreshDesc("进入域名页时自动拉一次列表", "Refresh the domain list when opening"),

    SettingsDefaults("默认值", "Defaults"),
    DefaultTtl("默认 TTL", "Default TTL"),

    SettingsSecurity("安全", "Security"),
    ClipboardDetect("剪贴板自动识别", "Clipboard detection"),
    ClipboardDetectDesc("从剪贴板里自动找出 cfsd_… 开头的密钥和 Secret", "Reads the clipboard and picks out cfsd_… keys"),
    KeystoreNote("API 密钥用 Android Keystore 加密存在本机，换设备请用备份导出喵", "Secrets are encrypted with Android Keystore. Use backup to move devices."),

    SettingsData("数据", "Data"),
    Logs("请求日志", "Request logs"),
    LogsDesc("最近 300 条 API 调用记录（不含密钥）", "Last 300 API calls (no secrets)"),
    LogsEmpty("还没有日志喵", "No logs yet"),
    ClearLogs("清空日志", "Clear logs"),
    ClearLogsConfirm("要清空全部请求日志吗？", "Clear all request logs?"),
    ClearAll("清空所有数据", "Erase all data"),
    ClearAllConfirm("会删掉本机保存的全部账号、设置和日志（服务器上的域名不受影响）。确定吗？", "Delete all local accounts, settings and logs?"),
    AllCleared("已经全部清空喵", "Everything cleared"),

    SettingsAbout("关于", "About"),
    AboutTitle("关于雫的域名盒", "About"),
    AboutDesc("给 DNSHE 免费域名做的小小管理盒子，多账号切换、DNS 记录随手改。用 Kotlin + Jetpack Compose 手搓，喵~", "A tiny multi-account manager for DNSHE free domains, built with Kotlin + Jetpack Compose."),
    Version("版本", "Version"),
    PackageName("包名", "Package"),
    BasedOn("基于 DNSHE Free Domain API V2.0", "Based on DNSHE Free Domain API V2.0"),
    ResetDefaults("恢复默认设置", "Reset settings"),
    ResetDefaultsConfirm("把设置恢复成默认值吗？账号数据不受影响。", "Reset settings to defaults?"),
    ResetDone("设置已恢复默认喵", "Settings reset"),

    /* 危险操作二次确认通用 */
    ConfirmDeleteTitle("确认删除", "Confirm delete"),
    AreYouSure("真的要这样做吗喵？", "Are you sure?"),

    /* ============================ 邮箱 ============================ */
    MailTitle("邮箱", "Mail"),
    MailInbox("收件箱", "Inbox"),
    MailSent("已发送", "Sent"),
    MailCompose("写邮件", "Compose"),
    MailAccounts("邮箱账号", "Mail accounts"),
    MailAccountsDesc("mail.v.cd 的邮箱，独立于域名账号", "Mailboxes on mail.v.cd, separate from domain accounts"),
    MailAddAccount("添加邮箱", "Add mailbox"),
    MailEditAccount("编辑邮箱", "Edit mailbox"),
    MailAddress("邮箱地址", "Address"),
    MailAddressHint("shizuku@v.cd", "shizuku@v.cd"),
    MailToken("API Token", "API token"),
    MailTokenHint("lq_ 开头的一长串", "Starts with lq_"),
    MailMailboxId("邮箱 ID", "Mailbox ID"),
    MailMailboxIdHint("mbx_ 开头，留空则自动探测", "Starts with mbx_, auto-detected if empty"),
    MailAutoDetect("自动探测", "Auto-detect"),
    MailEmpty("这里还没有邮件喵", "No mail here yet"),
    MailLoadFailed("邮件加载失败", "Failed to load mail"),
    MailTo("收件人", "To"),
    MailToHint("多个收件人用逗号或换行分开", "Separate multiple recipients with commas or newlines"),
    MailCc("抄送", "Cc"),
    MailBcc("密送", "Bcc"),
    MailFrom("发件人", "From"),
    MailFromName("发件人名称", "Sender name"),
    MailSubject("主题", "Subject"),
    MailBody("正文", "Body"),
    MailBodyHtml("HTML 正文（可选，留空则用纯文本）", "HTML body (optional)"),
    MailSend("发送", "Send"),
    MailSending("正在发送…", "Sending…"),
    MailSentOk("已投递喵~", "Delivered!"),
    MailQueued("已进入队列，正在投递…", "Queued for delivery…"),
    MailSendFailed("发送失败", "Send failed"),
    MailRateLimited("发得太快啦，等 %1\$d 秒再试喵", "Rate limited, retry in %1\$d s"),
    MailRateHint("每分钟最多 5 封", "Max 5 per minute"),
    MailNoRecipient("至少要填一个收件人喵", "At least one recipient is required"),
    MailRecipientsFmt("%1\$d 位收件人", "%1\$d recipients"),
    MailDetail("邮件详情", "Message"),
    MailFromLabel("发件人", "From"),
    MailToLabel("收件人", "To"),
    MailTime("时间", "Time"),
    MailSize("大小", "Size"),
    MailAttachments("附件", "Attachments"),
    MailAttachmentCount("附件 %1\$d 个", "%1\$d attachments"),
    MailDownload("下载", "Download"),
    MailSaved("已保存到下载目录", "Saved to Downloads"),
    MailAuth("验签", "Authentication"),
    MailAuthNone("无验签信息", "No authentication info"),
    MailSpf("SPF", "SPF"),
    MailDkim("DKIM", "DKIM"),
    MailDmarc("DMARC", "DMARC"),
    MailStatusQueued("排队中", "Queued"),
    MailStatusSending("投递中", "Sending"),
    MailStatusDelivered("已送达", "Delivered"),
    MailStatusFailed("投递失败", "Failed"),
    MailStatusRelayed("已中继", "Relayed"),
    MailUnread("未读", "Unread"),
    MailHasAttachment("含附件", "Has attachment"),
    MailSearchHint("搜索主题或发件人", "Search subject or sender"),
    MailFolderAll("全部", "All"),
    MailNoAccount("还没有邮箱账号，先去添加一个喵", "No mailbox yet, add one first"),
    MailGoAdd("去添加", "Add now"),
    MailDeleteAccount("删除这个邮箱？", "Delete this mailbox?"),
    MailDeleteAccountDesc("只删本机保存的 token，邮箱本身不受影响", "Only removes the local token; the mailbox itself is untouched"),
    MailVerifyOk("Token 有效喵~", "Token is valid"),
    MailVerifyFail("Token 无效或已过期", "Token is invalid or expired"),
    MailQuotaFmt("本分钟还能发 %1\$d 封", "%1\$d sends left this minute"),

    /* 更新日志 */
    ReleaseNotesTitle("更新日志", "Release notes"),
    ReleaseNotesDesc("当前 v%1\$s · 看看有什么新东西", "v%1\$s · see what's new"),
    ReleaseNotesHistory("历史版本", "History"),
    ReleaseNotesMajor("重要更新", "Major update"),

    /* 邮件补充 */
    MailCurrent("当前", "Current"),
    MailNoSubject("(无主题)", "(no subject)"),
    MailEmptyBody("(空)", "(empty)"),
    MailNameOptional("备注名（可选）", "Name (optional)"),
    MailExpandAdvanced("展开抄送 / HTML", "Show Cc / HTML"),
    MailCollapseAdvanced("收起抄送 / HTML", "Hide Cc / HTML"),
    MailTokenRequired("Token 不能为空", "Token is required"),
    MailTokenInvalid("Token 格式不对，应该以 lq_ 开头", "Invalid token, should start with lq_"),
    MailPickMailbox("选择邮箱", "Pick a mailbox"),
    MailPickMailboxHint("这个令牌下有 %1\$d 个邮箱，选一个添加", "This token has %1\$d mailboxes, pick one"),
    MailNoMailboxOnToken("这个令牌下没有可用邮箱", "No usable mailbox under this token"),
    MailAddAttachment("添加附件", "Attach file"),
    MailAttaching("读取中…", "Reading…"),
    MailAttachedCount("已选 %1\$d 个", "%1\$d attached"),
    MailAttachFailed("附件读取失败（单文件上限 8 MB）", "Could not read the file (max 8 MB each)"),
    ApiDocs("API 文档", "API docs"),

    /* 检查更新 */
    UpdateCheckTitle("检查更新", "Check for updates"),
    UpdateChecking("正在检查…", "Checking…"),
    UpdateUpToDate("已经是最新版本了喵", "You are up to date"),
    UpdateCheckFailed("检查失败", "Check failed"),
    UpdateFound("发现新版本 v%1\$s", "Version v%1\$s available"),
    UpdateDownloadInstall("下载并安装", "Download & install"),
    UpdateBrowserDownload("用浏览器下载", "Download in browser"),
    UpdateDownloading("下载中…", "Downloading…"),
    UpdateDownloadFailed("下载失败，试试浏览器下载", "Download failed, try the browser"),
    UpdateInstallFailed("无法调起安装器", "Could not open the installer"),
    UpdateNeedPermission("需要允许「安装未知来源应用」", "Allow installing unknown apps first"),
    UpdateAvailableBadge("有新版本", "Update available"),
}

fun Str.of(lang: Lang): String = when (lang) {
    Lang.EN -> en
    else -> zh
}

/**
 * 当前语言。用全局变量而不是完全依赖 CompositionLocal，
 * 因为很多文案是在 onClick / 协程这种非 composable 回调里拼出来的。
 * ProvideStrings 会在组合时刷新它，并用 key(lang) 让整棵树重建一次。
 */
object LangState {
    @Volatile
    var current: Lang = Lang.ZH
}

val LocalLang = staticCompositionLocalOf { Lang.ZH }

@Composable
fun ProvideStrings(lang: Lang, content: @Composable () -> Unit) {
    LangState.current = lang
    CompositionLocalProvider(LocalLang provides lang) {
        key(lang) { content() }
    }
}

fun t(key: Str, vararg args: Any): String {
    val s = key.of(LangState.current)
    return if (args.isEmpty()) s else runCatching { String.format(s, *args) }.getOrDefault(s)
}

/** 给需要延迟格式化（比如放进 lambda 里）的地方用 */
fun Str.fmt(lang: Lang, vararg args: Any): String {
    val s = of(lang)
    return if (args.isEmpty()) s else runCatching { String.format(s, *args) }.getOrDefault(s)
}
