package io.nekoshizuku.dnshe.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/* =========================================================================
 *  DNSHE Free Domain API V2.0 —— 数据模型
 *  Endpoint: https://api005.dnshe.com/index.php?m=domain_hub
 *  认证: X-API-Key / X-API-Secret
 * ========================================================================= */

/** 把可能是数字 0/1 也可能是 true/false 的字段统一成 Boolean */
internal fun JsonElement?.asBool(): Boolean {
    val p = this as? JsonPrimitive ?: return false
    return p.content == "1" || p.content.equals("true", ignoreCase = true)
}

internal fun JsonElement?.asTextOrNull(): String? {
    val p = this as? JsonPrimitive ?: return null
    return if (p.isString) p.content else p.content
}

@Serializable
data class Pagination(
    val page: Int? = null,
    @SerialName("per_page") val perPage: Int? = null,
    @SerialName("has_more") val hasMore: Boolean? = null,
    @SerialName("next_page") val nextPage: Int? = null,
    @SerialName("prev_page") val prevPage: Int? = null,
    val total: Int? = null,
)

/* ------------------------------ 子域名 ---------------------------------- */

@Serializable
data class Subdomain(
    val id: Long = 0L,
    val subdomain: String = "",
    val rootdomain: String = "",
    @SerialName("full_domain") val fullDomain: String = "",
    val status: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("never_expires") val neverExpires: JsonElement? = null,
    @SerialName("cloudflare_zone_id") val cloudflareZoneId: String? = null,
    @SerialName("provider_account_id") val providerAccountId: JsonElement? = null,
) {
    val neverExpiresBool: Boolean get() = neverExpires.asBool()
    val displayDomain: String get() = fullDomain.ifBlank { "$subdomain.$rootdomain" }
}

@Serializable
data class SubdomainListResponse(
    val success: Boolean = false,
    val count: Int? = null,
    val subdomains: List<Subdomain> = emptyList(),
    val pagination: Pagination? = null,
)

@Serializable
data class RegisterSubdomainResponse(
    val success: Boolean = false,
    val message: String? = null,
    @SerialName("subdomain_id") val subdomainId: Long? = null,
    @SerialName("full_domain") val fullDomain: String? = null,
)

@Serializable
data class SubdomainDetailResponse(
    val success: Boolean = false,
    val subdomain: Subdomain? = null,
    @SerialName("dns_records") val dnsRecords: List<DnsRecord> = emptyList(),
    @SerialName("dns_count") val dnsCount: Int? = null,
)

@Serializable
data class RenewResponse(
    val success: Boolean = false,
    val message: String? = null,
    @SerialName("subdomain_id") val subdomainId: Long? = null,
    val subdomain: String? = null,
    @SerialName("previous_expires_at") val previousExpiresAt: String? = null,
    @SerialName("new_expires_at") val newExpiresAt: String? = null,
    @SerialName("renewed_at") val renewedAt: String? = null,
    @SerialName("never_expires") val neverExpires: JsonElement? = null,
    val status: String? = null,
    @SerialName("remaining_days") val remainingDays: Int? = null,
    @SerialName("charged_amount") val chargedAmount: Double? = null,
)

/* ------------------------------ DNS 记录 -------------------------------- */

