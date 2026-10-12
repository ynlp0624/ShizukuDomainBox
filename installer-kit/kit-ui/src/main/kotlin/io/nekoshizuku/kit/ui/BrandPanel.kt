package io.nekoshizuku.kit.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 品牌区里的一步 */
data class WizardStep(val label: String)

/**
 * 左侧品牌区：logo + 产品名 + 步骤指示。
 *
 * 步骤指示做成「圆点 + 文字」的列表（比一根进度条信息量大）：
 * 已经走过的步骤打勾，当前步骤高亮，后面的暗淡。
 */
@Composable
fun BrandPanel(
    productName: String,
    version: String,
    steps: List<WizardStep>,
    currentIndex: Int,
    brand: Color,
    logo: ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    val dark = KitBrand.darken(brand, 0.45f)
    val tint = KitBrand.tint(brand, 0.80f)

    Box(
        modifier = modifier
            .width(250.dp)
            .fillMaxHeight()
            .background(Brush.verticalGradient(listOf(dark, brand))),
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            AppLogo(64.dp, logo)
            Spacer(Modifier.height(16.dp))
            Text(productName, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
            Text("v$version · Windows", color = tint, fontSize = 12.sp)

            Spacer(Modifier.height(22.dp))
            steps.forEachIndexed { index, step ->
                StepRow(
                    index = index,
                    label = step.label,
                    state = when {
                        index < currentIndex -> StepState.DONE
                        index == currentIndex -> StepState.CURRENT
                        else -> StepState.TODO
                    },
                    brand = brand,
                )
                Spacer(Modifier.height(9.dp))
            }

            Spacer(Modifier.weight(1f))
            Text("InstallerKit", color = Color(0x77FFFFFF), fontSize = 10.sp)
        }
    }
}

private enum class StepState { DONE, CURRENT, TODO }

@Composable
private fun StepRow(index: Int, label: String, state: StepState, brand: Color) {
    val (dotColor, textColor, weight) = when (state) {
        StepState.DONE -> Triple(Color(0x66FFFFFF), Color(0xAAFFFFFF), FontWeight.Normal)
        StepState.CURRENT -> Triple(Color.White, Color.White, FontWeight.SemiBold)
        StepState.TODO -> Triple(Color(0x33FFFFFF), Color(0x77FFFFFF), FontWeight.Normal)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(dotColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (state == StepState.DONE) "✓" else "${index + 1}",
                color = if (state == StepState.CURRENT) brand else Color(0xFF2A3550),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.width(9.dp))
        Text(
            label,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = weight,
            modifier = Modifier.alpha(if (state == StepState.TODO) 0.85f else 1f),
        )
    }
}

/**
 * 应用 logo：优先用产品自己的（打包期注入的 `app\kit-logo.png`），
 * 没有就用 kit 内置的。圆角比例沿用 jpackage 图标那套观感。
 */
@Composable
fun AppLogo(size: Dp = 56.dp, logo: ImageBitmap? = null, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size * 0.26f)
    val m = modifier.size(size).clip(shape)
    if (logo != null) {
        Image(bitmap = logo, contentDescription = null, modifier = m)
    } else {
        Image(painter = painterResource("app-logo.png"), contentDescription = null, modifier = m)
    }
}

/** 卸载向导那种「没有步骤」的页面用的顶部品牌条 */
@Composable
fun BrandHeader(
    productName: String,
    version: String,
    brand: Color,
    logo: ImageBitmap?,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        AppLogo(48.dp, logo)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                productName,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text("v$version · Windows", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
