package io.nekoshizuku.kit.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.nekoshizuku.kit.InstallerConfig
import io.nekoshizuku.kit.KitArgs
import io.nekoshizuku.kit.KitSelf
import io.nekoshizuku.kit.ui.i18n.Lang
import java.io.File

/**
 * 「普通模式」下显示的诊断页。
 *
 * 产品自己的 exe 走的是产品自己的 mainclass，根本不会到这儿；
 * 会走到这儿的是：直接双击 kit 的壳、或者传了别的参数。
 * 这一页存在的意义是**排障**——出问题时能一眼看出「配置到底读到了没有」。
 */
@Composable
fun DiagnosticsPage(
    config: InstallerConfig,
    configPath: String?,
    configError: Throwable?,
    args: KitArgs,
    appImageDir: File? = KitSelf.appImageDir,
    lang: Lang = Lang.detect(args.lang),
) {
    KitWizard(config, args, appImageDir, lang) {
        val S = it.strings
        SimpleFrame(
            title = S.diagTitle,
            subtitle = S.diagHint,
            content = {
                InfoLine(S.diagProductName, config.productName)
                InfoLine(S.diagAppId, config.appId)
                InfoLine(S.diagVersion, config.effectiveVersion)
                InfoLine(S.diagPublisher, config.publisher.ifBlank { S.diagDash })
                InfoLine(S.diagMainExe, config.effectiveExeName)
                InfoLine(S.diagInstallDirName, config.effectiveInstallDirName)
                InfoLine(S.diagDataDirName, config.effectiveDataDirName)

                Spacer(Modifier.height(8.dp))
                InfoLine(S.diagSelfExe, KitSelf.exePath ?: S.diagNoImage)
                InfoLine(S.diagAppImage, appImageDir?.absolutePath ?: S.diagDash)
                InfoLine(S.diagConfigFile, configPath ?: S.diagConfigFallback)
                InfoLine(S.diagLang, "${lang.code} · ${lang.label}")
                InfoLine(S.diagWizardClass, config.wizardMainClass)
                InfoLine(
                    S.diagClasspath,
                    if (config.wizardClasspath.isEmpty()) S.diagDash else config.wizardClasspath.joinToString("\n"),
                )
                InfoLine(
                    "kit-logo.png",
                    KitAssets.productLogoFile(appImageDir)?.absolutePath ?: S.diagDash,
                )
                InfoLine(
                    "kit-icon-*.png",
                    if (KitAssets.hasProductIcons(appImageDir)) "✓" else S.diagDash,
                )
                if (args.raw.isNotEmpty()) InfoLine(S.diagArgs, args.raw.joinToString(" "))

                if (configError != null) {
                    Spacer(Modifier.height(12.dp))
                    ErrorBox(S.diagConfigError.fill("error" to (configError.message ?: configError.toString())))
                }
            },
            bottom = { Spacer(Modifier.weight(1f)) },
        )
    }
}
