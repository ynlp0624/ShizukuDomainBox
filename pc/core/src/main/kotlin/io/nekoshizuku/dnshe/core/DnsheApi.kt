package io.nekoshizuku.dnshe.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** 一组 API 凭据 */
data class Creds(val key: String, val secret: String)

/** 请求日志条目（不含任何密钥，安全） */
@kotlinx.serialization.Serializable
data class RequestLogEntry(
    val time: Long = 0L,
    val method: String = "",
    val endpoint: String = "",
    val action: String? = null,
    val httpStatus: Int = 0,
    val ok: Boolean = false,
    val ms: Long = 0L,
    val errorCode: String? = null,
    val message: String? = null,
)

const val DEFAULT_BASE_URL = "https://api005.dnshe.com/index.php"

/**
 * DNSHE Free Domain API V2.0 客户端。
 *
 * 所有请求形如：
 *   {base}?m=domain_hub&endpoint={endpoint}&action={action}&{params}
 * 认证走请求头 X-API-Key / X-API-Secret（文档 V2.0 已禁用 URL/Body 传参方式）。
 */
class DnsheApi(private val logger: (RequestLogEntry) -> Unit) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()

    /* ----------------------------- 内部实现 ----------------------------- */

    private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun buildUrl(base: String, endpoint: String, action: String?, params: Map<String, String>): String {
        val b = base.trim().ifBlank { DEFAULT_BASE_URL }
        val sb = StringBuilder(b)
        sb.append(if (b.contains('?')) '&' else '?')
        sb.append("m=domain_hub")
        sb.append("&endpoint=").append(encode(endpoint))
        if (action != null) sb.append("&action=").append(encode(action))
        params.forEach { (k, v) -> sb.append('&').append(encode(k)).append('=').append(encode(v)) }
        return sb.toString()
    }

    private suspend fun <T> call(
        deserializer: DeserializationStrategy<T>,
        method: String,
        endpoint: String,
        action: String? = null,
        params: Map<String, String> = emptyMap(),
        body: JsonObject? = null,
        creds: Creds? = null,
        timeoutSeconds: Int = 20,
        baseUrl: String = DEFAULT_BASE_URL,
    ): T = withContext(Dispatchers.IO) {
        val url = buildUrl(baseUrl, endpoint, action, params)
        val started = System.currentTimeMillis()

        val reqBuilder = Request.Builder().url(url)
        creds?.let {
            if (it.key.isNotBlank()) reqBuilder.header("X-API-Key", it.key.trim())
            if (it.secret.isNotBlank()) reqBuilder.header("X-API-Secret", it.secret.trim())
        }
        reqBuilder.header("Accept", "application/json")

        when (method.uppercase()) {
            "GET" -> reqBuilder.get()
            "DELETE" -> if (body != null) {
                reqBuilder.delete(json.encodeToString(JsonObject.serializer(), body).toRequestBody(jsonMedia))
            } else {
                reqBuilder.delete()
            }
            else -> reqBuilder.post(
                json.encodeToString(JsonObject.serializer(), body ?: buildJsonObject { }).toRequestBody(jsonMedia)
            )
        }

        val call = client.newCall(reqBuilder.build())
        call.timeout().timeout(timeoutSeconds.coerceIn(5, 180).toLong(), TimeUnit.SECONDS)

        var httpStatus = 0
        try {
            call.execute().use { resp ->
                httpStatus = resp.code
                val text = resp.body?.string().orEmpty()
                val ms = System.currentTimeMillis() - started

                // 先尝试解析业务错误结构
                val err = runCatching {
                    json.decodeFromString(ApiErrorBody.serializer(), text)
                }.getOrNull()

                if (err != null && err.success.not() && (err.errorCode != null || err.message != null || err.error != null)) {
                    val msg = err.message ?: err.error ?: "Unknown error"
                    logger(
                        RequestLogEntry(started, method.uppercase(), endpoint, action, httpStatus, false, ms, err.errorCode, msg)
                    )
                    throw ApiException(
                        httpStatus = httpStatus,
                        errorCode = err.errorCode,
                        message = msg,
                        detailsText = err.details?.let { d ->
                            runCatching { json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), d) }.getOrNull()
                        },
                    )
                }

                if (httpStatus !in 200..299) {
                    logger(
                        RequestLogEntry(
                            started, method.uppercase(), endpoint, action, httpStatus, false, ms,
                            "http_$httpStatus", text.take(300).ifBlank { "HTTP $httpStatus" },
                        )
                    )
                    throw ApiException(httpStatus, "http_$httpStatus", text.take(300).ifBlank { "HTTP $httpStatus" })
                }

                val parsed = try {
                    json.decodeFromString(deserializer, text)
                } catch (t: Throwable) {
                    logger(
                        RequestLogEntry(
                            started, method.uppercase(), endpoint, action, httpStatus, false, ms,
                            "parse_error", t.message ?: "JSON 解析失败",
                        )
                    )
                    throw ApiException(httpStatus, "parse_error", "响应解析失败: ${t.message}\n${text.take(400)}")
                }

                logger(RequestLogEntry(started, method.uppercase(), endpoint, action, httpStatus, true, ms, null, null))
                parsed
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: Throwable) {
            val ms = System.currentTimeMillis() - started
            logger(
                RequestLogEntry(
                    started, method.uppercase(), endpoint, action, httpStatus, false, ms,
                    "network_error", e.message ?: e::class.java.simpleName,
                )
            )
            throw ApiException(httpStatus, "network_error", e.message ?: "网络请求失败")
        }
    }

    private fun obj(vararg pairs: Pair<String, Any?>): JsonObject = buildJsonObject {
        pairs.forEach { (k, v) ->
            when (v) {
                null -> Unit
                is Int -> put(k, JsonPrimitive(v))
                is Long -> put(k, JsonPrimitive(v))
                is Double -> put(k, JsonPrimitive(v))
                is Boolean -> put(k, JsonPrimitive(v))
                is String -> if (v.isNotBlank()) put(k, JsonPrimitive(v))
                is JsonObject -> put(k, v)
            }
        }
    }

    /* --------------------------- 1. 子域名管理 --------------------------- */

    suspend fun listSubdomains(
        creds: Creds,
        baseUrl: String,
        timeoutSeconds: Int,
        page: Int? = null,
        perPage: Int? = null,
        includeTotal: Boolean = false,
        search: String? = null,
        rootdomain: String? = null,
        status: String? = null,
        createdFrom: String? = null,
        createdTo: String? = null,
        sortBy: String? = null,
        sortDir: String? = null,
    ): SubdomainListResponse = call(
        SubdomainListResponse.serializer(), "GET", "subdomains", "list",
        params = buildMap {
            page?.let { put("page", it.toString()) }
            perPage?.let { put("per_page", it.toString()) }
            if (includeTotal) put("include_total", "1")
            search?.takeIf { it.isNotBlank() }?.let { put("search", it) }
            rootdomain?.takeIf { it.isNotBlank() }?.let { put("rootdomain", it) }
            status?.takeIf { it.isNotBlank() }?.let { put("status", it) }
            createdFrom?.takeIf { it.isNotBlank() }?.let { put("created_from", it) }
            createdTo?.takeIf { it.isNotBlank() }?.let { put("created_to", it) }
            sortBy?.takeIf { it.isNotBlank() }?.let { put("sort_by", it) }
            sortDir?.takeIf { it.isNotBlank() }?.let { put("sort_dir", it) }
        },
        creds = creds, timeoutSeconds = timeoutSeconds, baseUrl = baseUrl,
    )

    suspend fun registerSubdomain(
        creds: Creds, baseUrl: String, timeoutSeconds: Int, subdomain: String, rootdomain: String,
    ): RegisterSubdomainResponse = call(
        RegisterSubdomainResponse.serializer(), "POST", "subdomains", "register",
        body = obj("subdomain" to subdomain.trim(), "rootdomain" to rootdomain.trim()),
        creds = creds, timeoutSeconds = timeoutSeconds, baseUrl = baseUrl,
    )

    suspend fun getSubdomain(
        creds: Creds, baseUrl: String, timeoutSeconds: Int, subdomainId: Long,
    ): SubdomainDetailResponse = call(
        SubdomainDetailResponse.serializer(), "GET", "subdomains", "get",
        params = mapOf("subdomain_id" to subdomainId.toString()),
        creds = creds, timeoutSeconds = timeoutSeconds, baseUrl = baseUrl,
    )

    suspend fun deleteSubdomain(
        creds: Creds, baseUrl: String, timeoutSeconds: Int, subdomainId: Long,
    ): SimpleResponse = call(
        SimpleResponse.serializer(), "POST", "subdomains", "delete",
        body = obj("subdomain_id" to subdomainId),
        creds = creds, timeoutSeconds = timeoutSeconds, baseUrl = baseUrl,
    )

    suspend fun renewSubdomain(
        creds: Creds, baseUrl: String, timeoutSeconds: Int, subdomainId: Long,
    ): RenewResponse = call(
        RenewResponse.serializer(), "POST", "subdomains", "renew",
        body = obj("subdomain_id" to subdomainId),
        creds = creds, timeoutSeconds = timeoutSeconds, baseUrl = baseUrl,
    )

    /* --------------------------- 2. DNS 记录管理 -------------------------- */

    suspend fun listDnsRecords(
        creds: Creds, baseUrl: String, timeoutSeconds: Int, subdomainId: Long,
    ): DnsRecordListResponse = call(
        DnsRecordListResponse.serializer(), "GET", "dns_records", "list",
        params = mapOf("subdomain_id" to subdomainId.toString()),
        creds = creds, timeoutSeconds = timeoutSeconds, baseUrl = baseUrl,
    )

    suspend fun createDnsRecord(
        creds: Creds, baseUrl: String, timeoutSeconds: Int,
        subdomainId: Long,
        type: String,
        name: String?,
        content: String?,
        ttl: Int?,
        priority: Int?,
        line: String?,
        weight: Int?,
        port: Int?,
        target: String?,
        caaFlag: Int?,
        caaTag: String?,
        caaValue: String?,
    ): DnsRecordMutationResponse = call(
        DnsRecordMutationResponse.serializer(), "POST", "dns_records", "create",
        body = obj(
            "subdomain_id" to subdomainId,
            "type" to type,
            "name" to name,
            "content" to content,
            "ttl" to ttl,
            "priority" to priority,
            "line" to line,
            "record_weight" to weight,
            "record_port" to port,
            "record_target" to target,
            "caa_flag" to caaFlag,
            "caa_tag" to caaTag,
            "caa_value" to caaValue,
        ),
        creds = creds, timeoutSeconds = timeoutSeconds, baseUrl = baseUrl,
    )

    suspend fun updateDnsRecord(
        creds: Creds, baseUrl: String, timeoutSeconds: Int,
        id: Long?,
        recordId: String?,
        type: String?,
        name: String?,
        content: String?,
        ttl: Int?,
        priority: Int?,
        line: String?,
        weight: Int?,
        port: Int?,
        target: String?,
        caaFlag: Int?,
        caaTag: String?,
        caaValue: String?,
    ): DnsRecordMutationResponse = call(
        DnsRecordMutationResponse.serializer(), "POST", "dns_records", "update",
        body = obj(
            "id" to id,
            "record_id" to recordId,
            "type" to type,
            "name" to name,
            "content" to content,
            "ttl" to ttl,
            "priority" to priority,
            "line" to line,
            "record_weight" to weight,
            "record_port" to port,
            "record_target" to target,
            "caa_flag" to caaFlag,
            "caa_tag" to caaTag,
            "caa_value" to caaValue,
        ),
        creds = creds, timeoutSeconds = timeoutSeconds, baseUrl = baseUrl,
    )

    suspend fun deleteDnsRecord(
        creds: Creds, baseUrl: String, timeoutSeconds: Int, id: Long?, recordId: String?,
    ): SimpleResponse = call(
        SimpleResponse.serializer(), "POST", "dns_records", "delete",
        body = obj("id" to id, "record_id" to recordId),
        creds = creds, timeoutSeconds = timeoutSeconds, baseUrl = baseUrl,
    )

    /* --------------------------- 3. API 密钥管理 -------------------------- */

    suspend fun listKeys(
        creds: Creds, baseUrl: String, timeoutSeconds: Int,
    ): ApiKeyListResponse = call(
        ApiKeyListResponse.serializer(), "GET", "keys", "list",
        creds = creds, timeoutSeconds = timeoutSeconds, baseUrl = baseUrl,
    )

    suspend fun createKey(
        creds: Creds, baseUrl: String, timeoutSeconds: Int, keyName: String, ipWhitelist: String?,
    ): ApiKeyCreatedResponse = call(
        ApiKeyCreatedResponse.serializer(), "POST", "keys", "create",
        body = obj("key_name" to keyName, "ip_whitelist" to ipWhitelist),
        creds = creds, timeoutSeconds = timeoutSeconds, baseUrl = baseUrl,
    )

    suspend fun deleteKey(
        creds: Creds, baseUrl: String, timeoutSeconds: Int, keyId: Long,
    ): SimpleResponse = call(
        SimpleResponse.serializer(), "POST", "keys", "delete",
        body = obj("key_id" to keyId),
        creds = creds, timeoutSeconds = timeoutSeconds, baseUrl = baseUrl,
    )

    suspend fun regenerateKey(
        creds: Creds, baseUrl: String, timeoutSeconds: Int, keyId: Long,
    ): ApiKeyCreatedResponse = call(
        ApiKeyCreatedResponse.serializer(), "POST", "keys", "regenerate",
        body = obj("key_id" to keyId),
        creds = creds, timeoutSeconds = timeoutSeconds, baseUrl = baseUrl,
    )

    /* ------------------------------ 4. 配额 ------------------------------ */

    suspend fun quota(
        creds: Creds, baseUrl: String, timeoutSeconds: Int,
    ): QuotaResponse = call(
        QuotaResponse.serializer(), "GET", "quota",
        creds = creds, timeoutSeconds = timeoutSeconds, baseUrl = baseUrl,
    )

    /* ----------------------------- 5. WHOIS ------------------------------ */

    /** 公开接口，creds 可为空；带上凭据时按 key 计费配额 */
    suspend fun whois(
        creds: Creds?, baseUrl: String, timeoutSeconds: Int, domain: String,
    ): WhoisResponse = call(
        WhoisResponse.serializer(), "GET", "whois",
        params = mapOf("domain" to domain.trim()),
        creds = creds, timeoutSeconds = timeoutSeconds, baseUrl = baseUrl,
    )

    /* ------------------------- 6. 永久升级中心 ---------------------------- */

    suspend fun upgradeList(
        creds: Creds, baseUrl: String, timeoutSeconds: Int, page: Int? = null, perPage: Int? = null,
    ): UpgradeListResponse = call(
        UpgradeListResponse.serializer(), "GET", "permanent_upgrade", "list",
        params = buildMap {
            page?.let { put("page", it.toString()) }
            perPage?.let { put("per_page", it.toString()) }
        },
        creds = creds, timeoutSeconds = timeoutSeconds, baseUrl = baseUrl,
    )

    suspend fun upgradeCreate(
        creds: Creds, baseUrl: String, timeoutSeconds: Int, subdomainId: Long,
    ): SimpleResponse = call(
        SimpleResponse.serializer(), "POST", "permanent_upgrade", "create",
        body = obj("subdomain_id" to subdomainId),
        creds = creds, timeoutSeconds = timeoutSeconds, baseUrl = baseUrl,
    )

    suspend fun upgradeAssist(
        creds: Creds, baseUrl: String, timeoutSeconds: Int, assistCode: String,
    ): SimpleResponse = call(
        SimpleResponse.serializer(), "POST", "permanent_upgrade", "assist",
        body = obj("assist_code" to assistCode.trim()),
        creds = creds, timeoutSeconds = timeoutSeconds, baseUrl = baseUrl,
    )

    suspend fun upgradeCancel(
        creds: Creds, baseUrl: String, timeoutSeconds: Int, requestId: Long,
    ): SimpleResponse = call(
        SimpleResponse.serializer(), "POST", "permanent_upgrade", "cancel",
        body = obj("request_id" to requestId),
        creds = creds, timeoutSeconds = timeoutSeconds, baseUrl = baseUrl,
    )
}
