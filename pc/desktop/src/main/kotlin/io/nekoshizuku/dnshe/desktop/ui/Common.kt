package io.nekoshizuku.dnshe.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.nekoshizuku.dnshe.desktop.i18n.Str
import io.nekoshizuku.dnshe.desktop.i18n.t
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/* ============================== 基础组件 ==============================
 * 和安卓端同一套「咽喉式迁移」：页面都通过这些组件取样式，
 * 只改这一层，桌面端整体就变成 HyperOS 观感。
 */

/** Miuix Card 的 InsideMargin 默认是 0，必须自己给内边距 */
private val CardPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = MiuixTheme.colorScheme.primary,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(start = 2.dp, top = 12.dp, bottom = 6.dp),
    )
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (onClick == null) {
        Card(modifier = modifier.fillMaxWidth(), insideMargin = CardPadding) { content() }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            insideMargin = CardPadding,
            onClick = onClick,
        ) { content() }
    }
}

@Composable
fun InfoRow(
    label: String,
    value: String?,
    mono: Boolean = false,
    labelWidth: Dp = 104.dp,
    onCopy: (() -> Unit)? = null,
) {
    if (value.isNullOrBlank()) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 13.sp,
            modifier = Modifier.width(labelWidth),
        )
        Text(
            text = value,
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = 13.5.sp,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            modifier = Modifier.weight(1f),
        )
        if (onCopy != null) {
            IconButton(onClick = onCopy, minWidth = 28.dp, minHeight = 28.dp) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = t(Str.Copy),
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

@Composable
fun StatusPill(status: String?) {
    val c = MiuixTheme.colorScheme
    val (bg, fg) = when (status?.lowercase()) {
        "active", "registered" -> c.primaryContainer to c.onPrimaryContainer
        "suspended" -> c.errorContainer to c.error
        "expired" -> c.surfaceContainerHighest to c.onSurfaceVariantSummary
        else -> c.secondaryContainer to c.onSecondaryContainer
    }
    val text = when (status?.lowercase()) {
        "active", "registered" -> t(Str.StatusActive)
        "suspended" -> t(Str.StatusSuspended)
        "expired" -> t(Str.StatusExpired)
        else -> status ?: t(Str.Unknown)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 9.dp, vertical = 2.dp),
    ) {
        Text(text = text, color = fg, fontSize = 11.sp)
    }
}

/** 应用标志 */
@Composable
fun AppLogo(size: Dp = 56.dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource("app-logo.png"),
        contentDescription = null,
        modifier = modifier.size(size).clip(RoundedCornerShape(size * 0.26f)),
    )
}

/** 空状态 / 提示位用的矢量图标 */
@Composable
fun StateIcon(image: ImageVector, tint: Color? = null) {
    Icon(
        imageVector = image,
        contentDescription = null,
        tint = tint ?: MiuixTheme.colorScheme.primary,
        modifier = Modifier.size(46.dp),
    )
}

@Composable
fun EmptyState(
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) icon() else AppLogo(60.dp)
        Text(
            text = text,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 14.sp,
        )
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeWidth = 2.5.dp, size = 30.dp)
    }
}

@Composable
fun ErrorBox(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.error,
            modifier = Modifier.size(34.dp),
        )
        Text(t(Str.Error), fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(
            message,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 13.5.sp,
        )
        if (onRetry != null) {
            TextButton(text = t(Str.Retry), onClick = onRetry)
        }
    }
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    hint: String? = null,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    isSecret: Boolean = false,
    revealSecret: Boolean = false,
    enabled: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            useLabelAsPlaceholder = placeholder != null && value.isEmpty(),
            enabled = enabled,
            singleLine = singleLine,
            maxLines = maxLines,
            visualTransformation = if (isSecret && !revealSecret) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (hint != null) {
            Text(
                text = hint,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, top = 5.dp),
            )
        }
    }
}

@Composable
fun SettingSwitchRow(title: String, desc: String? = null, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            if (desc != null) {
                Text(desc, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingClickRow(title: String, desc: String? = null, value: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            if (desc != null) {
                Text(desc, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
            }
        }
        if (value != null) {
            Text(
                value,
                color = MiuixTheme.colorScheme.primary,
                fontSize = 13.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(280.dp),
            )
        }
    }
}

@Composable
fun InlineMessage(text: String?, isError: Boolean = true, modifier: Modifier = Modifier) {
    if (text.isNullOrBlank()) return
    val c = MiuixTheme.colorScheme
    val bg = if (isError) c.errorContainer else c.primaryContainer
    val fg = if (isError) c.error else c.primary
    Card(
        modifier = modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        colors = CardDefaults.defaultColors(color = bg),
    ) {
        Text(text, color = fg, fontSize = 12.5.sp)
    }
}

/* ============================ 窗口内弹窗 ============================= */

/**
 * 桌面版用「窗口内浮层」而不是独立窗口：
 * 不会被主窗口挡住、也方便统一截图和键盘处理。
 *
 * 注意：这个组件名字和 Miuix 的 OverlayDialog 撞名，我们用的是自己这套，
 * 所以文件里没有 import Miuix 的那个。
 */
@Composable
fun OverlayDialog(
    title: String,
    onDismiss: () -> Unit,
    width: Dp = 640.dp,
    maxHeight: Dp = 640.dp,
    showClose: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(enabled = false) { },
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.width(width).heightIn(max = maxHeight),
            cornerRadius = 20.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (showClose) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = t(Str.Close),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content,
                )
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

/** 底部按钮条（配合 OverlayDialog 使用） */
@Composable
fun DialogButtons(
    onCancel: (() -> Unit)?,
    onConfirm: (() -> Unit)?,
    confirmLabel: String = t(Str.Confirm),
    cancelLabel: String = t(Str.Cancel),
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
    extra: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        extra?.invoke()
        Spacer(Modifier.weight(1f))
        if (onCancel != null) {
            TextButton(text = cancelLabel, onClick = onCancel)
        }
        if (onConfirm != null) {
            if (destructive) {
                Button(
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.error,
                        disabledColor = MiuixTheme.colorScheme.disabledPrimaryButton,
                    ),
                ) { Text(confirmLabel) }
            } else {
                Button(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmLabel) }
            }
        }
    }
}

/** 通用选择对话框 */
@Composable
fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayDialog(title = title, onDismiss = onDismiss, width = 440.dp, maxHeight = 540.dp) {
        options.forEach { (value, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (value == selected) MiuixTheme.colorScheme.primaryContainer else Color.Transparent
                    )
                    .clickable { onSelect(value); onDismiss() }
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    color = if (value == selected) {
                        MiuixTheme.colorScheme.onPrimaryContainer
                    } else {
                        MiuixTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

/** 带下拉的类型选择器 */
@Composable
fun TypeDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Text(label, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Box {
            TextButton(text = "$selected   ▾", onClick = { open = true })
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { o ->
                    DropdownMenuItem(text = { Text(o, fontSize = 14.sp) }, onClick = { onSelect(o); open = false })
                }
            }
        }
    }
}

/** 顶部工具条按钮 */
@Composable
fun ToolbarButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(text = text, onClick = onClick, enabled = enabled)
}

/** 简单的分隔线 */
@Composable
fun Divider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier.fillMaxWidth(), color = MiuixTheme.colorScheme.dividerLine)
}

/** 左侧竖条色块（用于强调） */
@Composable
fun AccentBar(color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.width(3.dp).fillMaxHeight().background(color, RoundedCornerShape(2.dp)))
}

@Composable
fun OutlinedLabel(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, MiuixTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(text, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 11.sp)
    }
}
