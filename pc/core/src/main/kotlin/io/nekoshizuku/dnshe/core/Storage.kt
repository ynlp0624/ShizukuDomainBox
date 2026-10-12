package io.nekoshizuku.dnshe.core

import com.sun.jna.platform.win32.Crypt32Util
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
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
    val baseUrl: String = DEFAULT_BASE_URL,
    val timeoutSeconds: Int = 20,
    val perPage: Int = 50,
    val autoRefresh: Boolean = true,
    val defaultRootDomain: String = "",
    val defaultTtl: Int = 600,
    val windowWidth: Int = 1180,
    val windowHeight: Int = 780,
    val windowMaximized: Boolean = false,
    val lastDomainId: Long? = null,
)

/* =========================== 配置目录 ================================ */

/**
 * 数据目录：默认 %APPDATA%\ShizukuDomainBox，
 * 可用 JVM 参数 -Ddnshe.dataDir=... 覆盖（安装程序的「保留数据」选项会用到）。
 */
object AppPaths {
    val dataDir: File by lazy {
        val override = System.getProperty("dnshe.dataDir")
        val dir = if (!override.isNullOrBlank()) {
            File(override)
        } else {
            val appData = System.getenv("APPDATA")
            val base = if (!appData.isNullOrBlank()) {
                File(appData)
            } else {
                File(System.getProperty("user.home"), ".config")
            }
            File(base, "ShizukuDomainBox")
        }
        dir.mkdirs()
        dir
    }

    val accountsFile: File get() = File(dataDir, "accounts.json")
    val settingsFile: File get() = File(dataDir, "settings.json")
    val logsFile: File get() = File(dataDir, "logs.json")
}

/* ===================== Windows DPAPI 加密（PC 版 Keystore）============= */

/**
 * 用 Windows DPAPI 按「当前用户」加密。
 * 和安卓版的 Android Keystore 等价：换用户 / 换机器都解不开，不需要用户记密码。
 */
object Dpapi {

    private const val PREFIX = "dpapi:"
    private const val PLAIN = "plain:"

    val available: Boolean by lazy {
        runCatching {
            val probe = cryptProtect("probe".toByteArray())
            probe != null && cryptUnprotect(probe)?.toString(Charsets.UTF_8) == "probe"
        }.getOrDefault(false)
    }

    private fun cryptProtect(data: ByteArray): ByteArray? =
        runCatching { Crypt32Util.cryptProtectData(data) }.getOrNull()

    private fun cryptUnprotect(data: ByteArray): ByteArray? =
        runCatching { Crypt32Util.cryptUnprotectData(data) }.getOrNull()

    /** 加密成 "dpapi:base64"；DPAPI 不可用时退回 "plain:base64" 并返回 false */
    fun seal(plainText: String): Pair<String, Boolean> {
        val raw = plainText.toByteArray(Charsets.UTF_8)
        val enc = cryptProtect(raw)
        return if (enc != null) {
            PREFIX + Base64.getEncoder().encodeToString(enc) to true
        } else {
            PLAIN + Base64.getEncoder().encodeToString(raw) to false
        }
    }

    /** 解密失败（换用户/换机器/文件来自安卓版）返回 null */
    fun open(sealed: String): String? {
        if (sealed.isBlank()) return null
        return runCatching {
            when {
                sealed.startsWith(PREFIX) ->
                    cryptUnprotect(Base64.getDecoder().decode(sealed.removePrefix(PREFIX)))
                        ?.toString(Charsets.UTF_8)
                sealed.startsWith(PLAIN) ->
                    String(Base64.getDecoder().decode(sealed.removePrefix(PLAIN)), Charsets.UTF_8)
                else -> null
            }
        }.getOrNull()
    }

    fun isEncrypted(sealed: String): Boolean = sealed.startsWith(PREFIX)
}

/* ==================== 备份文件加密（与安卓版完全一致）================== */

/**
 * 备份文件的口令加密。参数、字段名、算法都和安卓版一模一样，
 * 所以两边导出的 .json 可以直接互相导入。
 */
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
        // 导致 looksEncrypted() 认不出这是加密备份（老版本就是这么坏的）
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
    val secret: String = "",
)

@Serializable
private data class AccountFile(
    val version: Int = 2,
    val current: String? = null,
    val accounts: List<StoredAccount> = emptyList(),
)

/** 与安卓版完全相同的明文备份结构 */
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

class AccountStore {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private val _currentId = MutableStateFlow<String?>(null)
    val currentId: StateFlow<String?> = _currentId.asStateFlow()

    private val _brokenIds = MutableStateFlow<Set<String>>(emptySet())
    val brokenIds: StateFlow<Set<String>> = _brokenIds.asStateFlow()

