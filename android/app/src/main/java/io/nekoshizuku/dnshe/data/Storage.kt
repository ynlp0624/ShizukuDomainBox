package io.nekoshizuku.dnshe.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/* ============================ 语言 / 主题 ============================= */

enum class Lang { SYSTEM, ZH, EN }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/* ============================== 设置 ================================= */

@Serializable
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val lang: Lang = Lang.SYSTEM,
    val dynamicColor: Boolean = true,
    val baseUrl: String = DEFAULT_BASE_URL,
    val timeoutSeconds: Int = 20,
    val perPage: Int = 50,
    val autoRefresh: Boolean = true,
    val defaultRootDomain: String = "",
    val defaultTtl: Int = 600,
    val clipboardDetect: Boolean = true,
    val lastDomainId: Int? = null,
    /** 上次看到的版本号（用于判断要不要弹更新说明） */
    val lastSeenVersion: String = "",
)

class SettingsStore(private val ctx: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val file = File(ctx.filesDir, "settings.json")

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    val current: AppSettings get() = _state.value

    private fun load(): AppSettings = runCatching {
        if (!file.exists()) return@runCatching AppSettings()
        json.decodeFromString(AppSettings.serializer(), file.readText())
    }.getOrElse { AppSettings() }

    private fun persist(s: AppSettings) {
        runCatching { file.writeText(json.encodeToString(AppSettings.serializer(), s)) }
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_state.value)
        _state.value = next
        persist(next)
    }
}

/* ======================= Android Keystore 加密 ======================== */

/**
 * 用 Android Keystore 里的 AES-256-GCM 主密钥加密敏感字段。
 * 密钥永远不出 TEE/StrongBox，应用卸载即销毁。
 */
object CryptoBox {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "nekoshizuku_dnshe_master_key_v1"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128

    @Volatile private var cached: SecretKey? = null

    private fun masterKey(): SecretKey {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            val existing = (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            val key = existing ?: run {
                val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
                gen.init(
                    KeyGenParameterSpec.Builder(
                        ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .setRandomizedEncryptionRequired(true)
                        .build()
                )
                gen.generateKey()
            }
            cached = key
            return key
        }
    }

    /** 返回 "base64(iv):base64(ciphertext)"，失败返回 null */
    fun encrypt(plain: String, aad: String? = null): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        aad?.let { cipher.updateAAD(it.toByteArray()) }
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val iv = Base64.getEncoder().encodeToString(cipher.iv)
        val body = Base64.getEncoder().encodeToString(ct)
        "$iv:$body"
    }.getOrNull()

    /** 解密失败（比如密钥被系统重置）返回 null */
    fun decrypt(token: String, aad: String? = null): String? = runCatching {
        val parts = token.split(':', limit = 2)
        if (parts.size != 2) return@runCatching null
        val iv = Base64.getDecoder().decode(parts[0])
        val ct = Base64.getDecoder().decode(parts[1])
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(TAG_BITS, iv))
        aad?.let { cipher.updateAAD(it.toByteArray()) }
        String(cipher.doFinal(ct), Charsets.UTF_8)
    }.getOrNull()
}

/** 导出的备份文件用口令加密（PBKDF2-HMAC-SHA256 + AES-256-GCM） */
object BackupCrypto {

    private const val ITERATIONS = 120_000
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val TRANSFORM = "AES/GCM/NoPadding"

    @Serializable
    data class Envelope(
        val format: String = "dnshe-backup",
        val version: Int = 1,
        val kdf: String = "PBKDF2WithHmacSHA256",
        val iterations: Int = ITERATIONS,
        val salt: String = "",
        val iv: String = "",
        val data: String = "",
    )

    private fun derive(password: String, salt: ByteArray, iterations: Int): SecretKey {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(f.generateSecret(spec).encoded, "AES")
    }

