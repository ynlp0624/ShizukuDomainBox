package io.nekoshizuku.pack

import io.nekoshizuku.kit.InstallerConfig
import io.nekoshizuku.kit.InstallerConfigs
import java.io.File

/**
 * 打包器的命令行参数。
 *
 * 无界面模式是给 CI / 脚本用的（也是 packer 能被无头自检的前提）：
 *
 * ```
 * packer --headless --app-image D:\myapp\build\...\MyApp --product "我的工具" --app-id MyTool \
 *        --version 1.0.0 --out D:\installer-kit\my-tool.config.json --build
 * ```
 */
class PackerArgs(raw: List<String>) {

    val rawArgs: List<String> = raw.toList()

    private fun flag(name: String): Boolean = rawArgs.any { it.equals(name, ignoreCase = true) }

    private fun value(vararg names: String): String? =
        rawArgs.firstOrNull { a -> names.any { a.startsWith("$it=", ignoreCase = true) } }
            ?.substringAfter('=')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    val headless: Boolean get() = flag("--headless") || flag("--cli")

    /** 界面默认是简单版；加 --pro 直接开专业版 */
    val proMode: Boolean get() = flag("--pro") || flag("--professional")

    val kitRoot: File? get() = value("--kit")?.let { File(it) }
    val appImage: File? get() = value("--app-image")?.let { File(it) }
    val productName: String? get() = value("--product")
    val appId: String? get() = value("--app-id")
    val version: String? get() = value("--version")
    val publisher: String? get() = value("--publisher")
    val out: File? get() = value("--out")?.let { File(it) }

    /** 写完配置顺带把包也打了 */
    val build: Boolean get() = flag("--build")
    /** 跳过「构建产品 app-image」这一步 */
    val noProductBuild: Boolean get() = flag("--no-product-build")
    /** 只出目录版，不封单文件 exe */
    val noIExpress: Boolean get() = flag("--no-iexpress")
}

/** 无界面模式：探测 app-image → 写（甚至合并进已有的）installer.config.json → 可选直接打包 */
object PackerCli {

    fun run(cli: PackerArgs, log: (String) -> Unit): Int {
        val remembered = PackerSettings.load().kitRoot.takeIf { it.isNotBlank() }?.let { File(it) }
        val kitRoot = KitPaths.detect(cli.kitRoot, remembered)
        if (kitRoot == null) {
            log("找不到 InstallerKit 工程目录（里面应该有 tools\\build-installer.ps1）")
            log("用 --kit=<目录> 指一下，或者直接把这个打包器放进工程里跑")
            return 2
        }
        log("工程目录：${kitRoot.absolutePath}")

        val outFile = cli.out ?: File(kitRoot, KitPaths.DEFAULT_CONFIG)

        // 已有配置就以它为底，命令行只覆盖显式给出的字段
        var config = if (outFile.isFile) {
            runCatching { InstallerConfigs.load(outFile) }
                .onFailure { log("已有配置读不动，按默认值来：${it.message}") }
                .getOrElse { InstallerConfig() }
        } else {
            InstallerConfig()
        }

        cli.appImage?.let { img ->
            val probe = AppImageProbe.probe(img)
            probe.problems.forEach { log("⚠ $it") }
            if (!probe.usable) {
                log("这个目录不能用来打包，先修上面的问题")
                return 3
            }
            log("探测结果：${probe.summary}")
            config = config.copy(
                mainAppDir = relativize(outFile.parentFile ?: kitRoot, img),
                mainExeName = probe.exeName ?: config.mainExeName,
                estimatedSizeMb = if (probe.sizeMb > 0) probe.sizeMb.toInt() else config.estimatedSizeMb,
            )
        }

        cli.productName?.let { config = config.copy(productName = it) }
        cli.appId?.let { config = config.copy(appId = it) }
        cli.version?.let { config = config.copy(version = it) }
        cli.publisher?.let { config = config.copy(publisher = it) }

        runCatching {
            outFile.parentFile?.mkdirs()
            outFile.writeText(InstallerConfigs.encode(config), Charsets.UTF_8)
        }.onFailure {
            log("配置写不进去：${it.message}")
            return 4
        }
        log("配置已写入：${outFile.absolutePath}")
        log("  产品名 ${config.productName} / AppId ${config.appId} / 版本 ${config.effectiveVersion}")
        log("  主程序 ${config.mainAppDir}")
        log("  数字签名 ${if (config.codeSign.enabled) "开（档案 ${config.codeSign.profile}）" else "关"}")

        if (!cli.build) {
            log("加 --build 就会接着打包")
            return 0
        }

        val extra = buildList {
            if (cli.noProductBuild) add("-SkipBuild")
            if (cli.noIExpress) add("-SkipIExpress")
        }
        log("")
        log("开始打包…")
        val code = BuildRunner(kitRoot).runBlocking(outFile, extra, log)
        log("打包退出码：$code")
        return code
    }

    /** 能转成相对路径就转（配置好搬），不行就用绝对路径 */
    private fun relativize(base: File, path: File): String =
        runCatching { base.toPath().relativize(path.toPath()).toString().replace('/', '\\') }
            .getOrElse { path.absolutePath }
}
