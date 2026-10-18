package io.nekoshizuku.dnshe.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * mail.v.cd 开放 API 客户端。
 *
 * **这是逆向分析出来的接口，官方尚未发布文档。**
 * 详见仓库根目录 `mail-v-cd-API-逆向文档.md`。
 *
 * 认证：`Authorization: Bearer <token>`
 *
 * 已实测的能力：
 *  - 发信（真实投递，每分钟限 5 封）
 *  - 查投递状态（queued → sending → delivered / failed）
 *  - 读收件箱 / 已发送（含正文内联返回）
 *  - 下载附件
 *
 * 已知限制：
 *  - **不支持 CORS**，只能客户端/后端直连，不能网页前端 fetch
 *  - 不能改已读/星标、不能移动或删除邮件（未找到端点）
 *  - 发信不支持模板、定时、自定义头部
 */
const val MAIL_BASE_URL = "https://mail.v.cd/api/open/v1"

/** 发信频率限制：每分钟 5 封 */
const val MAIL_RATE_LIMIT_PER_MINUTE = 5

class MailApi(private val logger: (RequestLogEntry) -> Unit = {}) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
        encodeDefaults = false
    }

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()

    /** 发信时间戳队列，用于本地限流（服务端不回 Retry-After，只能自己数） */
    private val recentSends = ArrayDeque<Long>()

    /* ======================== 核心请求 ======================== */

    private suspend fun <T> call(
        token: String,
        method: String,
        path: String,
        deserializer: DeserializationStrategy<T>,
        body: JsonObject? = null,
        endpointLabel: String = path,
        extraHeaders: Map<String, String> = emptyMap(),
    ): T = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val url = MAIL_BASE_URL.trimEnd('/') + "/" + path.trimStart('/')

        val hex = token.toByteArray(Charsets.UTF_8).joinToString("") { String.format("%02x", it) }
        val reqBuilder = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .apply { extraHeaders.forEach { (k, v) -> header(k, v) } }

        when (method.uppercase()) {
            "GET" -> reqBuilder.get()
            "POST" -> reqBuilder.post(
                json.encodeToString(JsonObject.serializer(), body ?: buildJsonObject { })
                    .toRequestBody(jsonMedia)
            )
            "DELETE" -> reqBuilder.delete()
            else -> reqBuilder.get()
        }

        val call = client.newCall(reqBuilder.build())

        var httpStatus = 0
        try {
            call.execute().use { resp ->
                httpStatus = resp.code
                val text = resp.body?.string().orEmpty()
                val ms = System.currentTimeMillis() - started

                if (httpStatus !in 200..299) {
                    // 错误体固定是 {"error":"..."}
                    val msg = runCatching {
                        json.parseToJsonElement(text)
                            .let { it as? JsonObject }
                            ?.get("error")
                            ?.let { (it as? JsonPrimitive)?.content }
                    }.getOrNull() ?: text.take(300).ifBlank { "HTTP $httpStatus" }

                    val code = when (httpStatus) {
                        401 -> "unauthorized"
                        403 -> "forbidden"
                        429 -> "rate_limited"
                        404 -> "not_found"
                        400 -> "bad_request"
                        else -> "http_$httpStatus"
                    }
                    logger(RequestLogEntry(started, method.uppercase(), endpointLabel, null, httpStatus, false, ms, code, msg))
                    throw ApiException(httpStatus, code, msg)
                }

                val parsed = try {
                    json.decodeFromString(deserializer, text)
                } catch (t: Throwable) {
                    logger(
                        RequestLogEntry(
                            started, method.uppercase(), endpointLabel, null, httpStatus, false, ms,
                            "parse_error", t.message ?: "JSON 解析失败",
                        )
                    )
                    throw ApiException(httpStatus, "parse_error", "响应解析失败: ${t.message}\n${text.take(400)}")
                }

                logger(RequestLogEntry(started, method.uppercase(), endpointLabel, null, httpStatus, true, ms, null, null))
                parsed
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: Throwable) {
            val ms = System.currentTimeMillis() - started
            logger(
                RequestLogEntry(
                    started, method.uppercase(), endpointLabel, null, httpStatus, false, ms,
                    "network_error", e.message ?: e::class.java.simpleName,
                )
            )
            throw ApiException(httpStatus, "network_error", e.message ?: "网络请求失败")
        }
    }

    /* ======================== 1. 发信 ======================== */

    /**
     * 发一封邮件。
     *
     * @throws ApiException 429 时表示触发限流（每分钟 5 封）
     */
    suspend fun send(
        token: String,
        compose: MailCompose,
        idempotencyKey: String? = null,
    ): MailSendResult {
        require(compose.isValid) { "至少需要一个收件人" }
        require(compose.recipientCount <= 100) { "收件人最多 100 个" }
        require(compose.attachments.size <= 20) { "附件最多 20 个" }

        val body = buildJsonObject {
            if (compose.mailboxId.isNotBlank()) put("mailboxId", compose.mailboxId)
            putJsonArray("to") { compose.to.forEach { add(JsonPrimitive(it)) } }
            if (compose.cc.isNotEmpty()) putJsonArray("cc") { compose.cc.forEach { add(JsonPrimitive(it)) } }
            if (compose.bcc.isNotEmpty()) putJsonArray("bcc") { compose.bcc.forEach { add(JsonPrimitive(it)) } }
            if (compose.subject.isNotBlank()) put("subject", compose.subject)
            if (compose.text.isNotBlank()) put("text", compose.text)
            if (compose.html.isNotBlank()) put("html", compose.html)
            if (compose.attachments.isNotEmpty()) {
                putJsonArray("attachments") {
                    compose.attachments.forEach { a ->
                        addJsonObject {
                            put("filename", a.filename)
                            put("contentBase64", a.contentBase64)
                            if (a.contentType.isNotBlank()) put("contentType", a.contentType)
                        }
                    }
                }
            }
        }

        val key = idempotencyKey ?: java.util.UUID.randomUUID().toString()
        val result = call(
            token = token,
            method = "POST",
            path = "send",
            deserializer = MailSendResult.serializer(),
            body = body,
            endpointLabel = "send",
            extraHeaders = mapOf("Idempotency-Key" to key.take(128)),
        )
        synchronized(recentSends) {
            val now = System.currentTimeMillis()
            recentSends.addLast(now)
            while (recentSends.isNotEmpty() && now - recentSends.first() > 60_000) {
                recentSends.removeFirst()
            }
        }
        return result
    }

    /**
     * 本地估算还能发几封（服务端限流 5 封/分钟，且不给 Retry-After）。
     * 返回 0 时建议等 [secondsUntilNextSlot] 秒。
     */
    fun remainingQuota(): Int = synchronized(recentSends) {
        val now = System.currentTimeMillis()
        while (recentSends.isNotEmpty() && now - recentSends.first() > 60_000) {
            recentSends.removeFirst()
        }
        (MAIL_RATE_LIMIT_PER_MINUTE - recentSends.size).coerceAtLeast(0)
    }

    /** 距离下一个发信名额还有几秒 */
    fun secondsUntilNextSlot(): Long = synchronized(recentSends) {
        if (recentSends.isEmpty()) return 0
        val now = System.currentTimeMillis()
        val oldest = recentSends.first()
        val remain = 60_000 - (now - oldest)
        if (remain <= 0) 0 else (remain / 1000) + 1
    }

    /* ======================== 2. 发信记录 ======================== */

    /**
     * 查发信队列。
     *
     * 注意：这个列表只包含**队列中**的记录，已投递的会被移走。
     * 想看已发送的邮件请用 [listMessages] 的 `Sent` 文件夹。
     */
    suspend fun listQueue(
        token: String,
        status: MailQueueStatus? = null,
        limit: Int = 20,
        cursor: String = "",
    ): MailQueueResponse {
        val q = buildList {
            add("limit=$limit")
            if (status != null) add("status=${status.value}")
            if (cursor.isNotBlank()) add("cursor=$cursor")
        }.joinToString("&")
        return call(token, "GET", "send?$q", MailQueueResponse.serializer(), null, "send")
    }

    /** 查单条发信状态。id 可以是 mail_xxx 或 snd_xxx。 */
    suspend fun getSend(token: String, id: String): MailSendResult =
        call(token, "GET", "send/$id", MailSendResult.serializer(), null, "send/$id")

    /* ======================== 3. 邮件（收信） ======================== */

    /**
     * 列邮件。
     *
     * @param mailboxId 形如 mbx_xxxxx，从 [MailAccount.mailboxId] 取
     * @param folder Inbox / Sent / Drafts / ...
     */
    suspend fun listMessages(
        token: String,
        mailboxId: String,
        folder: MailFolder? = null,
        limit: Int = 30,
        cursor: String = "",
        search: String = "",
    ): MailListResponse {
        val q = buildList {
            add("limit=$limit")
            if (folder != null) add("folder=${folder.value}")
            if (cursor.isNotBlank()) add("cursor=$cursor")
            if (search.isNotBlank()) add("search=$search")
        }.joinToString("&")
        return call(
            token, "GET", "mailboxes/$mailboxId/messages?$q",
            MailListResponse.serializer(), null, "mailboxes/*/messages",
        )
    }

    /** 取单封邮件（含完整正文与附件列表） */
    suspend fun getMessage(token: String, messageId: String): MailMessage =
        call(token, "GET", "messages/$messageId", MailMessage.serializer(), null, "messages/$messageId")

    /* ======================== 4. 附件 ======================== */

    /**
     * 下载附件原始字节。
     *
     * 只有裸路径 `/attachments/{id}` 能用，
     * `/attachments/{id}/download` 之类都是 404。
     */
    suspend fun downloadAttachment(token: String, attachmentId: String): ByteArray =
        withContext(Dispatchers.IO) {
            val started = System.currentTimeMillis()
            val req = Request.Builder()
                .url("$MAIL_BASE_URL/attachments/$attachmentId")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            try {
                client.newCall(req).execute().use { resp ->
                    val ms = System.currentTimeMillis() - started
                    if (!resp.isSuccessful) {
                        val msg = resp.body?.string()?.take(300) ?: "HTTP ${resp.code}"
                        logger(
                            RequestLogEntry(
                                started, "GET", "attachments/$attachmentId", null, resp.code, false, ms,
                                "http_${resp.code}", msg,
                            )
                        )
                        throw ApiException(resp.code, "http_${resp.code}", msg)
                    }
                    val bytes = resp.body?.bytes() ?: ByteArray(0)
                    logger(
                        RequestLogEntry(
                            started, "GET", "attachments/$attachmentId", null, resp.code, true, ms, null, null,
                        )
                    )
                    bytes
                }
            } catch (e: ApiException) {
                throw e
            } catch (e: Throwable) {
                val ms = System.currentTimeMillis() - started
                logger(
                    RequestLogEntry(
                        started, "GET", "attachments/$attachmentId", null, 0, false, ms,
                        "network_error", e.message ?: e::class.java.simpleName,
                    )
                )
                throw ApiException(0, "network_error", e.message ?: "下载失败")
            }
        }

    /* ======================== 5. 连通性自检 ======================== */

    /**
     * 验证 token 是否可用。
     *
     * 用 `GET /send?limit=1` 探测：token 无效会 401，
     * 有效则返回空列表（200）。不会真的发信。
     */
    /* ======================== 我的邮箱（官方 /me/mailboxes） ======================== */

    /**
     * 列出 Token 所属账户下的邮箱。
     *
     * 这是官方推荐拿 mailboxId 的方式，比让用户手填靠谱得多。
     * 只返回 active 的本地邮箱（不含共享、外部、锁定、已删除）。
     */
    suspend fun listMyMailboxes(
        token: String,
        limit: Int = 50,
        cursor: String = "",
    ): MailboxListResponse {
        val q = buildString {
            append("me/mailboxes?limit=").append(limit.coerceIn(1, 100))
            if (cursor.isNotBlank()) append("&cursor=").append(java.net.URLEncoder.encode(cursor, "UTF-8"))
        }
        return call(token, "GET", q, MailboxListResponse.serializer(), null, "me/mailboxes")
    }

    /**
     * 拉全所有邮箱（自动翻页）。
     */
    suspend fun listAllMyMailboxes(token: String): List<MailboxInfo> {
        val out = mutableListOf<MailboxInfo>()
        var cursor = ""
        var guard = 0
        while (guard++ < 20) {
            val page = listMyMailboxes(token, limit = 100, cursor = cursor)
            out += page.items
            cursor = page.nextCursor
            if (cursor.isBlank() || page.items.isEmpty()) break
        }
        return out
    }

    /* ======================== 发送记录与投递事件 ======================== */

    /** `GET /send/{id}/events` —— 查看投递事件（queued / relayed / delivered ...） */
    suspend fun sendEvents(token: String, sendId: String): List<SendEvent> =
        call(
            token, "GET", "send/$sendId/events",
            SendEventResponse.serializer(), null, "send/events",
        ).auditEvents

    /** `POST /send/{id}/retry` —— 重试失败的发送（需要 messages:manage） */
    suspend fun retrySend(token: String, sendId: String): MailSendResult =
        call(token, "POST", "send/$sendId/retry", MailSendResult.serializer(),
            buildJsonObject { }, "send/retry")

    /** `POST /send/{id}/cancel` —— 取消还在队列里的发送（需要 messages:manage） */
    suspend fun cancelSend(token: String, sendId: String): MailSendResult =
        call(token, "POST", "send/$sendId/cancel", MailSendResult.serializer(),
            buildJsonObject { }, "send/cancel")

    suspend fun verify(token: String): Boolean = try {
        listQueue(token, limit = 1)
        true
    } catch (e: ApiException) {
        if (e.httpStatus == 401) false else throw e
    }
}
