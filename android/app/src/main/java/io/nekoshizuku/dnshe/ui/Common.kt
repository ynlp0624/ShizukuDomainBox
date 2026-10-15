package io.nekoshizuku.dnshe.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.nekoshizuku.dnshe.R
import io.nekoshizuku.dnshe.i18n.Str
import io.nekoshizuku.dnshe.i18n.t
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/* ============================== 通用小组件 ==============================
 * 说明：这些组件是「咽喉」——所有页面都通过它们取样式。
 * 内部换成 Miuix 组件后，各页面不用改代码就能整体变成 HyperOS 观感。
 */

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = MiuixTheme.colorScheme.primary,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(start = 4.dp, top = 10.dp, bottom = 6.dp),
    )
}

/** Miuix Card 的 InsideMargin 默认是 0，必须自己给内边距 */
private val CardPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)

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
fun KeyValueRow(
    label: String,
    value: String?,
    mono: Boolean = false,
    onCopy: (() -> Unit)? = null,
) {
    if (value.isNullOrBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 13.sp,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = 13.5.sp,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            modifier = Modifier
                .weight(1f)
                .then(if (onCopy != null) Modifier.clickable { onCopy() } else Modifier),
        )
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

/** 应用标志（正式版界面里代替 emoji 用） */
@Composable
fun AppLogo(size: Dp = 56.dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_logo),
        contentDescription = null,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.26f)),
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
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            icon()
        } else {
            AppLogo(60.dp)
        }
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

/** 空状态里用的矢量图标（比 emoji 正式得多） */
@Composable
fun StateIcon(painter: androidx.compose.ui.graphics.painter.Painter, tint: Color? = null) {
    Icon(
        painter = painter,
        contentDescription = null,
        tint = tint ?: MiuixTheme.colorScheme.primary,
        modifier = Modifier.size(46.dp),
    )
}

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
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeWidth = 2.5.dp, size = 30.dp)
    }
}

@Composable
fun ErrorBox(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.error,
            modifier = Modifier.size(34.dp),
        )
        Text(text = t(Str.Error), fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(
            text = message,
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
    keyboardType: KeyboardType = KeyboardType.Text,
    leading: ImageVector? = null,
    enabled: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            // Miuix 没有独立的 placeholder，用 label 兼当占位提示更贴近 HyperOS
            useLabelAsPlaceholder = placeholder != null && value.isEmpty(),
            enabled = enabled,
            singleLine = singleLine,
            maxLines = maxLines,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = if (isSecret && !revealSecret) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            leadingIcon = leading?.let {
                { Icon(it, contentDescription = null, modifier = Modifier.size(20.dp)) }
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
fun SettingSwitchRow(
    title: String,
    desc: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            if (desc != null) {
                Text(
                    desc,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingClickRow(
    title: String,
    desc: String? = null,
    value: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            if (desc != null) {
                Text(
                    desc,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                )
            }
        }
        if (value != null) {
            Text(
                value,
                color = MiuixTheme.colorScheme.primary,
                fontSize = 13.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                modifier = Modifier.padding(start = 12.dp).width(150.dp),
            )
        }
    }
}

/**
 * 确认对话框。用 Miuix 的 OverlayDialog（HyperOS 是从底部滑上来的卡片），
 * renderInRootScaffold = false 表示渲染到 AppRoot 里挂的 MiuixPopupHost。
 */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String = t(Str.Confirm),
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var show by remember { mutableStateOf(true) }
    OverlayDialog(
        show = show,
        title = title,
        summary = text,
        renderInRootScaffold = false,
        onDismissRequest = { show = false; onDismiss() },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                text = t(Str.Cancel),
                onClick = { show = false; onDismiss() },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = confirmLabel,
                onClick = { show = false; onConfirm(); onDismiss() },
                modifier = Modifier.weight(1f),
                colors = if (destructive) {
                    top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColors(
                        color = MiuixTheme.colorScheme.error,
                    )
                } else {
                    top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColors()
                },
            )
        }
    }
}

@Composable
fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String>>, // value to label
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var show by remember { mutableStateOf(true) }
    OverlayDialog(
        show = show,
        title = title,
        renderInRootScaffold = false,
        onDismissRequest = { show = false; onDismiss() },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            options.forEach { (value, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { show = false; onSelect(value); onDismiss() }
                        .padding(horizontal = 12.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        fontSize = 15.sp,
                        fontWeight = if (value == selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (value == selected) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (value == selected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            TextButton(
                text = t(Str.Close),
                onClick = { show = false; onDismiss() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * 小信息块。这里刻意不用 Miuix 的 Card：
 * 它的内部 Box 带 propagateMinConstraints，和外层 Row 的 weight 组合时
 * 高度会被算小，导致文字溢出卡片。直接用带背景的 Box 最稳。
 */
@Composable
fun InfoTile(
    label: String,
    value: String,
    accent: Color = MiuixTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column {
            Text(
                label,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
            )
            Text(value, color = accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun ClearableField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String? = null,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        useLabelAsPlaceholder = placeholder != null && value.isEmpty(),
        singleLine = true,
        trailingIcon = {
            if (value.isNotEmpty()) {
                Icon(
                    Icons.Filled.Clear,
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onValueChange("") },
                )
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

/** 手机上字段多的表单用整屏对话框更舒服 */
@Composable
fun FullScreenDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            // 关键：默认 true 时对话框窗口会自己吃掉 IME inset，
            // 导致 imePadding() 拿不到键盘高度，底部按钮被键盘盖住。
            decorFitsSystemWindows = false,
        ),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize(0.94f)
                .padding(horizontal = 4.dp, vertical = 16.dp)
                .imePadding(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 6.dp, end = 2.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Filled.Clear,
                        contentDescription = t(Str.Close),
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { onDismiss() },
                    )
                }
                content()
            }
        }
    }
}

/**
 * 对话框里的行内提示。
 * 对话框是独立窗口，会把 Activity 的 Snackbar 挡在后面，所以
 * 表单类对话框必须自己把错误/成功信息显示出来。
 */
@Composable
fun InlineMessage(
    text: String?,
    isError: Boolean = true,
    modifier: Modifier = Modifier,
) {
    if (text.isNullOrBlank()) return
    val c = MiuixTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(
            color = if (isError) c.errorContainer else c.primaryContainer,
        ),
    ) {
        Text(
            text = text,
            color = if (isError) c.error else c.primary,
            fontSize = 12.5.sp,
        )
    }
}

@Composable
fun FullScreenLoading(text: String = t(Str.Loading)) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(strokeWidth = 3.dp)
        Spacer(Modifier.height(14.dp))
        Text(text, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}
