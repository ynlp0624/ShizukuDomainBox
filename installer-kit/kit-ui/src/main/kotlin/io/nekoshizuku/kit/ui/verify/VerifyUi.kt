package io.nekoshizuku.kit.ui.verify

import androidx.compose.ui.graphics.Color
import io.nekoshizuku.kit.ui.KitAssets
import io.nekoshizuku.kit.ui.KitBrand
import io.nekoshizuku.kit.ui.i18n.Lang
import io.nekoshizuku.kit.ui.i18n.Strings
import java.io.File

/**
 * kit-ui 的无头自检。
 *
 * 跑法：`gradle :kit-ui:verifyUi`
 *
 * 这里**刻意不碰任何 Compose 组合**（不建窗口、不加载 Skiko），
 * 只测「纯函数」：文案查表、品牌色解析、素材路径判断。
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
    println("=== kit-ui 自检 ===")

    // ---------------- 文案：两种语言的 key 必须一一对应 ----------------

    println("Strings · key 对齐")
    val zhKeys = Strings.builtinKeys(Lang.ZH)
    val enKeys = Strings.builtinKeys(Lang.EN)
    ok("中文文案非空", zhKeys.isNotEmpty(), "共 ${zhKeys.size} 条")
    ok("没有只有中文的 key", (zhKeys - enKeys).isEmpty(), (zhKeys - enKeys).toString())
    ok("没有只有英文的 key", (enKeys - zhKeys).isEmpty(), (enKeys - zhKeys).toString())

    // ---------------- 文案：查找顺序与占位符 ----------------

    println("Strings · 查找与替换")
    // 和 Wizard.rememberWizardContext 里的全局占位符保持一致
    val vars = mapOf(
        "productName" to "雫的演示盒",
        "version" to "1.0.0",
        "publisher" to "Nekoshizuku",
        "appId" to "KitDemoBox",
        "dataDir" to "C:\\data",
        "mainExe" to "KitDemoBox.exe",
    )
    val zh = Strings(Lang.ZH, vars = vars)
    val en = Strings(Lang.EN, vars = vars)

    // 便携模式（绿色软件）的欢迎页不该提「自带 Java 运行时」
    val zhPortable = Strings(Lang.ZH, vars = vars, portable = true)
    ok("便携模式欢迎页不提 Java", !zhPortable["welcome.bullet1"].contains("Java"), zhPortable["welcome.bullet1"])
    ok("便携模式文案是给人看的（不是 key）", !zhPortable["welcome.bullet1"].startsWith("«"))
    ok("非便携模式仍然提自带运行时", zh["welcome.bullet1"].contains("Java"), zh["welcome.bullet1"])
    eq("便携模式不覆盖其它 key", zh["welcome.title"], zhPortable["welcome.title"])

    ok("中文标题填好了产品名", zh.welcomeTitle.contains("雫的演示盒") && !zh.welcomeTitle.contains("{"))
    ok("英文标题填好了产品名", en.welcomeTitle.contains("雫的演示盒") && !en.welcomeTitle.contains("{"))
    eq("漏掉的 key 会显形", "«no.such.key»", zh["no.such.key"])

    val overridden = Strings(
        Lang.ZH,
        overrides = mapOf("zh" to mapOf("welcome.title" to "自定义标题：{productName}")),
        vars = vars,
    )
    eq("配置覆盖优先", "自定义标题：雫的演示盒", overridden.welcomeTitle)
    val enWithZhOverride = Strings(Lang.EN, overrides = mapOf("zh" to mapOf("welcome.title" to "自定义")))
    ok("中文覆盖不影响英文", !enWithZhOverride.welcomeTitle.contains("自定义"))

    // ---------------- 文案：所有 getter 都拿得到（防拼错 key） ----------------

    println("Strings · 全部 getter")
    // 手写清单：加了新 getter 记得补进来（这就是清单存在的意义）
    fun allOf(s: Strings): Map<String, String> = mapOf(
        "next" to s.next, "back" to s.back, "cancel" to s.cancel, "install" to s.install,
        "finish" to s.finish, "close" to s.close, "browse" to s.browse,
        "startUninstall" to s.startUninstall, "startRepair" to s.startRepair,
        "stepWelcome" to s.stepWelcome, "stepLicense" to s.stepLicense, "stepScope" to s.stepScope,
        "stepExisting" to s.stepExisting,
        "stepPath" to s.stepPath, "stepOptions" to s.stepOptions, "stepInstalling" to s.stepInstalling,
        "stepFinish" to s.stepFinish,
        "existingTitle" to s.existingTitle, "existingIntro" to s.existingIntro,
        "existingUpgrade" to s.existingUpgrade, "existingUpgradeDesc" to s.existingUpgradeDesc,
        "existingFresh" to s.existingFresh, "existingFreshDesc" to s.existingFreshDesc,
        "existingHint" to s.existingHint,
        "pathSpaceAvailable" to s.pathSpaceAvailable, "pathSpaceAvailableValue" to s.pathSpaceAvailableValue,
        "pathSpaceNotEnough" to s.pathSpaceNotEnough, "pathWillUpgrade" to s.pathWillUpgrade,
        "welcomeTitle" to s.welcomeTitle, "welcomeIntro" to s.welcomeIntro,
        "welcomePublisher" to s.welcomePublisher, "welcomeCheckTitle" to s.welcomeCheckTitle,
        "licenseTitle" to s.licenseTitle, "licenseAccept" to s.licenseAccept, "licenseNone" to s.licenseNone,
        "scopeTitle" to s.scopeTitle, "scopePerUser" to s.scopePerUser, "scopePerUserDesc" to s.scopePerUserDesc,
        "scopeAll" to s.scopeAll, "scopeAllDesc" to s.scopeAllDesc, "scopeAllDescAdmin" to s.scopeAllDescAdmin,
        "scopeAllDescUac" to s.scopeAllDescUac, "scopeIsAdmin" to s.scopeIsAdmin, "scopeNotAdmin" to s.scopeNotAdmin,
        "pathTitle" to s.pathTitle, "pathSpace" to s.pathSpace, "pathSpaceValue" to s.pathSpaceValue,
        "pathInstallDir" to s.pathInstallDir, "pathDataDir" to s.pathDataDir, "pathDataDirValue" to s.pathDataDirValue,
        "pathHint" to s.pathHint, "pathErrEmpty" to s.pathErrEmpty, "pathErrRoot" to s.pathErrRoot,
        "pathErrIllegal" to s.pathErrIllegal, "pathErrGeneric" to s.pathErrGeneric,
        "optionsTitle" to s.optionsTitle, "optionsDesktop" to s.optionsDesktop, "optionsStartMenu" to s.optionsStartMenu,
        "optionsAfterTitle" to s.optionsAfterTitle, "optionsLaunch" to s.optionsLaunch, "optionsHint" to s.optionsHint,
        "progressTitle" to s.progressTitle, "progressErrorTitle" to s.progressErrorTitle,
        "progressPreparing" to s.progressPreparing,
        "finishTitle" to s.finishTitle, "finishIntro" to s.finishIntro, "finishInstallDir" to s.finishInstallDir,
        "finishUninstaller" to s.finishUninstaller, "finishDataDir" to s.finishDataDir,
        "finishLaunchHint" to s.finishLaunchHint, "finishCloseHint" to s.finishCloseHint,
        "uninstallTitle" to s.uninstallTitle, "uninstallRepairTitle" to s.uninstallRepairTitle,
        "uninstallInstallDir" to s.uninstallInstallDir, "uninstallDataDir" to s.uninstallDataDir,
        "uninstallKeepData" to s.uninstallKeepData, "uninstallKeepDataDesc" to s.uninstallKeepDataDesc,
        "uninstallRemoveAll" to s.uninstallRemoveAll, "uninstallRemoveAllDesc" to s.uninstallRemoveAllDesc,
        "uninstallRepair" to s.uninstallRepair, "uninstallRepairDesc" to s.uninstallRepairDesc,
        "uninstallWorking" to s.uninstallWorking, "uninstallDone" to s.uninstallDone,
        "uninstallDoneRepair" to s.uninstallDoneRepair, "uninstallDataKept" to s.uninstallDataKept,
        "uninstallDirHint" to s.uninstallDirHint,
        "diagTitle" to s.diagTitle, "diagHint" to s.diagHint, "diagProductName" to s.diagProductName,
        "diagAppId" to s.diagAppId, "diagVersion" to s.diagVersion, "diagPublisher" to s.diagPublisher,
        "diagMainExe" to s.diagMainExe, "diagInstallDirName" to s.diagInstallDirName,
        "diagDataDirName" to s.diagDataDirName, "diagSelfExe" to s.diagSelfExe,
        "diagAppImage" to s.diagAppImage, "diagConfigFile" to s.diagConfigFile,
        "diagConfigFallback" to s.diagConfigFallback, "diagWizardClass" to s.diagWizardClass,
        "diagClasspath" to s.diagClasspath, "diagArgs" to s.diagArgs, "diagLang" to s.diagLang,
        "diagConfigError" to s.diagConfigError, "diagNoImage" to s.diagNoImage, "diagDash" to s.diagDash,
    ).let { it + mapOf("welcomeBullets" to s.welcomeBullets.joinToString("|")) }

    for (lang in Lang.entries) {
        val s = if (lang == Lang.ZH) zh else en
        val empty = allOf(s).filterValues { it.contains("«") || it.isBlank() }
        ok("${lang.code}：所有 getter 都有文案", empty.isEmpty(), empty.keys.toString())
    }

    // 这条是给「占位符写错/忘了填」兜底的：文案里允许出现的局部占位符就这几个，
    // 其余 `{xxx}` 如果在渲染后还留在字符串里，就说明填漏了。
    // （真踩过：welcome.bullet3 用了 {dataDir}，但全局 vars 里没有它，
    //   界面上就直愣愣显示「用户数据放在 {dataDir}」。）
    println("Strings · 占位符")
    val allowedLocals = setOf(
        "{path}", "{sizeMb}", "{name}", "{error}",
        "{oldDir}", "{oldVersion}", "{newVersion}", "{availableMb}", "{requiredMb}",
    )
    val placeholderRe = Regex("\\{[A-Za-z][A-Za-z0-9]*}")
    for (lang in Lang.entries) {
        val s = if (lang == Lang.ZH) zh else en
        val leftover = allOf(s).filterValues { text ->
            placeholderRe.findAll(text).any { it.value !in allowedLocals }
        }
        ok("${lang.code}：没有漏填的占位符", leftover.isEmpty(), leftover.toString())
    }

    // ---------------- 品牌色 ----------------

    println("KitBrand")
    eq("解析 #RRGGBB", Color(0xFF3E6FD9), KitBrand.parse("#3E6FD9"))
    eq("解析不带 #", Color(0xFF3E6FD9), KitBrand.parse("3E6FD9"))
    eq("解析 #AARRGGBB", Color(0x883E6FD9), KitBrand.parse("#883E6FD9"))
    eq("空串 -> null", null, KitBrand.parse(""))
    eq("null -> null", null, KitBrand.parse(null))
    eq("乱写 -> null", null, KitBrand.parse("不是颜色"))
    eq("长度不对 -> null", null, KitBrand.parse("#12345"))
    ok("darken 变暗", KitBrand.darken(Color(0xFF3E6FD9)).red < Color(0xFF3E6FD9).red)
    ok("tint 变亮", KitBrand.tint(Color(0xFF000000)).red > 0f)
    ok("相同产品名给出相同兜底色", KitBrand.fallbackFor("X") == KitBrand.fallbackFor("X"))

    // ---------------- 素材路径 ----------------

    println("KitAssets")
    val tmp = File(System.getProperty("java.io.tmpdir"), "kit-ui-verify-${System.nanoTime()}")
    val appDir = File(tmp, "app")
    try {
        appDir.mkdirs()
        eq("没注入时 logo 为 null", null, KitAssets.productLogoFile(tmp))
        eq("没注入时 icon 为 null", null, KitAssets.productIconFile(tmp, 64))
        ok("没注入时 hasProductIcons=false", !KitAssets.hasProductIcons(tmp))

        val logo = File(appDir, KitAssets.LOGO_FILE).apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val icon = File(appDir, "${KitAssets.ICON_PREFIX}64.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        eq("注入后能找到 logo", logo, KitAssets.productLogoFile(tmp))
        eq("注入后能找到 64 图标", icon, KitAssets.productIconFile(tmp, 64))
        ok("注入后 hasProductIcons=true", KitAssets.hasProductIcons(tmp))
        eq("appImageDir 为 null 时不炸", null, KitAssets.productLogoFile(null))
    } finally {
        runCatching { tmp.deleteRecursively() }
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