    @Serializable
    private data class SecretPayload(val k: String = "", val s: String = "")

    init {
        load()
    }

    private fun load() {
        val parsed = runCatching {
            val f = AppPaths.accountsFile
            if (!f.exists()) return@runCatching AccountFile()
            json.decodeFromString(AccountFile.serializer(), f.readText())
        }.getOrElse { AccountFile() }

        val broken = mutableSetOf<String>()
        val list = parsed.accounts.mapNotNull { s ->
            val plain = Dpapi.open(s.secret)
            if (plain == null) {
                broken += s.id
                Account(id = s.id, name = s.name, defaultRootDomain = s.defaultRootDomain, memo = s.memo, createdAt = s.createdAt)
            } else {
                val parts = runCatching { json.decodeFromString(SecretPayload.serializer(), plain) }
                    .getOrNull() ?: SecretPayload()
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

    private fun persist() {
        val stored = _accounts.value.map { a ->
            val plain = json.encodeToString(SecretPayload.serializer(), SecretPayload(a.apiKey, a.apiSecret))
            StoredAccount(
                id = a.id, name = a.name, memo = a.memo, defaultRootDomain = a.defaultRootDomain,
                createdAt = a.createdAt, secret = Dpapi.seal(plain).first,
            )
        }
        runCatching {
            AppPaths.accountsFile.writeText(
                json.encodeToString(AccountFile.serializer(), AccountFile(2, _currentId.value, stored))
            )
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

    fun exportPlain(): String = json.encodeToString(
        PlainBackup.serializer(),
        PlainBackup(
            accounts = _accounts.value.map {
                PlainAccount(it.name, it.apiKey, it.apiSecret, it.defaultRootDomain, it.memo)
            },
        ),
    )

    fun exportWithPassword(password: String?): String {
        val plain = exportPlain()
        if (password.isNullOrEmpty()) return plain
        return BackupCrypto.wrap(plain, password) ?: throw IllegalStateException("备份加密失败")
    }

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

        val plainJson = if (looksEncrypted(t)) {
            if (password.isNullOrEmpty()) return ImportResult(0, 0, 0, "这个备份有口令保护，需要输入备份口令")
            BackupCrypto.unwrap(t, password) ?: return ImportResult(0, 0, 0, "口令不对，或者文件损坏了")
        } else {
            t
        }

        val backup = runCatching { json.decodeFromString(PlainBackup.serializer(), plainJson) }
            .getOrElse { return ImportResult(0, 0, 0, "备份内容解析失败：${it.message}") }

        var added = 0
        var updated = 0
        var failed = 0
        val working = _accounts.value.toMutableList()

        backup.accounts.forEach { p ->
            if (p.apiKey.isBlank() || p.apiSecret.isBlank()) {
                failed++
                return@forEach
            }
            val idx = working.indexOfFirst { it.apiKey == p.apiKey }
            if (idx >= 0) {
                val old = working[idx]
                working[idx] = old.copy(
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

/* ============================== 设置存储 ============================= */

class SettingsStore {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    val current: AppSettings get() = _state.value

    private fun load(): AppSettings = runCatching {
        val f = AppPaths.settingsFile
        if (!f.exists()) return@runCatching AppSettings()
        json.decodeFromString(AppSettings.serializer(), f.readText())
    }.getOrElse { AppSettings() }

    private fun persist(s: AppSettings) {
        runCatching { AppPaths.settingsFile.writeText(json.encodeToString(AppSettings.serializer(), s)) }
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_state.value)
        _state.value = next
        persist(next)
    }
}

/* ============================== 日志 ================================= */

class LogStore {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val max = 300

    private val _entries = MutableStateFlow<List<RequestLogEntry>>(emptyList())
    val entries: StateFlow<List<RequestLogEntry>> = _entries.asStateFlow()

    init {
        _entries.value = runCatching {
            val f = AppPaths.logsFile
            if (!f.exists()) return@runCatching emptyList()
            json.decodeFromString(ListSerializer(RequestLogEntry.serializer()), f.readText())
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
        runCatching { AppPaths.logsFile.delete() }
    }

    private fun persist() {
        runCatching {
            AppPaths.logsFile.writeText(json.encodeToString(ListSerializer(RequestLogEntry.serializer()), _entries.value))
        }
    }
}

/* =========================== 依赖容器 ================================ */

object AppCore {
    val settings: SettingsStore by lazy { SettingsStore() }
    val accounts: AccountStore by lazy { AccountStore() }
    val logs: LogStore by lazy { LogStore() }
    val api: DnsheApi by lazy { DnsheApi { entry -> logs.add(entry) } }

    fun credsOrNull(): Creds? = accounts.current?.takeIf { it.apiKey.isNotBlank() }?.creds
}
