package io.nekoshizuku.dnshe.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * mail.v.cd 开放 API 的数据模型。
 *
 * 这套模型来自逆向分析（官方尚无文档），字段全部实测验证过。
 * 详见仓库根目录的 `mail-v-cd-API-逆向文档.md`。
 */

/* ============================ 邮箱 ============================ */

/**
 * 一个邮箱账号。
 *
 * 注意：token 是**账号级**的，一个 token 绑定一个邮箱，
 * 所以这里把 token 和 mailboxId 放在一起。
 */
@Serializable
data class MailAccount(
    val id: String = "",
    val name: String = "",
    /** API token，形如 lq_xxxxx */
    val token: String = "",
    /** 邮箱地址，如 shizuku@v.cd */
    val address: String = "",
    /** 邮箱 ID，形如 mbx_xxxxx */
    val mailboxId: String = "",
    /** 备注 */
    val memo: String = "",
    val createdAt: Long = 0L,
)

/* ============================ 邮件 ============================ */

/** 邮件列表的响应包装 */
@Serializable
data class MailListResponse(
    val items: List<MailMessage> = emptyList(),
    val nextCursor: String = "",
)

/** 附件元信息（发信时也用这个结构引用） */
@Serializable
data class MailAttachment(
    val id: String = "",
    val messageId: String = "",
    val filename: String = "",
    val contentType: String = "",
    val sizeBytes: Long = 0L,
    val createdAt: String = "",
)

/** 一封邮件（收件箱里的完整对象） */
@Serializable
data class MailMessage(
    val id: String = "",
    val mailboxId: String = "",
    val folderId: String = "",
    /** Inbox / Sent / Drafts / Trash / Junk / Spam / Archive / Outbox */
    val folder: String = "",
    val messageUid: String = "",
    val imapUid: Int = 0,
    val imapModseq: Int = 0,
    val messageId: String = "",
    val threadId: String = "",
    val subject: String = "",
    val from: String = "",
    val fromName: String = "",
    val to: List<String> = emptyList(),
    val cc: List<String>? = null,
    val sentAt: String = "",
    val receivedAt: String = "",
    /** 摘要，适合列表页 */
    val snippet: String = "",
    /** 纯文本正文（列表接口也会内联返回） */
    val bodyText: String = "",
    /** HTML 正文 */
    val bodyHtml: String = "",
    val isRead: Boolean = false,
    val isStarred: Boolean = false,
    val hasAttachments: Boolean = false,
    val sizeBytes: Long = 0L,
    val attachments: List<MailAttachment> = emptyList(),
    val authentication: MailAuth = MailAuth(),
    val sendQueueId: String? = null,
    val sendQueueStatus: String? = null,
) {
    /** 发件人显示名，优先 fromName */
    val displayFrom: String
        get() = if (fromName.isNotBlank() && fromName != from) "$fromName <$from>" else from

    /** 是不是自己发出的 */
    val isSent: Boolean get() = folder.equals("Sent", ignoreCase = true)
}

/** SPF / DKIM / DMARC 验签结果 */
@Serializable
data class MailAuth(
    val authenticationResults: String = "",
    val receivedSpf: String = "",
    val spf: String = "",
    val dkim: String = "",
    val dmarc: String = "",
) {
    val hasAnyResult: Boolean
        get() = spf.isNotBlank() && spf != "unknown" ||
            dkim.isNotBlank() && dkim != "unknown" ||
            dmarc.isNotBlank() && dmarc != "unknown"
}

/* ============================ 发信 ============================ */

/**
 * 发信请求（按官方文档字段）。
 *
 * 官方字段：mailboxId（必填）/ to / cc / bcc / subject / text / html / attachments
 * 收件人合计 1~100 个。
 */
@Serializable
data class MailCompose(
    /** 发件邮箱 ID，来自 GET /me/mailboxes */
    val mailboxId: String = "",
    val to: List<String>,
    val cc: List<String> = emptyList(),
    val bcc: List<String> = emptyList(),
    val subject: String = "",
    val text: String = "",
    val html: String = "",
    val attachments: List<MailAttachmentUpload> = emptyList(),
) {
    val isValid: Boolean get() = to.any { it.contains("@") }

    /** 收件人总数，官方上限 100 */
    val recipientCount: Int get() = to.size + cc.size + bcc.size
}

/**
 * 发信时内联上传附件。
 *
 * 官方文档：attachments[] 最多 20 项，每项 filename + contentBase64（+ 可选 contentType）。
 */
@Serializable
data class MailAttachmentUpload(
    val filename: String,
    val contentBase64: String,
    val contentType: String = "application/octet-stream",
)

/** 发信成功的响应 */
@Serializable
data class MailSendResult(
    val id: String = "",
    val queueId: String = "",
    /** queued / relayed / ... */
    val status: String = "",
    /** queued / sending / delivered / failed */
    val queueStatus: String = "",
    val messageId: String = "",
    val rfcMessageId: String = "",
    val mailboxId: String = "",
    val mailboxAddress: String = "",
    val subject: String = "",
    val recipients: List<String> = emptyList(),
    /** 投递失败最多重试几次（实测为 5） */
    val maxAttempts: Int = 0,
    val nextAttemptAt: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
) {
    val isDelivered: Boolean get() = queueStatus.equals("delivered", true)
    val isFailed: Boolean get() = queueStatus.equals("failed", true)
}

/* ============================ 发信队列 ============================ */

/** `GET /send` 的响应 */
@Serializable
data class MailQueueResponse(
    val items: List<MailSendResult> = emptyList(),
    val nextCursor: String = "",
)

/** 发信队列状态（实测只接受这三个值） */
enum class MailQueueStatus(val value: String) {
    QUEUED("queued"),
    SENDING("sending"),
    FAILED("failed");

    companion object {
        fun of(raw: String): MailQueueStatus? =
            entries.firstOrNull { it.value.equals(raw, ignoreCase = true) }
    }
}

/** 邮件文件夹（实测存在的那些） */
enum class MailFolder(val value: String) {
    INBOX("Inbox"),
    SENT("Sent"),
    DRAFTS("Drafts"),
    TRASH("Trash"),
    JUNK("Junk"),
    SPAM("Spam"),
    ARCHIVE("Archive"),
    OUTBOX("Outbox"),
}

/* ============================ 我的邮箱 ============================ */

/** `GET /me/mailboxes` 里的单个邮箱 */
@Serializable
data class MailboxInfo(
    val id: String = "",
    val domainId: String = "",
    val address: String = "",
    val displayName: String = "",
    /** 配额，单位 MB */
    val quotaMb: Int = 0,
    /** active / locked / deleted ... */
    val status: String = "",
    val createdAt: String = "",
) {
    val isActive: Boolean get() = status.equals("active", true)
}

/** `GET /me/mailboxes` 的响应 */
@Serializable
data class MailboxListResponse(
    val items: List<MailboxInfo> = emptyList(),
    val nextCursor: String = "",
)

/* ============================ 投递事件 ============================ */

/** 发送记录的一条投递事件 */
@Serializable
data class SendEvent(
    val id: String = "",
    val queueId: String = "",
    val mailboxId: String = "",
    val sentMessageId: String = "",
    val source: String = "",
    /** queued / relayed / delivered / failed / bounced ... */
    val event: String = "",
    val status: String = "",
    val mailFrom: String = "",
    val headerFrom: String = "",
    val recipients: List<String> = emptyList(),
    val createdAt: String = "",
)

/** `GET /send/{id}/events` 的响应 */
@Serializable
data class SendEventResponse(
    val auditEvents: List<SendEvent> = emptyList(),
)
