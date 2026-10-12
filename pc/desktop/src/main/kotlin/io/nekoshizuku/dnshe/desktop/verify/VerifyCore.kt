package io.nekoshizuku.dnshe.desktop.verify

import io.nekoshizuku.dnshe.core.Account
import io.nekoshizuku.dnshe.core.AppCore
import io.nekoshizuku.dnshe.core.AppPaths
import io.nekoshizuku.dnshe.core.Dpapi
import io.nekoshizuku.dnshe.core.Lang
import io.nekoshizuku.dnshe.core.RootDomains
import io.nekoshizuku.dnshe.core.apiErrorMessage
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * 无头自检：验证 API 层、DPAPI 加密、JSON 备份互通。
 *
 * 结果同时写到 D:\dnshe-pc\verify-report.txt（UTF-8），避免控制台编码问题。
 *
 * 用法: gradle :desktop:verifyCore --args="add <key> <secret> [名字]"
 *       gradle :desktop:verifyCore --args="check"
 *       gradle :desktop:verifyCore --args="backup <文件> [口令]"
 *       gradle :desktop:verifyCore --args="import <文件> [口令]"
 */
private val report = StringBuilder()

private fun log(line: String) {
    println(line)
    report.append(line).append('\n')
}

private const val DEFAULT_BASE = "https://api005.dnshe.com/index.php"

fun main(args: Array<String>) = runBlocking {
    val mode = args.firstOrNull() ?: "check"
    log("模式: $mode")
    log("数据目录: ${AppPaths.dataDir.absolutePath}")
    log("DPAPI 可用: ${Dpapi.available}")

    try {
        when (mode) {
            "add" -> {
                val key = args[1]
                val secret = args[2]
                val name = args.getOrNull(3) ?: "PC-Test"
                val acc = AppCore.accounts.add(Account(name = name, apiKey = key, apiSecret = secret))
                log("已添加账号: ${acc.name} / ${acc.maskedKey}")
                log("账号文件: ${AppPaths.accountsFile.absolutePath} (${AppPaths.accountsFile.length()} 字节)")
                val raw = AppPaths.accountsFile.readText()
                log("secret 字段 DPAPI 加密: ${raw.contains("\"secret\": \"dpapi:")}")
                log("明文密钥是否出现在文件里: ${raw.contains(secret)}")
            }

            "check" -> {
                val acc = AppCore.accounts.current
                if (acc == null) {
                    log("没有账号，先跑 add")
                } else {
                    log("当前账号: ${acc.name} (${acc.maskedKey}) 密钥长度=${acc.apiSecret.length}")

                    val quota = runCatching { AppCore.api.quota(acc.creds, DEFAULT_BASE, 20) }
                    quota.onSuccess {
                        log("配额: 可用 ${it.quota?.available} / 共 ${it.quota?.total}（已用 ${it.quota?.used}，基础 ${it.quota?.base}，邀请 ${it.quota?.inviteBonus}）")
                    }.onFailure { log("配额失败: " + apiErrorMessage(Lang.ZH, it)) }

                    val list = runCatching { AppCore.api.listSubdomains(acc.creds, DEFAULT_BASE, 20, perPage = 50) }
                    list.onSuccess { r ->
                        log("域名列表: ${r.count ?: r.subdomains.size} 个，has_more=${r.pagination?.hasMore}")
                        r.subdomains.forEach { d ->
                            val tag = if (RootDomains.isExternal(d.rootdomain)) "[外部域名]" else if (RootDomains.isKnown(d.rootdomain)) "[官方]" else "[未知]"
                            log("  - ${d.displayDomain}  状态=${d.status}  id=${d.id}  到期=${d.expiresAt}  $tag")
                        }
                        r.subdomains.firstOrNull()?.let { d ->
                            runCatching { AppCore.api.getSubdomain(acc.creds, DEFAULT_BASE, 20, d.id) }
                                .onSuccess { dd ->
                                    log("DNS 记录 (${d.displayDomain}): ${dd.dnsRecords.size} 条")
                                    dd.dnsRecords.forEach { rec ->
                                        log("  - [${rec.id}] ${rec.type} ${rec.name} => ${rec.content} ttl=${rec.ttl} pri=${rec.priority}")
                                    }
                                }
                                .onFailure { log("取详情失败: " + apiErrorMessage(Lang.ZH, it)) }
                        }
                    }.onFailure { log("域名列表失败: " + apiErrorMessage(Lang.ZH, it)) }

                    runCatching { AppCore.api.listKeys(acc.creds, DEFAULT_BASE, 20) }
                        .onSuccess { log("API 密钥: ${it.keys.size} 个" + it.keys.joinToString("") { k -> " [${k.keyName} 调用${k.requestCount}]" }) }
                        .onFailure { log("密钥失败: " + apiErrorMessage(Lang.ZH, it)) }

                    runCatching { AppCore.api.whois(acc.creds, DEFAULT_BASE, 20, "mynmail.bbroot.com") }
                        .onSuccess { log("WHOIS: status=${it.status} 已注册=${it.registered} ns=${it.nsList.joinToString()}") }
                        .onFailure { log("WHOIS 失败: " + apiErrorMessage(Lang.ZH, it)) }

                    runCatching { AppCore.api.upgradeList(acc.creds, DEFAULT_BASE, 20) }
                        .onSuccess { log("永久升级: state 字段=${(it.state as? kotlinx.serialization.json.JsonObject)?.keys?.joinToString()}") }
                        .onFailure { log("升级中心失败: " + apiErrorMessage(Lang.ZH, it)) }

                    log("请求日志条数: ${AppCore.logs.entries.value.size}")
                }
            }

            "backup" -> {
                val out = File(args.getOrElse(1) { "dnshe-accounts-out.json" })
                val pwd = args.getOrNull(2)
                val text = AppCore.accounts.exportWithPassword(pwd)
                out.writeText(text, Charsets.UTF_8)
                log("已导出备份: ${out.absolutePath} (${out.length()} 字节, 口令加密=${!pwd.isNullOrEmpty()})")
                log("---- 备份内容 ----")
                text.lines().take(30).forEach { log(it) }
            }

            "import" -> {
                val f = File(args[1])
                val pwd = args.getOrNull(2)
                val r = AppCore.accounts.importJson(f.readText(Charsets.UTF_8), pwd)
                log("导入结果: 新增=${r.added} 更新=${r.updated} 失败=${r.failed} 错误=${r.message}")
                AppCore.accounts.accounts.value.forEach { log("  - ${it.name} ${it.maskedKey}") }
            }

            else -> log("未知模式: $mode")
        }
    } catch (t: Throwable) {
        log("异常: ${t::class.java.simpleName}: ${t.message}")
        t.stackTrace.take(6).forEach { log("    at $it") }
    }

    runCatching { File("D:\\dnshe-pc\\verify-report.txt").writeText(report.toString(), Charsets.UTF_8) }
    Unit
}
