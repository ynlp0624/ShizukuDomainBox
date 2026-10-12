package io.nekoshizuku.pack.verify

import io.nekoshizuku.kit.InstallerConfig
import io.nekoshizuku.kit.InstallerConfigs
import io.nekoshizuku.pack.AppImageProbe
import io.nekoshizuku.pack.KitPaths
import io.nekoshizuku.pack.PackerArgs
import io.nekoshizuku.pack.ReleaseInfo
import java.io.File

/**
 * packer 的无头自检：`gradle :packer:verifyPacker`
 *
 * 界面点不了（Compose 不吃合成点击，见 docs/PITFALLS.md 第 22 条），
 * 所以打包器的正确性靠这里盯：**探测逻辑、工程定位、配置往返、命令行解析**。
 * 「点一下就能打包」这条链路则由端到端验收（tools\verify.cmd）保证 —— 它们跑的是同一套脚本。
 */

private var passed = 0
private val failed = mutableListOf<String>()

private fun ok(name: String, condition: Boolean, detail: String = "") {
    if (condition) {
        passed++
        println("  [OK]   $name")
    } else {
        failed += name
        println("  [FAIL] $name   $detail")
    }
}

private fun <T> eq(name: String, expected: T, actual: T) =
    ok(name, expected == actual, "期望=$expected 实际=$actual")