@Serializable
data class DnsRecord(
    val id: Long? = null,
    @SerialName("record_id") val recordId: String? = null,
    val name: String = "",
    val type: String = "",
    val content: String = "",
    val ttl: Int? = null,
    val priority: Int? = null,
    val line: String? = null,
    val proxied: Boolean? = null,
    val status: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class DnsRecordListResponse(
    val success: Boolean = false,
    val count: Int? = null,
    val records: List<DnsRecord> = emptyList(),
)

@Serializable
data class DnsRecordMutationResponse(
    val success: Boolean = false,
    val message: String? = null,
    val id: Long? = null,
    @SerialName("record_id") val recordId: String? = null,
)

/* ------------------------------ API 密钥 -------------------------------- */

@Serializable
data class ApiKeyInfo(
    val id: Long = 0L,
    @SerialName("key_name") val keyName: String = "",
    @SerialName("api_key") val apiKey: String = "",
    val status: String = "",
    @SerialName("request_count") val requestCount: Int? = null,
    @SerialName("last_used_at") val lastUsedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class ApiKeyListResponse(
    val success: Boolean = false,
    val count: Int? = null,
    val keys: List<ApiKeyInfo> = emptyList(),
)

@Serializable
data class ApiKeyCreatedResponse(
    val success: Boolean = false,
    val message: String? = null,
    @SerialName("api_key") val apiKey: String? = null,
    @SerialName("api_secret") val apiSecret: String? = null,
    val warning: String? = null,
)

/* ------------------------------- 配额 ----------------------------------- */

@Serializable
data class Quota(
    val used: Int = 0,
    val base: Int = 0,
    @SerialName("invite_bonus") val inviteBonus: Int = 0,
    val total: Int = 0,
    val available: Int = 0,
)

@Serializable
data class QuotaResponse(
    val success: Boolean = false,
    val quota: Quota? = null,
)

/* ------------------------------ WHOIS ----------------------------------- */

@Serializable
data class WhoisRateLimit(
    val limit: Int? = null,
    val remaining: Int? = null,
    @SerialName("reset_at") val resetAt: String? = null,
)

@Serializable
data class WhoisResponse(
    val success: Boolean = false,
    val domain: String? = null,
    val status: String? = null,
    val registered: Boolean? = null,
    @SerialName("registered_at") val registeredAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("registrant_email") val registrantEmail: String? = null,
    val nameservers: List<String> = emptyList(),
    @SerialName("name_servers") val nameServersAlias: List<String> = emptyList(),
    val message: String? = null,
    @SerialName("rate_limit") val rateLimit: WhoisRateLimit? = null,
) {
    val nsList: List<String> get() = if (nameservers.isNotEmpty()) nameservers else nameServersAlias
}

/* --------------------------- 永久升级中心 -------------------------------- */

/**
 * 注意：permanent_upgrade/list 的真实返回结构和官方文档写得不一样，
 * 实测 state 里是 assist_enabled / eligible_domains / requests /
 * assist_logs / recent_success_feed / pending_count ... 等一堆字段，
 * 所以这里保留成 JsonElement，界面上按 key 取值再渲染。
 */
@Serializable
data class UpgradeListResponse(
    val success: Boolean = false,
    val state: JsonElement? = null,
)

@Serializable
data class SimpleResponse(
    val success: Boolean = false,
    val message: String? = null,
    val warning: String? = null,
)

/* ------------------------------ 错误结构 -------------------------------- */

/**
 * 统一错误结构:
 * {
 *   "success": false,
 *   "error_code": "auth_invalid_credentials",
 *   "message": "Invalid API key",
 *   "details": { ... },
 *   "error": "Invalid API key"
 * }
 */
@Serializable
data class ApiErrorBody(
    val success: Boolean = false,
    @SerialName("error_code") val errorCode: String? = null,
    val message: String? = null,
    val details: JsonElement? = null,
    val error: String? = null,
)

class ApiException(
    val httpStatus: Int,
    val errorCode: String?,
    override val message: String,
    val detailsText: String? = null,
) : Exception(message)

fun apiErrorMessage(lang: Lang, e: Throwable): String = when (e) {
    is ApiException -> e.errorCode?.let { code ->
        val friendly = when (code) {
            "auth_invalid_credentials" -> if (lang == Lang.ZH) "API Key 或 Secret 无效，检查一下账号喵" else "Invalid API Key or Secret"
            "auth_ip_not_allowed" -> if (lang == Lang.ZH) "当前 IP 不在白名单里" else "Request IP not in whitelist"
            "api_access_disabled" -> if (lang == Lang.ZH) "后台已关闭 API 访问" else "API access disabled"
            "quota_exceeded" -> if (lang == Lang.ZH) "配额已用完" else "Quota exceeded"
            "rate_limit_exceeded" -> if (lang == Lang.ZH) "请求太频繁了，等一下下喵" else "Rate limit exceeded"
            "provider_operation_failed" -> if (lang == Lang.ZH) "上游 DNS 服务商操作失败" else "Upstream DNS provider failed"
            "internal_error" -> if (lang == Lang.ZH) "服务端内部错误" else "Internal service error"
            "feature_disabled" -> if (lang == Lang.ZH) "该功能未开启" else "Feature disabled"
            "subdomain_not_found" -> if (lang == Lang.ZH) "找不到这个子域名" else "Subdomain not found"
            "dns_record_not_found" -> if (lang == Lang.ZH) "找不到这条 DNS 记录" else "DNS record not found"
            "renewal_not_yet_available" -> if (lang == Lang.ZH) "还没到续期时间窗口" else "Renewal window not open yet"
            else -> null
        }
        friendly ?: "[$code] ${e.message}"
    } ?: e.message
    else -> e.message ?: e::class.java.simpleName
}
