package io.nekoshizuku.kit.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 品牌色。产品的主题色可以写在 `installer.config.json` 的 `brandColor` 里
 * （`#RRGGBB`），不写就用默认的蓝色。
 */
object KitBrand {

    val Default: Color = Color(0xFF3E6FD9)

    private val FALLBACK_PALETTE = listOf(
        Color(0xFF3E6FD9), Color(0xFF7C4DFF), Color(0xFF00897B),
        Color(0xFFE64A19), Color(0xFFC2185B), Color(0xFF455A64),
    )

    /** 解析 `#RRGGBB` / `RRGGBB` / `#AARRGGBB`；解析不了就返回 null */
    fun parse(hex: String?): Color? {
        val raw = hex?.trim()?.removePrefix("#")?.takeIf { it.isNotEmpty() } ?: return null
        val value = raw.toLongOrNull(16) ?: return null
        return when (raw.length) {
            6 -> Color(0xFF000000L or value)
            8 -> Color(value)
            else -> null
        }
    }

    /** 把品牌色压暗一档，用来做左侧渐变的下半段 */
    fun darken(color: Color, factor: Float = 0.45f): Color = Color(
        red = color.red * (1f - factor),
        green = color.green * (1f - factor),
        blue = color.blue * (1f - factor),
        alpha = color.alpha,
    )

    /** 品牌色的浅色调，用于品牌区里的小字 */
    fun tint(color: Color, towardsWhite: Float = 0.72f): Color = Color(
        red = color.red + (1f - color.red) * towardsWhite,
        green = color.green + (1f - color.green) * towardsWhite,
        blue = color.blue + (1f - color.blue) * towardsWhite,
        alpha = color.alpha,
    )

    /** 品牌色为空时的兜底（按产品名稳定挑一个，别每次打包都变） */
    fun fallbackFor(seed: String): Color =
        FALLBACK_PALETTE[(seed.hashCode().toLong() and 0x7FFFFFFF).mod(FALLBACK_PALETTE.size.toLong()).toInt()]
}

/** 向导主题。整个 kit-ui 只用这一个主题入口，换皮改这里。 */
@Composable
fun KitTheme(
    brand: Color = KitBrand.Default,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = brand,
            onPrimary = Color.White,
            secondary = KitBrand.darken(brand, 0.35f),
            surface = Color(0xFFFDFDFF),
            surfaceVariant = Color(0xFFF2F4FA),
            outlineVariant = Color(0xFFD9DEE9),
            background = Color(0xFFFDFDFF),
            error = Color(0xFFB3261E),
            errorContainer = Color(0xFFF9DEDC),
            onErrorContainer = Color(0xFF410E0B),
        ),
        content = content,
    )
}
