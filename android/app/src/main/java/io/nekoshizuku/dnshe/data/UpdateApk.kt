package io.nekoshizuku.dnshe.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 更新包下载与安装。
 *
 * 直接用 OkHttp 流式下载（不走 DownloadManager —— 后者在部分设备/模拟器上
 * 会长期停在 PENDING，且拿不到可靠进度）。下完通过 FileProvider 交给系统安装器。
 */
object UpdateApk {

    private const val DIR = "updates"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * 下载更新包。
     *
     * @param onProgress (已下载字节, 总字节)；总字节未知时为 -1
     * @return 下载好的文件；失败/取消返回 null
     */
    suspend fun download(
        ctx: Context,
        url: String,
        fileName: String,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): File? = withContext(Dispatchers.IO) {
        val dir = ctx.getExternalFilesDir(DIR) ?: return@withContext null
        if (!dir.exists() && !dir.mkdirs()) return@withContext null

        val target = File(dir, fileName)
        val temp = File(dir, "$fileName.part")

        // 清掉上次残留
        runCatching { temp.takeIf { it.exists() }?.delete() }

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "ShizukuDomainBox")
            .header("Accept", "*/*")
            .build()

        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body ?: return@withContext null
                val total = body.contentLength()

                body.byteStream().use { input ->
                    temp.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var lastReport = 0L
                        while (true) {
                            // 用户取消时这里是取消点
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n <= 0) break
                            output.write(buf, 0, n)
                            done += n
                            if (done - lastReport >= 256 * 1024 || done == total) {
                                lastReport = done
                                withContext(Dispatchers.Main) { onProgress(done, total) }
                            }
                        }
                        output.flush()
                        withContext(Dispatchers.Main) {
                            onProgress(done, if (total > 0) total else done)
                        }
                    }
                }
            }

            if (!temp.exists() || temp.length() <= 0) return@withContext null

            // 清掉同名旧包再改名
            runCatching { target.takeIf { it.exists() }?.delete() }
            if (!temp.renameTo(target)) {
                // 改名失败就直接用临时文件
                return@withContext temp
            }
            target
        } catch (e: Throwable) {
            runCatching { temp.takeIf { it.exists() }?.delete() }
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    /**
     * 调起系统安装器。
     *
     * 优先直接指定系统「软件包安装程序」，避免弹出一堆文件管理器让用户选。
     * 找不到匹配的安装器时退回普通 chooser。
     */
    fun install(ctx: Context, file: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(
            ctx,
            "${ctx.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // 找系统安装器
        val pm = ctx.packageManager
        val handlers = pm.queryIntentActivities(intent, 0)
        val installer = handlers.firstOrNull { ri ->
            val pkg = ri.activityInfo.packageName
            pkg.contains("packageinstaller", true) ||
                pkg.contains("package.installer", true) ||
                pkg == "com.android.packageinstaller" ||
                pkg == "com.google.android.packageinstaller"
        }

        if (installer != null) {
            intent.setClassName(
                installer.activityInfo.packageName,
                installer.activityInfo.name,
            )
            ctx.startActivity(intent)
        } else if (handlers.isNotEmpty()) {
            // 没有明确安装器，让系统弹选择器
            ctx.startActivity(Intent.createChooser(intent, null).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } else {
            return@runCatching false
        }
        true
    }.getOrDefault(false)

    /** 是否已允许「安装未知来源」 */
    fun canInstallPackages(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { ctx.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
        } else {
            true
        }

    /** 跳到「安装未知来源」授权页 */
    fun openInstallSettings(ctx: Context) {
        runCatching {
            ctx.startActivity(
                Intent(
                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${ctx.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** 已下载好的包（下次可直接装，不用重下） */
    fun existing(ctx: Context, fileName: String): File? =
        File(ctx.getExternalFilesDir(DIR), fileName)
            .takeIf { it.exists() && it.length() > 0 }

    /** 用浏览器打开下载地址（兜底） */
    fun openInBrowser(ctx: Context, url: String) {
        runCatching {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
