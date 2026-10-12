package io.nekoshizuku.dnshe.core

/**
 * 官网注册页提供的免费根域名清单。
 *
 * 「外部域名」= 由第三方提供、不是 DNSHE 官方运营的根域名，
 * 稳定性 / 可用性 / 安全性 / 续期政策都无法保证，界面上必须标注清楚。
 *
 * 这份清单来自官网 register.php 的根域名下拉框（2026-10 核对）。
 * 官网随时可能新增或下线，所以注册页保留了「手动输入」入口。
 */
data class RootDomainOption(
    val domain: String,
    val external: Boolean,
)

object RootDomains {

    /** 官方根域名 */
    val OFFICIAL: List<RootDomainOption> = listOf(
        "l.cd",
        "us.ci",
        "cc.cd",
        "bot.cd",
        "de5.net",
        "ccwu.cc",
        "ddns.ge",
        "bbroot.com",
    ).map { RootDomainOption(it, external = false) }

    /** 第三方（外部）根域名 */
    val EXTERNAL: List<RootDomainOption> = listOf(
        "kz.ci",
        "xyz.ci",
        "pc.ci",
    ).map { RootDomainOption(it, external = true) }

    val ALL: List<RootDomainOption> = OFFICIAL + EXTERNAL

    fun find(root: String): RootDomainOption? =
        ALL.firstOrNull { it.domain.equals(root.trim(), ignoreCase = true) }

    /** 是否是已知的外部（第三方）根域名 */
    fun isExternal(root: String): Boolean = find(root)?.external == true

    /** 是否在已知清单里（不在的话可能是新上线或已下线） */
    fun isKnown(root: String): Boolean = find(root) != null
}
