package io.nekoshizuku.kit.ui.i18n

import java.util.Locale

/** 界面语言 */
enum class Lang(val code: String, val label: String) {
    ZH("zh", "简体中文"),
    EN("en", "English"),
    ;

    companion object {
        /**
         * 决定用哪种语言：命令行 `--lang=` > 系统语言 > 英文。
         * 只看第一位（`zh-CN` / `zh-Hans` 都算中文）。
         */
        fun detect(explicit: String? = null): Lang {
            explicit?.trim()?.takeIf { it.isNotEmpty() }?.let { want ->
                entries.firstOrNull { it.code.equals(want, true) }?.let { return it }
                if (want.startsWith("zh", true)) return ZH
                if (want.startsWith("en", true)) return EN
            }
            val tag = Locale.getDefault().language.lowercase(Locale.ROOT)
            return if (tag.startsWith("zh")) ZH else EN
        }
    }
}

/**
 * 向导文案。
 *
 * 查找顺序：**配置里的覆盖** → **内置文案** → `«key»`。
 * 最后那个 `«key»` 是故意的：漏翻译的时候界面上会直接露出 key，一眼就能发现，
 * 而不是静默显示空白。
 *
 * 配置覆盖就是 `installer.config.json` 里那段 `i18n`：
 *
 * ```json
 * { "i18n": { "zh": { "welcome.title": "欢迎使用 {productName} 安装向导" } } }
 * ```
 */
