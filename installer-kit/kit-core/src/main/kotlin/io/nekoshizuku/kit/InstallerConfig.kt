package io.nekoshizuku.kit

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 数字签名（打包期用，调 [ShizukuCodeSign](file:///D:/sing/codesign)）。
 *
 * 关键点：**先签里面的，再签外面的**。
 * 单文件安装包是由「发行目录 → payload.zip → IExpress」压出来的，
 * 所以顺序必须是
 *
 * 1. 组装发行目录
 * 2. 给发行目录里的可执行文件签名（这样解压出来的文件自带签名）
 * 3. 压 payload.zip
 * 4. IExpress 封成单个 Setup.exe
 * 5. 给这个 Setup.exe 签名
 *
 * 反过来的话，签名会被后面一步覆盖掉。
 */
@Serializable
data class CodeSignConfig(
    /** 开关。默认关，免得没有证书的机器上打不了包 */
    val enabled: Boolean = false,
    /** ShizukuCodeSign 工具目录（相对路径以配置文件为基准） */
    val toolsDir: String = "",
    /** 证书档案名 */
    val profile: String = "demo",
    /** 加时间戳（需要联网） */
    val timestamp: Boolean = false,
    /** 给最终的单个 Setup.exe 签名 */
    val signInstaller: Boolean = true,
    /** 给发行目录里的文件签名（解压出来的那份） */
    val signStageFiles: Boolean = true,
    /** 发行目录里要签哪些文件（相对路径 glob） */
    val includeGlobs: List<String> = listOf("*.exe"),
    /** 签完立刻验一遍 */
    val verify: Boolean = true,
)

/**
 * 产品配置 —— 整个工具**唯一的事实来源**。
 *
 * 用法（打包期注入，运行期读取）：
 * 1. 你写一份 `installer.config.json`（可以照抄 installer.config.example.json）；
 * 2. `tools\build-installer.ps1` 读它、补全衍生字段（[licenseText] / [wizardMainClass] /
 *    [wizardClasspath]），写进 app-image 的 `app\installer.config.json`；
 * 3. 安装程序运行时从自己所在的 app-image 里读回来。
 *
 * 所以「换产品只改配置不改代码」。
 */
@Serializable
data class InstallerConfig(
    // ---- 产品身份（注册表 / 快捷方式 / 向导标题都用这几个）----
    val productName: String = "MyApp",
    val appId: String = "MyApp",
    val version: String = "1.0.0",
    val publisher: String = "",
    val copyright: String = "",

    // ---- 外观 ----
    /** 打包用的 exe 图标（.ico），只在打包期用得到 */
    val iconFile: String = "",
    /** 窗口/任务栏用的 logo（.png），只在打包期用得到 */
    val logoFile: String = "",
    /** 要生成的窗口图标尺寸，和程序里 WINDOW_ICON_SIZES 对应 */
    val windowIconSizes: List<Int> = listOf(16, 20, 24, 32, 40, 48, 64, 96, 128, 256),
    /** 窗口图标的来源目录：里面放 `app-icon-<尺寸>.png`，打包期复制进 app-image */
    val windowIconDir: String = "",
    /** 向导的品牌主色（`#RRGGBB`）；留空 = 默认蓝 */
    val brandColor: String = "",

    // ---- 界面文案 ----
    /**
     * 文案覆盖，形如 `{ "zh": { "welcome.title": "…" }, "en": { … } }`。
     * 没写的 key 用 kit-ui 内置文案。
     */
    val i18n: Map<String, Map<String, String>> = emptyMap(),

    // ---- 许可协议 / 链接 ----
    /** 许可协议原文件（.txt/.md），打包期会被读进 [licenseText] */
    val licenseFile: String = "",
    /** 许可协议正文。打包期由 [licenseFile] 填充；留空则向导显示「未提供」 */
    val licenseText: String = "",
    val urlInfoAbout: String = "",
    val helpLink: String = "",

    // ---- 要打包的主程序 ----
    /**
     * 打包模式。留空 = 自动判断。
     *
     * - `"jpackage"`：产品是 jpackage 生成的 app-image（自带 `runtime\`）。
     *   安装器塞进它自带的运行时里，**安装包体积 ≈ 产品体积**，界面是那套 Compose 向导。
     * - `"portable"`：产品就是个普通文件夹（一个 exe + 一堆文件，也就是「绿色软件」）。
     *   用系统自带的 .NET 跑一个轻量向导，**安装包只比原程序大几十 KB**；
     *   代价是界面朴素一点，卸载走安装目录里的 `Uninstall.ps1`。
     */
    val installMode: String = "",

    /**
     * 便携模式（[installMode] = `"portable"`）用哪种安装向导。
     *
     * - `"lite"`（默认）：系统自带的 .NET/WinForms 小向导。安装包只比原程序大几十 KB。
     * - `"full"`：**和 jpackage 模式一模一样的那套 Compose 向导**（外观、文案、许可页全都一样），
     *   代价是安装包要多背约 57MB 运行时 —— 给 0.5MB 的绿色软件套它是 100 倍体积，
     *   所以默认不这么干，但你要一致的外观就选它。
     */
    val portableWizard: String = "lite",
    /** 打包期用：Gradle 工程根目录（留空 = 配置文件所在目录），相对路径以配置文件为基准 */
    val buildRoot: String = "",
    /** 打包期用：构建产品的 Gradle 任务，例如 `:samples:demo-app:createDistributable`；留空 = 不构建 */
    val buildTask: String = "",
    /** 主程序 app-image 目录（相对路径以配置文件所在目录为基准） */
    val mainAppDir: String = "",
    /** app-image 根目录里那个 exe 的文件名；留空 = `<appId>.exe` */
    val mainExeName: String = "",
    /** 安装目录名（默认装到 %LOCALAPPDATA%\Programs\<这个名字>）；留空 = [appId] */
    val installDirName: String = "",
    /** 用户数据目录名（%APPDATA%\<这个名字>）；留空 = [appId] */
    val dataDirName: String = "",
    /** 「所需空间」提示用，单位 MB */
    val estimatedSizeMb: Int = 160,

    // ---- 数字签名 ----
    val codeSign: CodeSignConfig = CodeSignConfig(),

    // ---- 向导默认勾选状态 ----
    val defaultPerUser: Boolean = true,
    /** 是否允许用户改安装范围；false = 锁死 [defaultPerUser] */
    val allowScopeChoice: Boolean = true,
    val defaultDesktopShortcut: Boolean = true,
    val defaultStartMenu: Boolean = true,
    val launchAfterInstall: Boolean = true,

    // ---- 卸载行为 ----
    val uninstallKeepDataByDefault: Boolean = true,
    val killRunningAppOnUninstall: Boolean = true,

    // ---- 下面这些由打包脚本填，手写没有意义 ----

    /**
     * 安装/卸载向导的入口类。
     *
     * 原理：jpackage 启动器按「和自己同名的 .cfg」读配置，而 cfg 里的
     * `app.mainclass` 决定入口。所以我们把 Setup.cfg / Uninstall.cfg 的
     * mainclass 指向 kit 的向导，产品自己的 .cfg 一个字不动 —— 同一个 exe、
     * 同一份运行时，就多出了安装/卸载两种身份。
     */
    val wizardMainClass: String = "io.nekoshizuku.kit.app.MainKt",

    /** 向导需要、但产品 app-image 里可能没有的 classpath 条目（形如 `$APPDIR\xxx.jar`） */
    val wizardClasspath: List<String> = emptyList(),
) {
    /** app-image 根目录里那个主程序 exe 的文件名 */
    val effectiveExeName: String get() = mainExeName.ifBlank { "$appId.exe" }

    val effectiveInstallDirName: String get() = installDirName.ifBlank { appId }

    val effectiveDataDirName: String get() = dataDirName.ifBlank { appId }

    val effectiveVersion: String get() = version.ifBlank { "1.0.0" }

    /** 是否能在向导里显示许可协议 */
    val hasLicense: Boolean get() = licenseText.isNotBlank()

    /** 便携模式：产品是普通文件夹，安装时复制的是 `app\payload\`，卸载走 PowerShell 脚本 */
    val isPortable: Boolean get() = installMode.equals("portable", ignoreCase = true)

    /** 便携模式下用不用那套 Compose 向导 */
    val useFullWizardInPortable: Boolean get() = portableWizard.equals("full", ignoreCase = true)
}

/** 配置的读取 / 序列化 */
object InstallerConfigs {

    /** 注入进 app-image 的文件名，一律放在 `app\` 下 */
    const val FILE_NAME = "installer.config.json"

    val json: Json = Json {
        ignoreUnknownKeys = true // 允许 _说明 这类注释键
        isLenient = true
        prettyPrint = true
        // ⚠️ 必须开：不开的话「等于默认值的字段」在序列化时会被**直接丢掉**。
        // 典型受害者是 version —— 它的默认值就是 "1.0.0"，所以一份 version 为 1.0.0 的配置
        // 存出来会**没有 version 字段**。我们自己的读取端有默认值兜底没事，但
        // 任何按字面读这个 JSON 的东西（比如 tools\verify-install.ps1 拼产物文件名）就会踩空。
        encodeDefaults = true
        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        prettyPrintIndent = "  "
    }

    fun parse(text: String): InstallerConfig =
        json.decodeFromString(InstallerConfig.serializer(), text)

    fun load(file: File): InstallerConfig = parse(file.readText(Charsets.UTF_8))

    fun encode(config: InstallerConfig): String =
        json.encodeToString(InstallerConfig.serializer(), config)

    /**
     * 运行时读取：`<appImageDir>\app\installer.config.json`。
     * 读不到（比如直接双击跑 kit 自己的壳）就退回内置默认值。
     */
    fun loadForAppImage(appImageDir: File?): Loaded {
        val file = appImageDir?.let { File(File(it, "app"), FILE_NAME) }
        return if (file != null && file.isFile) {
            runCatching { Loaded(load(file), file) }
                .getOrElse { Loaded(InstallerConfig(), null, it) }
        } else {
            Loaded(InstallerConfig(), null, null)
        }
    }

    data class Loaded(
        val config: InstallerConfig,
        val file: File?,
        val error: Throwable? = null,
    ) {
        /** 用的是内置默认值（没找到注入的配置） */
        val isFallback: Boolean get() = file == null
    }
}
