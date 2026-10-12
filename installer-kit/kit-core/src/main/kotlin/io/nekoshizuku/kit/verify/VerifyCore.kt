package io.nekoshizuku.kit.verify

import io.nekoshizuku.kit.InstallEngine
import io.nekoshizuku.kit.InstallerConfig
import io.nekoshizuku.kit.InstallerConfigs
import io.nekoshizuku.kit.JpackageCfg
import io.nekoshizuku.kit.KitArgs
import io.nekoshizuku.kit.ModeDetector
import io.nekoshizuku.kit.RegValue
import io.nekoshizuku.kit.RunMode
import io.nekoshizuku.kit.WinExec
import io.nekoshizuku.kit.WinPaths
import io.nekoshizuku.kit.WinReg
import java.io.File

/**
 * kit-core 的无头自检。
 *
 * 刻意不用 JUnit：这台机器要尽量离线跑，少一个依赖少一个坑。
 * 跑法：`gradle :kit-core:verifyCore`
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

/** 一份和真实 jpackage app-image 长得一样的 cfg */
private val SAMPLE_CFG = """
[Application]
app.classpath=${'$'}APPDIR\desktop-51ba1aef719b5d29b6fa438f49b1b986.jar
app.mainclass=io.nekoshizuku.dnshe.desktop.MainKt
app.classpath=${'$'}APPDIR\material3-desktop-1.7.0-ee63e397b34e56c20845a98a81ef96.jar

[JavaOptions]
java-options=-Djpackage.app-version=1.0.0
java-options=-Dskiko.library.path=${'$'}APPDIR
""".trimStart()

