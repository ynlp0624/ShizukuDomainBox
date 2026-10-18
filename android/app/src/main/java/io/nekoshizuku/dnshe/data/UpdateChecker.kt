package io.nekoshizuku.dnshe.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 检查更新。
 *
 * 从 GitHub / GitCode 的 Release 接口拿最新版本，和当前版本比对。
 * 两个源都是公开接口，不需要 token。
 *
 * GitCode 在国内快，优先用它；失败再退回 GitHub。
 */
class UpdateChecker(
    private val client: OkHttpClient = defaultClient(),
) {

    companion object {
        const val OWNER_GH = "ynlp0624"
        const val OWNER_GC = "gcw_YiSvwnI0"
        const val REPO = "ShizukuDomainBox"

        private const val API_GH =
            "https://api.github.com/repos/$OWNER_GH/$REPO/releases/latest"
        private const val API_GC =
            "https://api.gitcode.com/api/v5/repos/$OWNER_GC/$REPO/releases/latest"

        /** 国内下载直链模板 */
        private const val GC_DL =
            "https://gitcode.com/$OWNER_GC/$REPO/releases/download/%s/%s"
        private const val GH_DL =
            "https://github.com/$OWNER_GH/$REPO/releases/download/%s/%s"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .build()

        /**
         * 版本号比较。
         *
         * 支持 `v2.1.0` / `2.1.0` / `2.1` / `2.1.0-beta1` 这类写法。
         * 返回 true 表示 [remote] 比 [current] 新。
         */
        fun isNewer(remote: String, current: String): Boolean {
            val a = parseVersion(remote)
            val b = parseVersion(current)
            val n = maxOf(a.size, b.size)
            for (i in 0 until n) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }

        /** 把版本号切成数字数组，非数字部分丢弃 */
        fun parseVersion(v: String): List<Int> {
            val clean = v.trim().removePrefix("v").removePrefix("V")
            // 只取 - 或 + 之前的部分，避免 2.1.0-beta 里的 beta 干扰
            val core = clean.substringBefore('-').substringBefore('+')
            return core.split('.', '_')
                .mapNotNull { it.takeWhile { ch -> ch.isDigit() }.toIntOrNull() }
        }

        /** 从资产名判断是哪个平台的包 */
        fun pickAsset(name: String, platform: Platform): Boolean = when (platform) {
            Platform.ANDROID ->
                name.contains("Android", true) && name.endsWith(".apk", true)
            Platform.WINDOWS ->
                (name.contains("Setup", true) || name.contains("Windows", true)) &&
                    name.endsWith(".exe", true)
        }
    }

    enum class Platform { ANDROID, WINDOWS }

    /**
     * 检查更新。
     *
     * @return [Result.success] 里为 null 表示已是最新；非 null 表示有新版本。
     *         两个源都失败时返回 [Result.failure]。
     */
    suspend fun check(
        currentVersion: String,
        platform: Platform,
        preferGitCode: Boolean = true,
    ): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        val sources = if (preferGitCode) {
            listOf(Source.GITCODE to API_GC, Source.GITHUB to API_GH)
        } else {
            listOf(Source.GITHUB to API_GH, Source.GITCODE to API_GC)
        }

        var lastError: Throwable? = null
        for ((source, url) in sources) {
            try {
                val info = fetchOne(url, source, platform)
                    ?: continue
                return@withContext if (isNewer(info.version, currentVersion)) {
                    Result.success(info)
                } else {
                    Result.success(null)
                }
            } catch (e: Throwable) {
                lastError = e
            }
        }
        Result.failure(lastError ?: IllegalStateException("no source available"))
    }

    private enum class Source(val label: String) {
        GITCODE("GitCode"),
        GITHUB("GitHub"),
    }

    private fun fetchOne(url: String, source: Source, platform: Platform): UpdateInfo? {
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "ShizukuDomainBox")
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val text = resp.body?.string().orEmpty()
            if (text.isBlank()) return null

            val root = Json.parseToJsonElement(text).jsonObject
            val tag = root["tag_name"]?.jsonPrimitive?.contentOrNull ?: return null
            val version = tag.removePrefix("v").removePrefix("V")
            if (version.isBlank()) return null

            val title = root["name"]?.jsonPrimitive?.contentOrNull
                ?: "$tag"
            val notes = root["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val publishedAt = root["published_at"]?.jsonPrimitive?.contentOrNull
                ?: root["created_at"]?.jsonPrimitive?.contentOrNull
                ?: ""

            // 找当前平台的安装包
            val assets = root["assets"] as? JsonArray ?: JsonArray(emptyList())
            var dlUrl: String? = null
            var dlSize = 0L
            for (a in assets) {
                val obj = a as? JsonObject ?: continue
                val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: continue
                if (!pickAsset(name, platform)) continue
                dlUrl = obj["browser_download_url"]?.jsonPrimitive?.contentOrNull
                dlSize = obj["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
                if (dlUrl != null) break
            }

            // 资产没解析到就按命名规则拼一个
            if (dlUrl == null) {
                val fileName = when (platform) {
                    Platform.ANDROID -> "ShizukuDomainBox-Android-v$version.apk"
                    Platform.WINDOWS -> "ShizukuDomainBox-Setup-v$version.exe"
                }
                val tpl = when (source) {
                    Source.GITCODE -> GC_DL
                    Source.GITHUB -> GH_DL
                }
                dlUrl = String.format(tpl, tag, fileName)
            }

            return UpdateInfo(
                version = version,
                tagName = tag,
                title = title,
                notes = notes,
                publishedAt = publishedAt,
                downloadUrl = dlUrl,
                sizeBytes = dlSize,
                source = source.label,
            )
        }
    }
}

/** 检测到的更新信息 */
data class UpdateInfo(
    val version: String,
    val tagName: String,
    val title: String,
    val notes: String,
    val publishedAt: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val source: String,
) {}