    fun wrap(plainJson: String, password: String): String? = runCatching {
        val rnd = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { rnd.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { rnd.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, derive(password, salt, ITERATIONS), GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plainJson.toByteArray(Charsets.UTF_8))
        val b64 = Base64.getEncoder()
        // 必须 encodeDefaults：否则 format/version/kdf/iterations 会被省略，
        // 导致 looksEncrypted() 认不出这是加密备份（v1.1.0 就是这么坏的）
        Json { prettyPrint = true; encodeDefaults = true }.encodeToString(
            Envelope.serializer(),
            Envelope(salt = b64.encodeToString(salt), iv = b64.encodeToString(iv), data = b64.encodeToString(ct)),
        )
    }.getOrNull()

    fun unwrap(envelopeJson: String, password: String): String? = runCatching {
        val env = Json { ignoreUnknownKeys = true }.decodeFromString(Envelope.serializer(), envelopeJson)
        val b64 = Base64.getDecoder()
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(
            Cipher.DECRYPT_MODE,
            derive(password, b64.decode(env.salt), env.iterations),
            GCMParameterSpec(128, b64.decode(env.iv)),
        )
        String(cipher.doFinal(b64.decode(env.data)), Charsets.UTF_8)
    }.getOrNull()
}

/* ============================== 账号 ================================= */

@Serializable
data class Account(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val apiKey: String = "",
    val apiSecret: String = "",
    val defaultRootDomain: String = "",
    val memo: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    val creds: Creds get() = Creds(apiKey, apiSecret)
    val maskedKey: String
        get() = if (apiKey.length <= 12) apiKey else apiKey.take(10) + "…" + apiKey.takeLast(4)
}

@Serializable
private data class StoredAccount(
    val id: String = "",
    val name: String = "",
    val memo: String = "",
    @SerialName("default_rootdomain") val defaultRootDomain: String = "",
    @SerialName("created_at") val createdAt: Long = 0L,
    /** CryptoBox 加密后的 "iv:ct"，明文是 {"k":..,"s":..} */
    val secret: String = "",
)

@Serializable
private data class AccountFile(
    val version: Int = 1,
    val current: String? = null,
    val accounts: List<StoredAccount> = emptyList(),
)

@Serializable
data class PlainBackup(
    val format: String = "dnshe-accounts",
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val accounts: List<PlainAccount> = emptyList(),
)

@Serializable
data class PlainAccount(
    val name: String = "",
    val apiKey: String = "",
    val apiSecret: String = "",
    val defaultRootDomain: String = "",
    val memo: String = "",
)

data class ImportResult(val added: Int, val updated: Int, val failed: Int, val message: String?)

class AccountStore(private val ctx: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val file = File(ctx.filesDir, "accounts.json")

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private val _currentId = MutableStateFlow<String?>(null)
    val currentId: StateFlow<String?> = _currentId.asStateFlow()

    /** 解密失败的账号 id（密钥被系统重置等情况） */
    private val _brokenIds = MutableStateFlow<Set<String>>(emptySet())
    val brokenIds: StateFlow<Set<String>> = _brokenIds.asStateFlow()

    init {
        load()
    }

    private fun load() {
        val parsed = runCatching {
            if (!file.exists()) return@runCatching AccountFile()
            json.decodeFromString(AccountFile.serializer(), file.readText())
        }.getOrElse { AccountFile() }

        val broken = mutableSetOf<String>()
        val list = parsed.accounts.mapNotNull { s ->
            val plain = CryptoBox.decrypt(s.secret, s.id)
            if (plain == null) {
                broken += s.id
                Account(id = s.id, name = s.name, defaultRootDomain = s.defaultRootDomain, memo = s.memo, createdAt = s.createdAt)
            } else {
                val parts = runCatching {
                    json.decodeFromString(SecretPayload.serializer(), plain)
                }.getOrNull() ?: SecretPayload()
                Account(
                    id = s.id, name = s.name, apiKey = parts.k, apiSecret = parts.s,
                    defaultRootDomain = s.defaultRootDomain, memo = s.memo, createdAt = s.createdAt,
                )
            }
        }
        _brokenIds.value = broken
        _accounts.value = list
        _currentId.value = parsed.current?.takeIf { id -> list.any { it.id == id } } ?: list.firstOrNull()?.id
    }

    @Serializable
    private data class SecretPayload(val k: String = "", val s: String = "")

    private fun persist() {
        val stored = _accounts.value.map { a ->
            val enc = CryptoBox.encrypt(json.encodeToString(SecretPayload.serializer(), SecretPayload(a.apiKey, a.apiSecret)), a.id)
            StoredAccount(
                id = a.id, name = a.name, memo = a.memo, defaultRootDomain = a.defaultRootDomain,
                createdAt = a.createdAt, secret = enc ?: "",
            )
        }
        runCatching {
            file.writeText(json.encodeToString(AccountFile.serializer(), AccountFile(1, _currentId.value, stored)))
        }
    }

    val current: Account?
        get() = _accounts.value.firstOrNull { it.id == _currentId.value } ?: _accounts.value.firstOrNull()

    fun select(id: String) {
        _currentId.value = id
        persist()
    }

    fun add(account: Account): Account {
        val a = if (account.name.isBlank()) account.copy(name = account.apiKey.take(12)) else account
        _accounts.value = _accounts.value + a
        if (_currentId.value == null) _currentId.value = a.id
        persist()
        return a
    }

    fun update(account: Account) {
        _accounts.value = _accounts.value.map { if (it.id == account.id) account else it }
        _brokenIds.value = _brokenIds.value - account.id
        persist()
    }

    fun remove(id: String) {
        _accounts.value = _accounts.value.filterNot { it.id == id }
        if (_currentId.value == id) _currentId.value = _accounts.value.firstOrNull()?.id
        persist()
    }

    fun removeAll() {
        _accounts.value = emptyList()
        _currentId.value = null
        _brokenIds.value = emptySet()
        persist()
    }

    /** 导出明文备份 JSON（可选择再用口令包一层） */
    fun exportPlain(): String {
        val plain = PlainBackup(
            accounts = _accounts.value.map {
                PlainAccount(it.name, it.apiKey, it.apiSecret, it.defaultRootDomain, it.memo)
            },
        )
        return json.encodeToString(PlainBackup.serializer(), plain)
    }

    fun exportWithPassword(password: String?): String {
        val plain = exportPlain()
        if (password.isNullOrEmpty()) return plain
        return BackupCrypto.wrap(plain, password)
            ?: throw IllegalStateException("备份加密失败")
    }

    /** 自动识别是明文备份还是口令加密备份 */
    fun looksEncrypted(text: String): Boolean {
        val t = text.trim()
        if (!t.startsWith("{")) return false
        // 明文备份一定带 accounts 数组，加密信封一定带 data，
        // 这样就不会再把加密备份当明文解析成「0 个账号」了
        if (t.contains("\"accounts\"")) return false
        return t.contains("\"data\"") && (t.contains("\"kdf\"") || t.contains("\"salt\""))
    }

    fun importJson(text: String, password: String?): ImportResult {
        val t = text.trim()
        if (!t.startsWith("{")) return ImportResult(0, 0, 0, "不是有效的 JSON 备份文件")

        val plainJson: String = if (looksEncrypted(t)) {
            if (password.isNullOrEmpty()) return ImportResult(0, 0, 0, "这个备份有口令保护，需要输入密码")
            BackupCrypto.unwrap(t, password) ?: return ImportResult(0, 0, 0, "口令不对，或者文件损坏了")
        } else {
            t
        }

        val backup = runCatching {
            json.decodeFromString(PlainBackup.serializer(), plainJson)
        }.getOrElse { return ImportResult(0, 0, 0, "备份内容解析失败：${it.message}") }

        var added = 0
        var updated = 0
        var failed = 0
        val working = _accounts.value.toMutableList()

        backup.accounts.forEach { p ->
            if (p.apiKey.isBlank() || p.apiSecret.isBlank()) {
                failed++
                return@forEach
            }
            val existingIdx = working.indexOfFirst { it.apiKey == p.apiKey }
            if (existingIdx >= 0) {
                val old = working[existingIdx]
                working[existingIdx] = old.copy(
                    name = p.name.ifBlank { old.name },
                    apiSecret = p.apiSecret,
                    defaultRootDomain = p.defaultRootDomain.ifBlank { old.defaultRootDomain },
                    memo = p.memo.ifBlank { old.memo },
                )
                updated++
            } else {
                working += Account(
                    name = p.name.ifBlank { p.apiKey.take(12) },
                    apiKey = p.apiKey.trim(),
                    apiSecret = p.apiSecret.trim(),
                    defaultRootDomain = p.defaultRootDomain,
                    memo = p.memo,
                )
                added++
            }
        }

        _accounts.value = working
        if (_currentId.value == null) _currentId.value = working.firstOrNull()?.id
        persist()
        return ImportResult(added, updated, failed, null)
    }
}

/* ============================== 日志 ================================= */

class LogStore(private val ctx: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val file = File(ctx.filesDir, "request_logs.json")
    private val max = 300

    private val _entries = MutableStateFlow<List<RequestLogEntry>>(emptyList())
    val entries: StateFlow<List<RequestLogEntry>> = _entries.asStateFlow()

    init {
        _entries.value = runCatching {
            if (!file.exists()) return@runCatching emptyList()
            json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(RequestLogEntry.serializer()), file.readText())
        }.getOrElse { emptyList() }
    }