class Strings(
    val lang: Lang,
    private val overrides: Map<String, Map<String, String>> = emptyMap(),
    private val vars: Map<String, String> = emptyMap(),
    /**
     * 便携模式：产品是绿色软件（一个 exe + 一堆文件），没有自带 Java 运行时。
     * 欢迎页那条「程序自带 Java 运行时」就不该出现 —— 得换成人话。
     */
    private val portable: Boolean = false,
) {

    /** 取一条文案，并替换掉 `{xxx}` 占位符 */
    operator fun get(key: String): String {
        val text = overrides[lang.code]?.get(key) ?: builtin(key) ?: "«$key»"
        if (vars.isEmpty() || '{' !in text) return text
        var out = text
        vars.forEach { (k, v) -> out = out.replace("{$k}", v) }
        return out
    }

    /** 内置文案；个别条目要按打包模式分岔 */
    private fun builtin(key: String): String? {
        if (portable) {
            when (key) {
                "welcome.bullet1" -> return when (lang) {
                    Lang.ZH -> "程序是绿色版，装完即可用，不依赖任何运行库"
                    Lang.EN -> "Portable app — ready to use, no runtime libraries required"
                }
                "welcome.bullet2" -> return when (lang) {
                    Lang.ZH -> "安装过程不需要联网"
                    Lang.EN -> "No internet connection needed during setup"
                }
            }
        }
        return BUILTIN[lang]?.get(key)
    }

    // ---------------- 按钮 ----------------

    val next get() = this["btn.next"]
    val back get() = this["btn.back"]
    val cancel get() = this["btn.cancel"]
    val install get() = this["btn.install"]
    val finish get() = this["btn.finish"]
    val close get() = this["btn.close"]
    val browse get() = this["btn.browse"]
    val startUninstall get() = this["btn.uninstall"]
    val startRepair get() = this["btn.repair"]

    fun stepOf(step: Int, total: Int) =
        this["common.stepOf"].replace("{step}", step.toString()).replace("{total}", total.toString())

    // ---------------- 步骤名 ----------------

    val stepWelcome get() = this["step.welcome"]
    val stepLicense get() = this["step.license"]
    val stepExisting get() = this["step.existing"]
    val stepScope get() = this["step.scope"]
    val stepPath get() = this["step.path"]
    val stepOptions get() = this["step.options"]
    val stepInstalling get() = this["step.installing"]
    val stepFinish get() = this["step.finish"]

    // ---------------- 欢迎 ----------------

    val welcomeTitle get() = this["welcome.title"]
    val welcomeIntro get() = this["welcome.intro"]
    val welcomePublisher get() = this["welcome.publisher"]
    val welcomeCheckTitle get() = this["welcome.checkTitle"]
    val welcomeBullets get() = listOf(this["welcome.bullet1"], this["welcome.bullet2"], this["welcome.bullet3"])

    // ---------------- 许可协议 ----------------

    val licenseTitle get() = this["license.title"]
    val licenseAccept get() = this["license.accept"]
    val licenseNone get() = this["license.none"]

    // ---------------- 已经装过 ----------------

    val existingTitle get() = this["existing.title"]
    val existingIntro get() = this["existing.intro"]
    val existingUpgrade get() = this["existing.upgrade"]
    val existingUpgradeDesc get() = this["existing.upgradeDesc"]
    val existingFresh get() = this["existing.fresh"]
    val existingFreshDesc get() = this["existing.freshDesc"]
    val existingHint get() = this["existing.hint"]

    // ---------------- 安装范围 ----------------

    val scopeTitle get() = this["scope.title"]
    val scopePerUser get() = this["scope.perUser"]
    val scopePerUserDesc get() = this["scope.perUserDesc"]
    val scopeAll get() = this["scope.all"]
    val scopeAllDesc get() = this["scope.allDesc"]
    val scopeAllDescAdmin get() = this["scope.allDescAdmin"]
    val scopeAllDescUac get() = this["scope.allDescUac"]
    val scopeIsAdmin get() = this["scope.isAdmin"]
    val scopeNotAdmin get() = this["scope.notAdmin"]

    // ---------------- 安装位置 ----------------

    val pathTitle get() = this["path.title"]
    val pathSpace get() = this["path.space"]
    val pathSpaceValue get() = this["path.spaceValue"]
    val pathInstallDir get() = this["path.installDir"]
    val pathDataDir get() = this["path.dataDir"]
    val pathDataDirValue get() = this["path.dataDirValue"]
    val pathHint get() = this["path.hint"]
    val pathErrEmpty get() = this["path.errEmpty"]
    val pathErrRoot get() = this["path.errRoot"]
    val pathErrIllegal get() = this["path.errIllegal"]
    val pathErrGeneric get() = this["path.errGeneric"]
    val pathSpaceAvailable get() = this["path.spaceAvailable"]
    val pathSpaceAvailableValue get() = this["path.spaceAvailableValue"]
    val pathSpaceNotEnough get() = this["path.spaceNotEnough"]
    val pathWillUpgrade get() = this["path.willUpgrade"]

    // ---------------- 附加任务 ----------------

    val optionsTitle get() = this["options.title"]
    val optionsDesktop get() = this["options.desktop"]
    val optionsStartMenu get() = this["options.startMenu"]
    val optionsAfterTitle get() = this["options.afterTitle"]
    val optionsLaunch get() = this["options.launch"]
    val optionsHint get() = this["options.hint"]

    // ---------------- 进度 / 完成 ----------------

    val progressTitle get() = this["progress.title"]
    val progressErrorTitle get() = this["progress.errorTitle"]
    val progressPreparing get() = this["progress.preparing"]

    val finishTitle get() = this["finish.title"]
    val finishIntro get() = this["finish.intro"]
    val finishInstallDir get() = this["finish.installDir"]
    val finishUninstaller get() = this["finish.uninstaller"]
    val finishDataDir get() = this["finish.dataDir"]
    val finishLaunchHint get() = this["finish.launchHint"]
    val finishCloseHint get() = this["finish.closeHint"]

    // ---------------- 卸载 ----------------

    val uninstallTitle get() = this["uninstall.title"]
    val uninstallRepairTitle get() = this["uninstall.repairTitle"]
    val uninstallInstallDir get() = this["uninstall.installDir"]
    val uninstallDataDir get() = this["uninstall.dataDir"]
    val uninstallKeepData get() = this["uninstall.keepData"]
    val uninstallKeepDataDesc get() = this["uninstall.keepDataDesc"]
    val uninstallRemoveAll get() = this["uninstall.removeAll"]
    val uninstallRemoveAllDesc get() = this["uninstall.removeAllDesc"]
    val uninstallRepair get() = this["uninstall.repair"]
    val uninstallRepairDesc get() = this["uninstall.repairDesc"]
    val uninstallWorking get() = this["uninstall.working"]
    val uninstallDone get() = this["uninstall.done"]
    val uninstallDoneRepair get() = this["uninstall.doneRepair"]
    val uninstallDataKept get() = this["uninstall.dataKept"]
    val uninstallDirHint get() = this["uninstall.dirHint"]

    // ---------------- 诊断页 ----------------

    val diagTitle get() = this["diag.title"]
    val diagHint get() = this["diag.hint"]
    val diagProductName get() = this["diag.productName"]
    val diagAppId get() = this["diag.appId"]
    val diagVersion get() = this["diag.version"]
    val diagPublisher get() = this["diag.publisher"]
    val diagMainExe get() = this["diag.mainExe"]
    val diagInstallDirName get() = this["diag.installDirName"]
    val diagDataDirName get() = this["diag.dataDirName"]
    val diagSelfExe get() = this["diag.selfExe"]
    val diagAppImage get() = this["diag.appImage"]
    val diagConfigFile get() = this["diag.configFile"]
    val diagConfigFallback get() = this["diag.configFallback"]
    val diagWizardClass get() = this["diag.wizardClass"]
    val diagClasspath get() = this["diag.classpath"]
    val diagArgs get() = this["diag.args"]
    val diagLang get() = this["diag.lang"]
    val diagConfigError get() = this["diag.configError"]
    val diagNoImage get() = this["diag.noImage"]
    val diagDash get() = this["diag.dash"]

    companion object {

        private val ZH = mapOf(
            // 按钮
            "btn.next" to "下一步",
            "btn.back" to "上一步",
            "btn.cancel" to "取消",
            "btn.install" to "安装",
            "btn.finish" to "完成",
            "btn.close" to "关闭",
            "btn.browse" to "浏览…",
            "btn.uninstall" to "开始卸载",
            "btn.repair" to "开始修复",
            "common.stepOf" to "第 {step} 步 / 共 {total} 步",

            // 步骤名
            "step.welcome" to "欢迎",
            "step.license" to "许可协议",
            "step.existing" to "已经装过",
            "step.scope" to "安装范围",
            "step.path" to "安装位置",
            "step.options" to "附加任务",
            "step.installing" to "正在安装",
            "step.finish" to "完成",

            // 欢迎
            "welcome.title" to "欢迎使用 {productName} 安装向导",
            "welcome.intro" to "这个向导会把「{productName}」装到这台电脑上。",
            "welcome.publisher" to "发布者：{publisher}",
            "welcome.checkTitle" to "安装前请确认：",
            "welcome.bullet1" to "程序自带 Java 运行时，电脑上不用另外装 Java",
            "welcome.bullet2" to "安装过程不需要联网，程序运行时才需要",
            "welcome.bullet3" to "用户数据放在 {dataDir}，卸载时可以选择保留",

            // 许可协议
            "license.title" to "许可协议",
            "license.accept" to "我接受以上许可协议",
            "license.none" to "（本产品没有附带许可协议）",

            // 已经装过
            "existing.title" to "这台电脑上已经装过 {productName}",
            "existing.intro" to "检测到一份已安装的：{oldVersion}，位置 {oldDir}",
            "existing.upgrade" to "覆盖升级到原位置（推荐）",
            "existing.upgradeDesc" to "装到 {oldDir}，用户数据（{dataDir}）保持不动",
            "existing.fresh" to "换个目录重新装一份",
            "existing.freshDesc" to "旧的那份原样留着，按我下面选的目录再装一份新的",
            "existing.hint" to "想先卸干净的话，运行旧位置里的 Uninstall.exe 就行。",

            // 安装范围
            "scope.title" to "选择安装范围",
            "scope.perUser" to "仅为我安装（推荐）",
            "scope.perUserDesc" to "装到 {path}，不需要管理员权限",
            "scope.all" to "为这台电脑上的所有用户安装",
            "scope.allDesc" to "装到 {path}",
            "scope.allDescAdmin" to "装到 {path}（需要管理员权限）",
            "scope.allDescUac" to "装到 {path}；下一步会弹 UAC 提权",
            "scope.isAdmin" to "当前以管理员身份运行。",
            "scope.notAdmin" to "当前不是管理员身份。",

            // 安装位置
            "path.title" to "选择安装位置",
            "path.space" to "所需空间",
            "path.spaceValue" to "约 {sizeMb} MB",
            "path.installDir" to "将安装到",
            "path.dataDir" to "用户数据",
            "path.dataDirValue" to "{dataDir}（不受影响）",
            "path.hint" to "提示：装到系统盘以外的位置也完全可以，程序不会往安装目录写用户数据。",
            "path.errEmpty" to "安装目录不能为空",
            "path.errRoot" to "请选一个具体的目录，不能直接装到盘符根目录",
            "path.errIllegal" to "目录名里有非法字符：{name}",
            "path.errGeneric" to "请选一个具体的目录",
            "path.spaceAvailable" to "可用空间",
            "path.spaceAvailableValue" to "{availableMb} MB",
            "path.spaceNotEnough" to "空间不够：这个盘只剩 {availableMb} MB，装下需要约 {requiredMb} MB",
            "path.willUpgrade" to "将覆盖升级：v{oldVersion} → v{newVersion}（用户数据不动）",

            // 附加任务
            "options.title" to "附加任务",
            "options.desktop" to "创建桌面快捷方式",
            "options.startMenu" to "创建开始菜单项",
            "options.afterTitle" to "安装完成后",
            "options.launch" to "立即启动",
            "options.hint" to "卸载程序会一起装到安装目录（Uninstall.exe），随时可以在\n" +
                "「设置 → 应用」里卸载，也可以用开始菜单里的卸载入口。\n" +
                "卸载支持：保留数据 / 彻底删除 / 修复安装。",

            // 进度 / 完成
            "progress.title" to "正在安装",
            "progress.errorTitle" to "出错了",
            "progress.preparing" to "准备安装…",
            "finish.title" to "安装完成喵~",
            "finish.intro" to "「{productName}」已经装好了。",
            "finish.installDir" to "安装位置",
            "finish.uninstaller" to "卸载程序",
            "finish.dataDir" to "数据目录",
            "finish.launchHint" to "点「完成」会自动启动程序。",
            "finish.closeHint" to "点「完成」关闭安装向导。",

            // 卸载
            "uninstall.title" to "卸载 {productName}",
            "uninstall.repairTitle" to "修复 {productName}",
            "uninstall.installDir" to "安装位置",
            "uninstall.dataDir" to "数据目录",
            "uninstall.keepData" to "卸载（保留账号数据）",
            "uninstall.keepDataDesc" to "只删程序；账号 / 设置 / 日志留在 {dataDir}",
            "uninstall.removeAll" to "彻底卸载（同时删除数据）",
            "uninstall.removeAllDesc" to "程序 + 账号 + 设置 + 日志全部删除，不可恢复",
            "uninstall.repair" to "修复安装",
            "uninstall.repairDesc" to "重建快捷方式和注册表项，账号数据不动",
            "uninstall.working" to "准备…",
            "uninstall.done" to "卸载完成，谢谢使用喵~",
            "uninstall.doneRepair" to "修复完成喵~",
            "uninstall.dataKept" to "账号数据还留在 {dataDir}",
            "uninstall.dirHint" to "安装目录会在几秒内自动删除干净。",

            // 诊断
            "diag.title" to "InstallerKit · 诊断",
            "diag.hint" to "当前不是安装/卸载模式。安装请运行 Setup.exe（或加 --install）。",
            "diag.productName" to "产品名",
            "diag.appId" to "AppId",
            "diag.version" to "版本",
            "diag.publisher" to "发布者",
            "diag.mainExe" to "主程序 exe",
            "diag.installDirName" to "安装目录名",
            "diag.dataDirName" to "数据目录名",
            "diag.selfExe" to "自身 exe",
            "diag.appImage" to "app-image",
            "diag.configFile" to "配置文件",
            "diag.configFallback" to "（没找到注入的 installer.config.json，用的内置默认值）",
            "diag.wizardClass" to "向导入口类",
            "diag.classpath" to "向导 classpath",
            "diag.args" to "启动参数",
            "diag.lang" to "界面语言",
            "diag.configError" to "配置读取失败，已退回默认值：{error}",
            "diag.noImage" to "（不是从 app-image 启动）",
            "diag.dash" to "—",
        )

        private val EN = mapOf(
            "btn.next" to "Next",
            "btn.back" to "Back",
            "btn.cancel" to "Cancel",
            "btn.install" to "Install",
            "btn.finish" to "Finish",
            "btn.close" to "Close",
            "btn.browse" to "Browse…",
            "btn.uninstall" to "Uninstall",
            "btn.repair" to "Repair",
            "common.stepOf" to "Step {step} of {total}",

            "step.welcome" to "Welcome",
            "step.license" to "License",
            "step.existing" to "Installed",
            "step.scope" to "Scope",
            "step.path" to "Location",
            "step.options" to "Tasks",
            "step.installing" to "Installing",
            "step.finish" to "Done",

            "welcome.title" to "Welcome to the {productName} Setup Wizard",
            "welcome.intro" to "This wizard will install {productName} on this computer.",
            "welcome.publisher" to "Publisher: {publisher}",
            "welcome.checkTitle" to "Before you continue:",
            "welcome.bullet1" to "A Java runtime is bundled — no separate Java install needed",
            "welcome.bullet2" to "Setup does not need the network; the app does",
            "welcome.bullet3" to "User data lives in {dataDir} and can be kept when uninstalling",

            "license.title" to "License Agreement",
            "license.accept" to "I accept the terms of this agreement",
            "license.none" to "(no license text was bundled with this product)",

            "existing.title" to "{productName} is already installed",
            "existing.intro" to "Found an existing installation: {oldVersion} at {oldDir}",
            "existing.upgrade" to "Upgrade it in place (recommended)",
            "existing.upgradeDesc" to "Installs into {oldDir}; your data in {dataDir} stays untouched",
            "existing.fresh" to "Install a fresh copy elsewhere",
            "existing.freshDesc" to "Leaves the old copy alone and installs into the folder I pick below",
            "existing.hint" to "To remove the old copy first, run Uninstall.exe from its folder.",

            "scope.title" to "Choose install scope",
            "scope.perUser" to "Install for me only (recommended)",
            "scope.perUserDesc" to "Installs to {path}; no administrator rights needed",
            "scope.all" to "Install for all users on this computer",
            "scope.allDesc" to "Installs to {path}",
            "scope.allDescAdmin" to "Installs to {path} (administrator rights required)",
            "scope.allDescUac" to "Installs to {path}; a UAC prompt will appear next",
            "scope.isAdmin" to "Currently running as administrator.",
            "scope.notAdmin" to "Currently not running as administrator.",

            "path.title" to "Choose install location",
            "path.space" to "Space required",
            "path.spaceValue" to "about {sizeMb} MB",
            "path.installDir" to "Will install to",
            "path.dataDir" to "User data",
            "path.dataDirValue" to "{dataDir} (not affected)",
            "path.hint" to "Tip: installing outside the system drive is fine — user data never goes into the install folder.",
            "path.errEmpty" to "Install folder cannot be empty",
            "path.errRoot" to "Please pick a real folder, not a drive root",
            "path.errIllegal" to "Folder name contains illegal characters: {name}",
            "path.errGeneric" to "Please pick a real folder",
            "path.spaceAvailable" to "Free space",
            "path.spaceAvailableValue" to "{availableMb} MB",
            "path.spaceNotEnough" to "Not enough space: only {availableMb} MB left, about {requiredMb} MB needed",
            "path.willUpgrade" to "Will upgrade in place: v{oldVersion} → v{newVersion} (data untouched)",

            "options.title" to "Additional tasks",
            "options.desktop" to "Create a desktop shortcut",
            "options.startMenu" to "Create a Start menu entry",
            "options.afterTitle" to "When setup finishes",
            "options.launch" to "Launch the application",
            "options.hint" to "An uninstaller (Uninstall.exe) is installed into the same folder.\n" +
                "You can also remove the app from Settings → Apps, or from the Start menu.\n" +
                "Uninstall options: keep data / remove everything / repair.",

            "progress.title" to "Installing",
            "progress.errorTitle" to "Something went wrong",
            "progress.preparing" to "Getting ready…",
            "finish.title" to "Setup complete!",
            "finish.intro" to "{productName} has been installed.",
            "finish.installDir" to "Install folder",
            "finish.uninstaller" to "Uninstaller",
            "finish.dataDir" to "Data folder",
            "finish.launchHint" to "Click Finish to launch the application.",
            "finish.closeHint" to "Click Finish to close this wizard.",

            "uninstall.title" to "Uninstall {productName}",
            "uninstall.repairTitle" to "Repair {productName}",
            "uninstall.installDir" to "Install folder",
            "uninstall.dataDir" to "Data folder",
            "uninstall.keepData" to "Uninstall (keep my data)",
            "uninstall.keepDataDesc" to "Removes the program only; data stays in {dataDir}",
            "uninstall.removeAll" to "Uninstall everything (delete data too)",
            "uninstall.removeAllDesc" to "Program, settings and logs are all deleted — cannot be undone",
            "uninstall.repair" to "Repair installation",
            "uninstall.repairDesc" to "Rebuilds shortcuts and registry entries; data is untouched",
            "uninstall.working" to "Working…",
            "uninstall.done" to "Uninstalled. Thanks for using it!",
            "uninstall.doneRepair" to "Repair complete!",
            "uninstall.dataKept" to "Your data is still in {dataDir}",
            "uninstall.dirHint" to "The install folder will be removed within a few seconds.",

            "diag.title" to "InstallerKit · Diagnostics",
            "diag.hint" to "Not in install/uninstall mode. Run Setup.exe (or pass --install) to install.",
            "diag.productName" to "Product",
            "diag.appId" to "AppId",
            "diag.version" to "Version",
            "diag.publisher" to "Publisher",
            "diag.mainExe" to "Launcher exe",
            "diag.installDirName" to "Install dir name",
            "diag.dataDirName" to "Data dir name",
            "diag.selfExe" to "This exe",
            "diag.appImage" to "app-image",
            "diag.configFile" to "Config file",
            "diag.configFallback" to "(no injected installer.config.json — using built-in defaults)",
            "diag.wizardClass" to "Wizard entry class",
            "diag.classpath" to "Wizard classpath",
            "diag.args" to "Arguments",
            "diag.lang" to "Language",
            "diag.configError" to "Failed to read config, fell back to defaults: {error}",
            "diag.noImage" to "(not started from an app-image)",
            "diag.dash" to "—",
        )

        private val BUILTIN: Map<Lang, Map<String, String>> = mapOf(Lang.ZH to ZH, Lang.EN to EN)

        /** 内置文案里所有 key（自检用：保证两种语言 key 一致） */
        fun builtinKeys(lang: Lang): Set<String> = BUILTIN[lang]?.keys ?: emptySet()

        fun builtin(lang: Lang, key: String): String? = BUILTIN[lang]?.get(key)
    }
}
