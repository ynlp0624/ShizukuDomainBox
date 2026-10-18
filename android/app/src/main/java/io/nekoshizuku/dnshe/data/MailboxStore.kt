package io.nekoshizuku.dnshe.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * mail.v.cd 邮箱账号的本地存储。
 *
 * 安全策略与 DNSHE 账号一致：**token 用 CryptoBox 按账号 id 加密后落盘**，
 * 明文 token 只在内存里出现。
 */
@Serializable
data class MailboxAccount(
    val id: String = UUID.randomUUID().toString(),
    /** 显示名 */
    val name: String = "",
    /** 邮箱地址 shizuku@v.cd */
    val address: String = "",
    /** 邮箱 ID mbx_xxxxx（用于拉邮件列表） */
    val mailboxId: String = "",
    /** API token，lq_xxxxx */
    val token: String = "",
    val memo: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    val maskedToken: String
        get() = if (token.length <= 14) token
        else token.take(6) + "…" + token.takeLast(4)

    /** 邮箱域名部分，如 v.cd */
    val domain: String
        get() = address.substringAfter("@", "")
}

@Serializable
private data class StoredMailbox(
    val id: String = "",
    val name: String = "",
    val address: String = "",
    @SerialName("mailbox_id") val mailboxId: String = "",
    val memo: String = "",
    @SerialName("created_at") val createdAt: Long = 0L,
    /** CryptoBox 加密后的 token */
    val secret: String = "",
)

@Serializable
private data class MailboxFile(
    val version: Int = 1,
    val current: String? = null,
    val mailboxes: List<StoredMailbox> = emptyList(),
)

class MailboxStore(private val ctx: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val file = File(ctx.filesDir, "mailboxes.json")

    private val _mailboxes = MutableStateFlow<List<MailboxAccount>>(emptyList())
    val mailboxes: StateFlow<List<MailboxAccount>> = _mailboxes.asStateFlow()

    private val _currentId = MutableStateFlow<String?>(null)
    val currentId: StateFlow<String?> = _currentId.asStateFlow()

    /** 解密失败的 id */
    private val _brokenIds = MutableStateFlow<Set<String>>(emptySet())
    val brokenIds: StateFlow<Set<String>> = _brokenIds.asStateFlow()

    val current: MailboxAccount?
        get() = _mailboxes.value.firstOrNull { it.id == _currentId.value }

    init {
        load()
    }

    private fun load() {
        val parsed = runCatching {
            if (!file.exists()) return@runCatching MailboxFile()
            json.decodeFromString(MailboxFile.serializer(), file.readText())
        }.getOrElse { MailboxFile() }

        val broken = mutableSetOf<String>()
        val list = parsed.mailboxes.map { s ->
            val plain = CryptoBox.decrypt(s.secret, s.id)
            if (plain == null) {
                broken += s.id
                MailboxAccount(
                    id = s.id, name = s.name, address = s.address,
                    mailboxId = s.mailboxId, memo = s.memo, createdAt = s.createdAt,
                )
            } else {
                MailboxAccount(
                    id = s.id, name = s.name, address = s.address,
                    mailboxId = s.mailboxId, token = plain,
                    memo = s.memo, createdAt = s.createdAt,
                )
            }
        }
        _brokenIds.value = broken
        _mailboxes.value = list
        _currentId.value = parsed.current?.takeIf { id -> list.any { it.id == id } }
            ?: list.firstOrNull()?.id
    }

    private fun persist() {
        _mailboxes.value.forEach { m ->
        }
        val stored = _mailboxes.value.map { m ->
            StoredMailbox(
                id = m.id,
                name = m.name,
                address = m.address,
                mailboxId = m.mailboxId,
                memo = m.memo,
                createdAt = m.createdAt,
                secret = CryptoBox.encrypt(m.token, m.id) ?: "",
            )
        }
        val data = MailboxFile(
            version = 1,
            current = _currentId.value,
            mailboxes = stored,
        )
        runCatching { file.writeText(json.encodeToString(MailboxFile.serializer(), data)) }
    }

    /* ------------------------- 增删改 ------------------------- */

    fun add(mailbox: MailboxAccount): MailboxAccount {
        val withId = if (mailbox.id.isBlank()) mailbox.copy(id = UUID.randomUUID().toString()) else mailbox
        _mailboxes.value = _mailboxes.value + withId
        if (_currentId.value == null) _currentId.value = withId.id
        persist()
        return withId
    }

    fun update(mailbox: MailboxAccount) {
        _mailboxes.value = _mailboxes.value.map { if (it.id == mailbox.id) mailbox else it }
        persist()
    }

    fun remove(id: String) {
        _mailboxes.value = _mailboxes.value.filterNot { it.id == id }
        if (_currentId.value == id) {
            _currentId.value = _mailboxes.value.firstOrNull()?.id
        }
        persist()
    }

    fun select(id: String) {
        if (_mailboxes.value.any { it.id == id }) {
            _currentId.value = id
            persist()
        }
    }

    /* ------------------------- 导入导出 ------------------------- */

    fun exportPlain(): String {
        val payload = PlainMailBackup(
            exportedAt = System.currentTimeMillis(),
            mailboxes = _mailboxes.value.map {
                PlainMailbox(
                    name = it.name, address = it.address,
                    mailboxId = it.mailboxId, token = it.token, memo = it.memo,
                )
            },
        )
        return json.encodeToString(PlainMailBackup.serializer(), payload)
    }

    fun importPlain(text: String): ImportResult {
        val parsed = runCatching {
            json.decodeFromString(PlainMailBackup.serializer(), text)
        }.getOrNull() ?: return ImportResult(0, 0, 1, "不是有效的邮箱备份文件")

        var added = 0
        var updated = 0
        var failed = 0
        parsed.mailboxes.forEach { p ->
            if (p.token.isBlank()) {
                failed++
                return@forEach
            }
            val exist = _mailboxes.value.firstOrNull {
                it.token == p.token || (it.address.isNotBlank() && it.address == p.address)
            }
            if (exist != null) {
                update(
                    exist.copy(
                        name = p.name.ifBlank { exist.name },
                        address = p.address.ifBlank { exist.address },
                        mailboxId = p.mailboxId.ifBlank { exist.mailboxId },
                        token = p.token,
                        memo = p.memo.ifBlank { exist.memo },
                    )
                )
                updated++
            } else {
                add(
                    MailboxAccount(
                        name = p.name.ifBlank { p.address.substringBefore("@") },
                        address = p.address,
                        mailboxId = p.mailboxId,
                        token = p.token,
                        memo = p.memo,
                    )
                )
                added++
            }
        }
        return ImportResult(added, updated, failed, null)
    }
}

@Serializable
data class PlainMailBackup(
    val format: String = "dnshe-mailboxes",
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val mailboxes: List<PlainMailbox> = emptyList(),
)

@Serializable
data class PlainMailbox(
    val name: String = "",
    val address: String = "",
    @SerialName("mailbox_id") val mailboxId: String = "",
    val token: String = "",
    val memo: String = "",
)
