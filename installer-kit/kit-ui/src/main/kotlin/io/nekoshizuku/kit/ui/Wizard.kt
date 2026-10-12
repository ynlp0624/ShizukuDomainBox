package io.nekoshizuku.kit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.nekoshizuku.kit.InstallerConfig
import io.nekoshizuku.kit.KitArgs
import io.nekoshizuku.kit.KitSelf
import io.nekoshizuku.kit.WinPaths
import io.nekoshizuku.kit.ui.i18n.Lang
import io.nekoshizuku.kit.ui.i18n.Strings
import java.io.File

/**
 * 一次向导运行的全部上下文。
 *
 * 挂在 [LocalWizard] 上往下传，这样各个页面组件不用一路写参数，
 * 也让「页面」保持成纯粹的展示组件（谁都能拿去用）。
 */
@Immutable
data class WizardContext(
    val config: InstallerConfig,
    val args: KitArgs,
    val strings: Strings,
    val lang: Lang,
    val brand: Color,
    val logo: ImageBitmap?,
    val appImageDir: File?,
)

val LocalWizard = staticCompositionLocalOf<WizardContext> { error("LocalWizard 还没有被提供") }

/** 当前向导的文案（页面里写 `S.welcomeTitle` 就行） */
val wizardStrings: Strings
    @Composable get() = LocalWizard.current.strings

/** 把 `{xxx}` 占位符填上（只用于那些没放进全局 vars 的局部占位符，比如 `{path}`） */
internal fun String.fill(vararg pairs: Pair<String, String>): String =
    pairs.fold(this) { acc, (k, v) -> acc.replace("{$k}", v) }

@Composable
fun rememberWizardContext(
    config: InstallerConfig,
    args: KitArgs,
    appImageDir: File?,
    lang: Lang,
): WizardContext {
    val strings = remember(config, lang) {
        Strings(
            lang = lang,
            overrides = config.i18n,
            // 便携模式（绿色软件）没有自带 Java 运行时，欢迎页文案要跟着换
            portable = config.isPortable,
            // 全局占位符：所有页面都能直接用。
            // 页面自己的局部占位符（{path} / {sizeMb} / {name} / {error}）在页面里现填。
            vars = mapOf(
                "productName" to config.productName,
                "version" to config.effectiveVersion,
                "publisher" to config.publisher,
                "appId" to config.appId,
                "dataDir" to WinPaths(config).dataDir().absolutePath,
                "mainExe" to config.effectiveExeName,
            ),
        )
    }
    val brand = remember(config.brandColor) {
        KitBrand.parse(config.brandColor) ?: KitBrand.Default
    }
    val logo = remember(appImageDir) { KitAssets.productLogo(appImageDir) }
    return WizardContext(config, args, strings, lang, brand, logo, appImageDir)
}

/** 把上下文 + 主题一起装好。三个向导入口都用它，保证观感一致。 */
@Composable
fun KitWizard(
    config: InstallerConfig,
    args: KitArgs,
    appImageDir: File? = KitSelf.appImageDir,
    lang: Lang = Lang.detect(args.lang),
    content: @Composable (WizardContext) -> Unit,
) {
    val ctx = rememberWizardContext(config, args, appImageDir, lang)
    CompositionLocalProvider(LocalWizard provides ctx) {
        KitTheme(ctx.brand) { content(ctx) }
    }
}

/* ============================== 外壳 ============================== */

/**
 * 安装向导的外壳：左品牌区（logo + 产品名 + 步骤）+ 右内容区 + 底按钮条。
 */
@Composable
fun WizardFrame(
    steps: List<WizardStep>,
    currentIndex: Int,
    content: @Composable ColumnScope.() -> Unit,
    bottom: @Composable RowScope.() -> Unit,
) {
    val ctx = LocalWizard.current
    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BrandPanel(
            productName = ctx.config.productName,
            version = ctx.config.effectiveVersion,
            steps = steps,
            currentIndex = currentIndex,
            brand = ctx.brand,
            logo = ctx.logo,
        )
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .verticalScroll(rememberScrollState())
                    .padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                content = content,
            )
            BottomBar(bottom)
        }
    }
}

/** 卸载 / 修复向导的外壳：没有步骤条，顶部一条品牌信息 */
@Composable
fun SimpleFrame(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
    bottom: @Composable RowScope.() -> Unit,
) {
    val ctx = LocalWizard.current
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState())
                .padding(30.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BrandHeader(ctx.config.productName, ctx.config.effectiveVersion, ctx.brand, ctx.logo)
            Spacer(Modifier.height(18.dp))
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
        BottomBar(bottom)
    }
}

@Composable
private fun BottomBar(actions: @Composable RowScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 22.dp, vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            actions()
        }
    }
}

/* ============================== 小件 ============================== */

@Composable
fun Title(text: String) =
    Text(text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)

@Composable
fun SectionTitle(text: String) =
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium)

@Composable
fun Body(text: String, muted: Boolean = false) = Text(
    text,
    fontSize = 13.sp,
    color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
)

@Composable
fun Hint(text: String, lineHeight: androidx.compose.ui.unit.TextUnit = 18.sp) = Text(
    text,
    fontSize = 11.sp,
    lineHeight = lineHeight,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

/** 「标签 : 等宽值」一行，路径用它最清楚 */
@Composable
fun InfoLine(label: String, value: String, labelWidth: androidx.compose.ui.unit.Dp = 96.dp) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(labelWidth),
        )
        Text(value, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

/** 单选框样式的选项卡（安装范围、卸载方式都用它） */
@Composable
fun OptRow(selected: Boolean, title: String, desc: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(10.dp),
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Spacer(Modifier.width(6.dp))
        Column {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun CheckRow(checked: Boolean, text: String, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(text, fontSize = 13.sp)
    }
}

@Composable
fun ErrorBox(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(12.dp),
    ) {
        Text(message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

/** 带标题的滚动框（许可协议正文） */
@Composable
fun ScrollBox(height: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp)
            .verticalScroll(rememberScrollState()),
    ) { content() }
}