    @Synchronized
    fun add(entry: RequestLogEntry) {
        _entries.value = (listOf(entry) + _entries.value).take(max)
        persist()
    }

    @Synchronized
    fun clear() {
        _entries.value = emptyList()
        runCatching { file.delete() }
    }

    private fun persist() {
        runCatching {
            file.writeText(
                json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(RequestLogEntry.serializer()),
                    _entries.value,
                )
            )
        }
    }
}

/* =========================== 依赖容器 ================================ */

object AppContainer {
    lateinit var settings: SettingsStore
        private set
    lateinit var accounts: AccountStore
        private set
    lateinit var logs: LogStore
        private set
    lateinit var api: DnsheApi
        private set
    lateinit var mailboxes: MailboxStore
        private set
    lateinit var mailApi: MailApi
        private set

    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun init(ctx: Context) {
        if (::settings.isInitialized) return
        val app = ctx.applicationContext
        settings = SettingsStore(app)
        accounts = AccountStore(app)
        logs = LogStore(app)
        api = DnsheApi { entry -> logs.add(entry) }
        mailboxes = MailboxStore(app)
        mailApi = MailApi { entry -> logs.add(entry) }
    }

    fun credsOrNull(): Creds? = accounts.current?.takeIf { it.apiKey.isNotBlank() }?.creds
}