fun main() {
    println("=== packer 自检 ===")

    // ---------------- 工程定位 ----------------

    println("KitPaths")
    val kitRoot = KitPaths.detect()
    ok("能从当前工作目录找到工程根（tools\\build-installer.ps1）", kitRoot != null, "没找到")
    if (kitRoot != null) {
        ok("工程里有 build-installer.ps1", File(kitRoot, KitPaths.BUILD_SCRIPT).isFile)
        ok("工程里有 verify-install.ps1", File(kitRoot, KitPaths.VERIFY_SCRIPT).isFile)
        ok("非工程目录不会被误判", !KitPaths.looksLikeKitRoot(File(kitRoot, "samples")))
        ok("null 不会被误判", !KitPaths.looksLikeKitRoot(null))
    }

    // ---------------- 探测真实 app-image ----------------

    println("AppImageProbe · 真实样例")
    val demoImage = kitRoot?.let {
        File(it, "samples\\demo-app\\build\\compose\\binaries\\main\\app\\KitDemoBox")
    }
    if (demoImage != null && demoImage.isDirectory) {
        val probe = AppImageProbe.probe(demoImage)
        eq("认出来是 jpackage 程序", AppImageProbe.Kind.JPACKAGE, probe.kind)
        eq("给的 installMode 是 jpackage", "jpackage", probe.installMode)
        eq("认出主程序 exe", "KitDemoBox.exe", probe.exeName)
        eq("按同名规则找到 cfg", "KitDemoBox.cfg", probe.cfgName)
        ok("数出了 jar 数量（>=30）", probe.jarCount >= 30, "实际 ${probe.jarCount}")
        ok("检测到 runtime\\", probe.hasRuntime)
        ok("运行时里有 java.desktop", probe.hasJavaDesktop)
        ok("算出体积 > 100MB", probe.sizeMb > 100, "实际 ${probe.sizeMb} MB")
        ok("判定为可用", probe.usable, probe.problems.toString())
    } else {
        println("  [skip] 样例 app-image 还没构建（先跑 gradle :samples:demo-app:createDistributable）")
    }

    // ---------------- 探测：认不出来的目录 ----------------

    println("AppImageProbe · 认不出来的目录")
    // ⚠️ 别拿 %TEMP% 本身当「空目录」用 —— 那里头真的躺着别人解压出来的 exe
    // （实测见过一个 geek64.exe），会让人误以为探测出错了。
    val emptyDir = File(System.getProperty("java.io.tmpdir"), "kit-probe-empty-${System.nanoTime()}")
    emptyDir.mkdirs()
    val bad = AppImageProbe.probe(emptyDir)
    ok("空目录不会被当成程序", !bad.usable && bad.kind == AppImageProbe.Kind.UNKNOWN)
    ok("给出了问题说明", bad.problems.isNotEmpty(), bad.problems.toString())
    eq("认不出来时 summary 是一句人话", "这个文件夹不像是个程序", bad.summary)
    emptyDir.deleteRecursively()

    val missing = AppImageProbe.probe(File(System.getProperty("java.io.tmpdir"), "definitely-not-here-${System.nanoTime()}"))
    ok("不存在的目录也不炸", !missing.usable && missing.problems.isNotEmpty())

    // ---------------- 探测：绿色软件（一个 exe + 一堆文件） ----------------

    println("AppImageProbe · 绿色软件")
    val greenDir = File(System.getProperty("java.io.tmpdir"), "kit-probe-green-${System.nanoTime()}")
    runCatching {
        File(greenDir, "data\\smartscreen").mkdirs()
        File(greenDir, "我的工具.exe").writeText("not a real exe", Charsets.UTF_8)
        File(greenDir, "更新日志.txt").writeText("v1.0 改了点东西", Charsets.UTF_8)
        File(greenDir, "data\\smartscreen\\rules.dat").writeText("x", Charsets.UTF_8)

        val g = AppImageProbe.probe(greenDir)
        eq("绿色软件被认出是 PORTABLE", AppImageProbe.Kind.PORTABLE, g.kind)
        eq("给的 installMode 是 portable", "portable", g.installMode)
        eq("认出中文名 exe", "我的工具.exe", g.exeName)
        ok("绿色软件可用（不需要 cfg / jar / runtime）", g.usable, g.problems.toString())
        ok("数出了文件总数（3 个）", g.fileCount == 3, "实际 ${g.fileCount}")
        ok("没有 jpackage 那套东西也不报警", g.problems.isEmpty(), g.problems.toString())
        ok("summary 写明是绿色软件", g.summary.contains("绿色软件"), g.summary)

        // 只有 jar 没有 exe 的目录不算数
        val noExe = File(greenDir, "data")
        val n = AppImageProbe.probe(noExe)
        ok("没有 exe 的目录认不出来", !n.usable && n.kind == AppImageProbe.Kind.UNKNOWN)

        // 有 app\ 和 .cfg 但没有 runtime\ —— 像是 jpackage 少了一半，要提醒
        val halfJp = File(System.getProperty("java.io.tmpdir"), "kit-probe-half-${System.nanoTime()}")
        File(halfJp, "app").mkdirs()
        File(halfJp, "Half.exe").writeText("x", Charsets.UTF_8)
        File(halfJp, "app\\Half.cfg").writeText("app.mainclass=x", Charsets.UTF_8)
        File(halfJp, "app\\a.jar").writeText("x", Charsets.UTF_8)
        val h = AppImageProbe.probe(halfJp)
        eq("缺 runtime 的按绿色软件处理", AppImageProbe.Kind.PORTABLE, h.kind)
        ok("但会提醒缺运行时", h.problems.any { it.contains("runtime") }, h.problems.toString())
        halfJp.deleteRecursively()
    }.onFailure { ok("绿色软件探测没有抛异常", false, it.toString()) }
    greenDir.deleteRecursively()

    // ---------------- 配置往返 ----------------

    println("配置往返")
    val cfg = InstallerConfig(
        productName = "雫的测试盒",
        appId = "KitProbeBox",
        version = "2.5.1",
        publisher = "Shizuku Studio",
        brandColor = "#7C4DFF",
        codeSign = InstallerConfig().codeSign.copy(enabled = true, profile = "studio"),
    )
    val roundTrip = InstallerConfigs.parse(InstallerConfigs.encode(cfg))
    eq("encode → parse 一致", cfg, roundTrip)
    ok("codeSign 子配置跟着走", roundTrip.codeSign.enabled && roundTrip.codeSign.profile == "studio")

    // ---------------- 命令行解析 ----------------

    println("PackerArgs")
    val a1 = PackerArgs(
        listOf(
            "--headless", "--app-image=D:\\x\\MyApp", "--product=我的工具",
            "--app-id=MyTool", "--version=1.2.3", "--build", "--no-iexpress",
        ),
    )
    ok("认出 headless", a1.headless)
    eq("解析 --app-image", File("D:\\x\\MyApp"), a1.appImage)
    eq("解析 --product（中文）", "我的工具", a1.productName)
    eq("解析 --app-id", "MyTool", a1.appId)
    eq("解析 --version", "1.2.3", a1.version)
    ok("解析 --build", a1.build)
    ok("解析 --no-iexpress", a1.noIExpress)
    ok("没给 --no-product-build 就不跳过", !a1.noProductBuild)

    val a2 = PackerArgs(listOf("--cli"))
    ok("--cli 等价 --headless", a2.headless)
    eq("没给 product 就是 null", null, a2.productName)
    ok("默认不打包", !a2.build)

    // ---------------- 产物信息解析 ----------------

    println("ReleaseInfo")
    val release = ReleaseInfo.parse(
        """
        {
            "productName":  "雫的演示盒",
            "appId":  "KitDemoBox",
            "version":  "1.0.0",
            "stageDir":  "D:\\installer-kit\\build\\stage",
            "installer":  "D:\\installer-kit\\dist\\KitDemoBox-Setup-v1.0.0.exe",
            "sha256":  "ABC123"
        }
        """.trimIndent(),
    )
    eq("解析 productName（中文）", "雫的演示盒", release.productName)
    eq("解析 version", "1.0.0", release.version)
    eq("解析 installer", "D:\\installer-kit\\dist\\KitDemoBox-Setup-v1.0.0.exe", release.installer)
    eq("解析 sha256", "ABC123", release.sha256)
    eq("空 installer 不会炸", null, ReleaseInfo.parse("""{"installer": null}""").installer)
    eq("反转义双反斜杠", "D:\\a\\b.exe", ReleaseInfo.unescapeJson("D:\\\\a\\\\b.exe"))
    eq("反转义引号", "say \"hi\"", ReleaseInfo.unescapeJson("say \\\"hi\\\""))

    // ---------------- 汇总 ----------------

    println()
    if (failed.isEmpty()) {
        println("=== 全部通过：$passed 项 ===")
    } else {
        println("=== 失败 ${failed.size} 项 / 共 $passed 项通过 ===")
        failed.forEach { println("   - $it") }
        kotlin.system.exitProcess(1)
    }
}