fun main() {
    println("=== kit-core 自检 ===")

    // ---------------- JpackageCfg ----------------

    println("JpackageCfg")
    eq("读出 mainclass", "io.nekoshizuku.dnshe.desktop.MainKt", JpackageCfg.readMainClass(SAMPLE_CFG))
    eq("读出 classpath 条数", 2, JpackageCfg.readClasspath(SAMPLE_CFG).size)

    val swapped = JpackageCfg.overrideMainClass(SAMPLE_CFG, "io.nekoshizuku.kit.app.MainKt")
    eq("换掉 mainclass", "io.nekoshizuku.kit.app.MainKt", JpackageCfg.readMainClass(swapped))
    ok("换 mainclass 后 classpath 没丢", JpackageCfg.readClasspath(swapped).size == 2)
    ok("换 mainclass 后 JavaOptions 段还在", swapped.contains("[JavaOptions]"))
    ok("换 mainclass 后 skiko 选项还在", swapped.contains("-Dskiko.library.path="))

    val noMain = "[Application]\napp.classpath=\$APPDIR\\a.jar\n\n[JavaOptions]\njava-options=-Xmx1g\n"
    val inserted = JpackageCfg.overrideMainClass(noMain, "com.example.MainKt")
    eq("原本没有 mainclass 时能插进去", "com.example.MainKt", JpackageCfg.readMainClass(inserted))

    val withCp = JpackageCfg.appendClasspath(
        SAMPLE_CFG,
        listOf("${'$'}APPDIR\\kit-installer.jar", "${'$'}APPDIR\\kit-core.jar", "${'$'}APPDIR\\desktop-51ba1aef719b5d29b6fa438f49b1b986.jar"),
    )
    eq("追加 classpath（已在的不重复加）", 4, JpackageCfg.readClasspath(withCp).size)
    ok("追加的条目在 [Application] 段内", withCp.indexOf("kit-installer.jar") < withCp.indexOf("[JavaOptions]"))
    eq("追加 classpath 幂等", 4, JpackageCfg.readClasspath(JpackageCfg.appendClasspath(withCp, listOf("${'$'}APPDIR\\kit-core.jar"))).size)

    val withArgs = JpackageCfg.appendArguments(SAMPLE_CFG, "--install")
    ok("追加 [ArgOptions] 段", withArgs.contains("[ArgOptions]"))
    ok("追加 arguments=--install", withArgs.contains("arguments=--install"))
    ok("原有段落原样保留", withArgs.contains("[JavaOptions]") && withArgs.contains("app.mainclass=io.nekoshizuku.dnshe.desktop.MainKt"))

    val twice = JpackageCfg.appendArguments(withArgs, "--uninstall")
    eq("[ArgOptions] 只该有一个", 1, Regex("\\[ArgOptions]").findAll(twice).count())
    ok("重复追加时替换 arguments", twice.contains("arguments=--uninstall") && !twice.contains("arguments=--install"))

    val derived = JpackageCfg.deriveIdentity(SAMPLE_CFG, "io.nekoshizuku.kit.app.MainKt", listOf("${'$'}APPDIR\\kit.jar"), "--uninstall")
    eq("deriveIdentity 换入口", "io.nekoshizuku.kit.app.MainKt", JpackageCfg.readMainClass(derived))
    eq("deriveIdentity 补 classpath", 3, JpackageCfg.readClasspath(derived).size)
    ok("deriveIdentity 加参数", derived.contains("arguments=--uninstall"))

    // ---------------- 模式判定 ----------------

    println("ModeDetector")
    eq("--install 参数", RunMode.INSTALL, ModeDetector.detect(listOf("--install"), "KitDemoBox.exe"))
    eq("--uninstall 参数", RunMode.UNINSTALL, ModeDetector.detect(listOf("--uninstall"), "KitDemoBox.exe"))
    eq("Setup.exe 文件名", RunMode.INSTALL, ModeDetector.detect(emptyList(), "Setup.exe"))
    eq("Uninstall.exe 文件名", RunMode.UNINSTALL, ModeDetector.detect(emptyList(), "Uninstall.exe"))
    eq("大写的 setup.exe 也认", RunMode.INSTALL, ModeDetector.detect(emptyList(), "SETUP.EXE"))
    eq("普通启动", RunMode.NORMAL, ModeDetector.detect(listOf("--foo"), "KitDemoBox.exe"))
    eq("参数优先于文件名", RunMode.UNINSTALL, ModeDetector.detect(listOf("--uninstall"), "Setup.exe"))

    // ---------------- 命令行 ----------------

    println("KitArgs")
    val a1 = KitArgs(listOf("/S", "/DIR=D:\\test dir", "/SCOPE=all", "/NODESKTOP"))
    ok("/S 静默", a1.silent)
    eq("/DIR 取值（含空格）", File("D:\\test dir"), a1.installDir)
    eq("/SCOPE=all", true, a1.scopeAll)
    ok("/NODESKTOP", a1.noDesktopShortcut)

    val a2 = KitArgs(listOf("--silent", "--dir=E:\\x", "--scope=user", "--keep-data"))
    ok("--silent 等价 /S", a2.silent)
    eq("--dir 等价 /DIR", File("E:\\x"), a2.installDir)
    eq("--scope=user", false, a2.scopeAll)
    ok("--keep-data 等价 /KEEPDATA", a2.keepData)

    val a3 = KitArgs(emptyList())
    ok("空参数不静默", !a3.silent)
    eq("空参数 scope 未指定", null, a3.scopeAll)
    eq("空参数路径未指定", null, a3.installDir)

    // ---------------- 配置 ----------------

    println("InstallerConfig")
    val cfg = InstallerConfigs.parse(
        """
        {
          "_说明": "注释键应该被忽略",
          "productName": "雫的域名盒",
          "appId": "ShizukuDomainBox",
          "version": "2.3.1",
          "mainExeName": "",
          "installDirName": "",
          "dataDirName": "",
          "licenseText": "第一行\n第二行",
          "i18n": { "zh": { "k": "v" } }
        }
        """.trimIndent()
    )
    eq("中文产品名", "雫的域名盒", cfg.productName)
    eq("空 mainExeName 推导", "ShizukuDomainBox.exe", cfg.effectiveExeName)
    eq("空 installDirName 推导", "ShizukuDomainBox", cfg.effectiveInstallDirName)
    eq("licenseText 多行保留", "第一行\n第二行", cfg.licenseText)
    ok("有许可协议", cfg.hasLicense)

    val roundTrip = InstallerConfigs.parse(InstallerConfigs.encode(cfg))
    eq("序列化往返一致", cfg, roundTrip)

    // 存出来的 JSON 必须是**完整**的：不能因为「值等于默认值」就把字段省掉，
    // 否则按字面读这份 JSON 的工具（脚本 / 别的语言）会读不到 version 之类的字段。
    val encoded = InstallerConfigs.encode(InstallerConfig(version = "1.0.0"))
    ok("默认值的字段也要写出来（version）", encoded.contains("\"version\""), encoded.take(120))
    ok("默认值的字段也要写出来（estimatedSizeMb）", encoded.contains("estimatedSizeMb"))
    ok("默认值的字段也要写出来（codeSign 子对象）", encoded.contains("\"codeSign\""))

    val empty = InstallerConfig()
    ok("默认没有许可协议", !empty.hasLicense)
    eq("默认版本号兜底", "1.0.0", InstallerConfig(version = "").effectiveVersion)

    // ---------------- 路径推导 ----------------

    println("WinPaths")
    val paths = WinPaths(cfg)
    eq("主程序名", "ShizukuDomainBox.exe", paths.appExe)
    eq("启动配置名", "ShizukuDomainBox.cfg", paths.baseCfgName)
    eq("快捷方式名带中文", "雫的域名盒.lnk", paths.shortcutName())
    ok(
        "仅我安装到 LOCALAPPDATA\\Programs",
        paths.defaultInstallDir(true).absolutePath.contains("Programs\\ShizukuDomainBox"),
    )
    ok(
        "所有用户装到 Program Files",
        paths.defaultInstallDir(false).absolutePath.endsWith("ShizukuDomainBox") &&
            paths.defaultInstallDir(false).absolutePath.contains("Program Files"),
    )

    // ---------------- 安装目录校验 ----------------

    println("InstallEngine.validateInstallDir")
    eq("空目录被拒", "安装目录不能为空", InstallEngine.validateInstallDir(File("")))
    ok("盘符根被拒", InstallEngine.validateInstallDir(File("D:\\")) != null)
    eq("正常目录通过", null, InstallEngine.validateInstallDir(File("D:\\test\\KitDemoBox")))
    ok("非法字符被拒", InstallEngine.validateInstallDir(File("D:\\te*st")) != null)

    // ---------------- 注册表写入脚本 ----------------
    // 这一段专门盯住一个真踩过的坑：值里同时有空格和引号时，reg.exe 会静默失败，
    // 所以改成生成 PowerShell 脚本走 -EncodedCommand。顺便防住「Kotlin 字符串里的 $ 忘了转义」。

    println("WinReg.buildWriteScript")
    val uninstallKey = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\KitDemoBox"
    val regScript = WinReg.buildWriteScript(
        uninstallKey,
        listOf(
            RegValue("UninstallString", "\"C:\\Program Files\\X\\Uninstall.exe\""),
            RegValue("QuietUninstallString", "\"C:\\Program Files\\X\\Uninstall.exe\" /S /KEEPDATA"),
            RegValue("NoModify", "1", RegValue.REG_DWORD),
        ),
    )
    eq(
        "键路径转成 provider 形式",
        "HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\KitDemoBox",
        WinReg.toProviderPath(uninstallKey),
    )
    ok("PowerShell 变量是字面量 \$p（没被 Kotlin 当模板吃掉）", regScript.contains("\$p = "))
    ok("用了 -LiteralPath \$p", regScript.contains("-LiteralPath \$p"))
    ok(
        "带引号 + 空格的值原样进脚本",
        regScript.contains("""'"C:\Program Files\X\Uninstall.exe" /S /KEEPDATA'"""),
    )
    ok("DWORD 走 -PropertyType DWord", regScript.contains("-PropertyType DWord"))
    ok("字符串走 -PropertyType String", regScript.contains("-PropertyType String"))
    eq("单引号转义成两个", "'it''s'", WinReg.psQuote("it's"))
    ok("脚本以 exit 0 结尾", regScript.endsWith("exit 0"))

    // ---------------- 磁盘空间 ----------------

    println("InstallEngine · 磁盘空间")
    val tmpDir = File(System.getProperty("java.io.tmpdir"))
    ok("requiredMb 留了余量（160MB → 约 204MB）", InstallEngine.requiredMb(InstallerConfig(estimatedSizeMb = 160)) == 204L)
    eq("需求为 0 时不拦", null, InstallEngine.diskSpace(tmpDir, 0))
    val plenty = InstallEngine.diskSpace(tmpDir, 1)
    ok("要 1 MB 肯定够", plenty == null || plenty.ok, "实际 $plenty")
    val impossible = InstallEngine.diskSpace(tmpDir, 9_000_000_000L)
    ok("要 9 TB 一定不够", impossible != null && !impossible.ok, "实际 $impossible")
    ok(
        "不存在的目录会往上找祖先",
        InstallEngine.diskSpace(File(tmpDir, "kit-verify-not-exist/deeper"), 1) != null,
    )

    // ---------------- 覆盖安装检测 ----------------

    println("InstallEngine · 覆盖安装检测")
    val noSuchProduct = InstallEngine(InstallerConfig(appId = "InstallerKitNoSuchProduct"))
    eq("没装过的产品 -> null", null, noSuchProduct.detectExisting())

    // ---------------- 注册表：真写一次再读回来 ----------------
    // 这一段刻意带副作用（在 HKCU 下建一个探针键，finally 里删掉）：
    // 它覆盖的是「PowerShell -EncodedCommand 写注册表」那条路 ——
    // 也就是把 reg.exe 静默失败那个坑修掉之后的实现。

    println("WinReg · 真写真读")
    val probeKey = "HKCU\\Software\\InstallerKitSelfCheck"
    val probeReg = WinReg(WinPaths(InstallerConfig(appId = "InstallerKitSelfCheck")))
    val probeDir = File(tmpDir, "kit verify probe")   // 故意带空格
    val trickyValue = "\"C:\\Program Files\\X\\y.exe\" /S /KEEPDATA"
    try {
        val written = probeReg.writeValues(
            probeKey,
            listOf(
                RegValue("InstallPath", probeDir.absolutePath),
                RegValue("Version", "9.9.9"),
                RegValue("Tricky", trickyValue),
                RegValue("Count", "7", RegValue.REG_DWORD),
            ),
        )
        ok("写入成功（含空格 / 引号 / 中文键名的值）", written)
        eq("读回 InstallPath", probeDir.absolutePath, probeReg.query(probeKey, "InstallPath"))
        eq("读回 Version", "9.9.9", probeReg.query(probeKey, "Version"))
        eq("读回「空格 + 引号」的值（reg.exe 那条路会静默丢值）", trickyValue, probeReg.query(probeKey, "Tricky"))
        eq("不存在的值 -> null", null, probeReg.query(probeKey, "NoSuchValue"))
    } finally {
        WinExec.run("reg", "delete", probeKey, "/f")
    }

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
