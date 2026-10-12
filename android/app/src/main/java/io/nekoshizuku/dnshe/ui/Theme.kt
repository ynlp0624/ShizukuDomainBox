package io.nekoshizuku.dnshe.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.nekoshizuku.dnshe.data.ThemeMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColorScheme

/* 雫的蓝白配色 —— 参考猫羽雫的蓝色长发 + 白色水欧泊 */

private val Blue10 = Color(0xFF0A1533)
private val Blue20 = Color(0xFF14295C)
private val Blue40 = Color(0xFF3E6FD9)
private val Blue80 = Color(0xFFA9C6FF)
private val Blue90 = Color(0xFFD9E5FF)
private val Pink40 = Color(0xFFB3557E)
private val Pink80 = Color(0xFFFFB0CE)
private val Pink90 = Color(0xFFFFD9E6)

/* --------------------------- Material3（迁移期间保留） --------------------------- */

private val LightColors = lightColorScheme(
    primary = Blue40,
    onPrimary = Color.White,
    primaryContainer = Blue90,
    onPrimaryContainer = Blue10,
    secondary = Color(0xFF5A6B94),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE3F3),
    onSecondaryContainer = Color(0xFF16203A),
    tertiary = Pink40,
    onTertiary = Color.White,
    tertiaryContainer = Pink90,
    onTertiaryContainer = Color(0xFF3D0022),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF191C22),
    surface = Color(0xFFF8F9FF),
    onSurface = Color(0xFF191C22),
    surfaceVariant = Color(0xFFE1E3EE),
    onSurfaceVariant = Color(0xFF44474F),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F3FA),
    surfaceContainer = Color(0xFFECEEF6),
    surfaceContainerHigh = Color(0xFFE6E8F0),
    surfaceContainerHighest = Color(0xFFE0E2EA),
)

private val DarkColors = darkColorScheme(
    primary = Blue80,
    onPrimary = Blue20,
    primaryContainer = Color(0xFF2A4A96),
    onPrimaryContainer = Blue90,
    secondary = Color(0xFFBAC5E3),
    onSecondary = Color(0xFF25314F),
    secondaryContainer = Color(0xFF3B4767),
    onSecondaryContainer = Color(0xFFDDE3F3),
    tertiary = Pink80,
    onTertiary = Color(0xFF5C1137),
    tertiaryContainer = Color(0xFF7B2A50),
    onTertiaryContainer = Pink90,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF11131A),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF11131A),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474F),
    surfaceContainerLowest = Color(0xFF0C0E14),
    surfaceContainerLow = Color(0xFF191B23),
    surfaceContainer = Color(0xFF1D1F27),
    surfaceContainerHigh = Color(0xFF282A32),
    surfaceContainerHighest = Color(0xFF33353D),
)

private val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.5.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
)

/* --------------------------- Miuix（HyperOS 风格） --------------------------- */

/**
 * Miuix 配色：中性层次沿用 HyperOS 默认值（那套灰白卡片分层是 MIUI 观感的关键），
 * 只把蓝色的几个角色换成我们的品牌蓝 #3E6FD9，保持产品识别度。
 */
private val MiuixLight = miuixLightColorScheme(
    primary = Blue40,
    onPrimary = Color.White,
    primaryVariant = Blue40,
    onPrimaryVariant = Blue80,
    primaryContainer = Blue90,
    onPrimaryContainer = Blue10,
)

private val MiuixDark = miuixDarkColorScheme(
    primary = Blue80,
    onPrimary = Blue20,
    primaryVariant = Blue80,
    onPrimaryVariant = Blue20,
    primaryContainer = Color(0xFF2A4A96),
    onPrimaryContainer = Blue90,
)

@Composable
fun themeIsDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

/**
 * 迁移期间 Material3 与 Miuix 同时提供：
 * 还没改写的页面继续用 MaterialTheme，改写过的页面用 Miuix 组件，
 * 两边取色都正常，可以一屏一屏迁。
 */
@Composable
fun ShizukuTheme(dark: Boolean, dynamic: Boolean, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val monetAvailable = dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val materialScheme = when {
        monetAvailable -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkColors
        else -> LightColors
    }

    MaterialTheme(colorScheme = materialScheme, typography = AppTypography) {
        if (monetAvailable) {
            // 动态取色：让 Miuix 也从壁纸取色，keyColor 用品牌蓝兜底
            val controller = remember(dark) {
                ThemeController(
                    colorSchemeMode = if (dark) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight,
                    keyColor = Blue40,
                )
            }
            MiuixTheme(controller = controller, content = content)
        } else {
            MiuixTheme(colors = if (dark) MiuixDark else MiuixLight, content = content)
        }
    }
}
