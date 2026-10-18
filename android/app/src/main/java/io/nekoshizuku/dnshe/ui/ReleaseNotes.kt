package io.nekoshizuku.dnshe.ui

import io.nekoshizuku.dnshe.data.Lang

/**
 * 更新日志。
 *
 * 每次发版在这里加一条，App 首次启动（或版本号变化时）会弹窗展示。
 * 用纯数据而不是 res/values，方便和 PC 端共享。
 */
data class ReleaseNote(
    val version: String,
    val date: String,
    val titleZh: String,
    val titleEn: String,
    val items: List<NoteItem>,
    /** 是否为重要更新（会强调展示） */
    val major: Boolean = false,
) {
    fun title(lang: Lang) = if (lang == Lang.EN) titleEn else titleZh
}

data class NoteItem(
    val kind: Kind,
    val zh: String,
    val en: String,
) {
    enum class Kind { NEW, FIX, IMPROVE }

    fun text(lang: Lang) = if (lang == Lang.EN) en else zh

    fun badge(lang: Lang): String = when (kind) {
        Kind.NEW -> if (lang == Lang.EN) "NEW" else "新增"
        Kind.FIX -> if (lang == Lang.EN) "FIX" else "修复"
        Kind.IMPROVE -> if (lang == Lang.EN) "IMPROVE" else "优化"
    }
}

/** 当前版本的更新说明 */
val RELEASE_NOTES: List<ReleaseNote> = listOf(
    ReleaseNote(
        version = "2.3.0",
        date = "2026-10-18",
        titleZh = "对接官方 API",
        titleEn = "Official API",
        major = true,
        items = listOf(
            NoteItem(
                NoteItem.Kind.NEW,
                "自动获取邮箱 ID：填令牌就能列出名下所有邮箱，不用再手填 mailboxId",
                "Auto-detect mailbox IDs from your token — no more manual mailboxId",
            ),
            NoteItem(
                NoteItem.Kind.NEW,
                "支持添加附件发送（内联上传，最多 20 个）",
                "Send attachments (inline upload, up to 20 files)",
            ),
            NoteItem(
                NoteItem.Kind.NEW,
                "一个令牌绑多个邮箱时会弹选择框，挑一个添加",
                "Pick from your mailboxes when a token covers several",
            ),
            NoteItem(
                NoteItem.Kind.IMPROVE,
                "全面改用官方 /v1 接口与字段（mailboxId 必填、幂等键防重复发送）",
                "Switched to the official /v1 API and fields, with idempotent sends",
            ),
            NoteItem(
                NoteItem.Kind.FIX,
                "修复保存邮箱时调用会超时的旧接口",
                "Fixed the save flow calling an endpoint that times out",
            ),
        ),
    ),
    ReleaseNote(
        version = "2.2.0",
        date = "2026-10-18",
        titleZh = "应用内检查更新",
        titleEn = "In-app updates",
        major = true,
        items = listOf(
            NoteItem(
                NoteItem.Kind.NEW,
                "设置页可以手动检查更新，启动时也会静默检查一次",
                "Check for updates from Settings, plus a silent check on launch",
            ),
            NoteItem(
                NoteItem.Kind.NEW,
                "发现新版本直接弹窗显示更新内容，可一键下载安装",
                "New versions show a dialog with release notes and one-tap install",
            ),
            NoteItem(
                NoteItem.Kind.NEW,
                "同时对接 GitHub 和 GitCode，国内优先走 GitCode",
                "Queries both GitHub and GitCode, preferring GitCode in China",
            ),
            NoteItem(
                NoteItem.Kind.NEW,
                "下载有实时进度，装不了会提示去开「安装未知来源」权限",
                "Live download progress, with a hint when the install permission is missing",
            ),
            NoteItem(
                NoteItem.Kind.IMPROVE,
                "更新说明支持一键查看历史版本",
                "Release notes now have a version history view",
            ),
        ),
    ),
    ReleaseNote(
        version = "2.1.0",
        date = "2026-10-18",
        titleZh = "邮箱功能来了",
        titleEn = "Mail is here",
        major = true,
        items = listOf(
            NoteItem(
                NoteItem.Kind.NEW,
                "全新「邮箱」标签页：直接收发 mail.v.cd 的邮件",
                "New Mail tab: send and receive mail.v.cd messages",
            ),
            NoteItem(
                NoteItem.Kind.NEW,
                "支持多邮箱账号切换，token 用 Android Keystore 加密保存",
                "Multiple mailbox accounts with Keystore-encrypted tokens",
            ),
            NoteItem(
                NoteItem.Kind.NEW,
                "写邮件支持收件人 / 抄送 / 密送 / HTML 正文",
                "Compose with To / Cc / Bcc / HTML body",
            ),
            NoteItem(
                NoteItem.Kind.NEW,
                "收件箱、已发送分栏浏览，支持搜索",
                "Browse Inbox and Sent, with search",
            ),
            NoteItem(
                NoteItem.Kind.NEW,
                "邮件详情页：完整正文、附件下载、SPF/DKIM/DMARC 验签结果",
                "Message detail with body, attachment download and SPF/DKIM/DMARC results",
            ),
            NoteItem(
                NoteItem.Kind.NEW,
                "本地限流提示：每分钟最多 5 封，发送前就知道还剩几封",
                "Local rate-limit hint: max 5 per minute, shown before sending",
            ),
            NoteItem(
                NoteItem.Kind.IMPROVE,
                "首次对接 mail.v.cd 开放 API（官方还没出文档，接口是逆向出来的）",
                "First integration with the mail.v.cd open API (reverse-engineered)",
            ),
        ),
    ),
    ReleaseNote(
        version = "2.0.0",
        date = "2026-10-12",
        titleZh = "HyperOS 风格改版",
        titleEn = "HyperOS redesign",
        items = listOf(
            NoteItem(NoteItem.Kind.IMPROVE, "界面全面迁移到 Miuix（HyperOS 视觉语言）", "Full UI migration to Miuix (HyperOS)"),
            NoteItem(NoteItem.Kind.IMPROVE, "工具链升级：Gradle 9.8.1 / JDK 21 / Compose 1.12", "Toolchain: Gradle 9.8.1 / JDK 21 / Compose 1.12"),
            NoteItem(NoteItem.Kind.FIX, "修复部分页面卡片内边距丢失的问题", "Fixed missing card padding on some screens"),
        ),
    ),
    ReleaseNote(
        version = "1.1.1",
        date = "2026-10-06",
        titleZh = "稳定性改进",
        titleEn = "Stability",
        items = listOf(
            NoteItem(NoteItem.Kind.FIX, "修复备份导入时偶发的解析失败", "Fixed occasional import parse failures"),
            NoteItem(NoteItem.Kind.IMPROVE, "请求日志增加耗时统计", "Request logs now record latency"),
        ),
    ),
)

/** 当前最新版本号 */
val LATEST_VERSION: String get() = RELEASE_NOTES.firstOrNull()?.version ?: "1.0.0"

/** 按版本号找更新说明 */
fun noteFor(version: String): ReleaseNote? = RELEASE_NOTES.firstOrNull { it.version == version }
