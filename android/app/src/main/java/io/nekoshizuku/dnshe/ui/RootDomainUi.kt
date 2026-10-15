package io.nekoshizuku.dnshe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.nekoshizuku.dnshe.data.RootDomains
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import io.nekoshizuku.dnshe.i18n.Str
import io.nekoshizuku.dnshe.i18n.t

/** 官方 / 外部 / 未知 的小标签 */
@Composable
fun RootDomainBadge(root: String, modifier: Modifier = Modifier) {
    if (root.isBlank()) return
    val (bg, fg, label) = when {
        RootDomains.isExternal(root) ->
            Triple(
                MiuixTheme.colorScheme.errorContainer,
                MiuixTheme.colorScheme.error,
                t(Str.RootDomainExternalTag),
            )
        RootDomains.isKnown(root) ->
            Triple(
                MiuixTheme.colorScheme.primaryContainer,
                MiuixTheme.colorScheme.onPrimaryContainer,
                t(Str.RootDomainOfficialTag),
            )
        else ->
            Triple(
                MiuixTheme.colorScheme.surfaceContainerHighest,
                MiuixTheme.colorScheme.onSurfaceVariantSummary,
                t(Str.UnknownRootTag),
            )
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text = label, color = fg, fontSize = 10.sp)
    }
}

/**
 * 外部域名 / 未知根域名的提示卡。
 * 只在需要的时候渲染，不占位置。
 */
@Composable
fun RootDomainNotice(root: String, modifier: Modifier = Modifier) {
    if (root.isBlank()) return
    val external = RootDomains.isExternal(root)
    val unknown = !RootDomains.isKnown(root)
    if (!external && !unknown) return

    val bg = if (external) MiuixTheme.colorScheme.errorContainer else MiuixTheme.colorScheme.tertiaryContainer
    val fg = if (external) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onTertiaryContainer

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = bg,
    ) {
        Row(modifier = Modifier.padding(14.dp)) {
            Icon(
                imageVector = if (external) Icons.Filled.Warning else Icons.Filled.Info,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = if (external) t(Str.ExternalWarnTitle) else t(Str.UnknownRootWarnTitle),
                    fontSize = 14.sp,
                    color = fg,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (external) t(Str.ExternalWarnBody, root) else t(Str.UnknownRootWarnBody, root),
                    fontSize = 12.sp,
                    color = fg,
                )
            }
        }
    }
}

/** 域名列表里跟在域名后面的极简外部标记 */
@Composable
fun ExternalMiniTag(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MiuixTheme.colorScheme.errorContainer)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            text = t(Str.RootDomainExternalTag),
            color = MiuixTheme.colorScheme.error,
            fontSize = 9.sp,
        )
    }
}

/**
 * 根域名选择对话框：官方分组在上，外部分组在下并且整体标红。
 */
@Composable
fun RootDomainPickerDialog(
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var dlgShow by remember { mutableStateOf(true) }
    OverlayDialog(
        show = dlgShow,
        title = t(Str.RootDomainListTitle),
        renderInRootScaffold = false,
        onDismissRequest = { dlgShow = false; onDismiss() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = t(Str.RootDomainNote),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(10.dp))

            GroupHeader(t(Str.RootDomainGroupOfficial), MiuixTheme.colorScheme.onSurfaceVariantSummary)
            RootDomains.OFFICIAL.forEach { opt ->
                RootOptionRow(
                    domain = opt.domain,
                    selected = opt.domain.equals(current.trim(), true),
                    highlight = false,
                    onClick = { onSelect(opt.domain) },
                )
            }

            Spacer(Modifier.height(14.dp))
            GroupHeader(t(Str.RootDomainGroupExternal), MiuixTheme.colorScheme.error)
            RootDomains.EXTERNAL.forEach { opt ->
                RootOptionRow(
                    domain = opt.domain,
                    selected = opt.domain.equals(current.trim(), true),
                    highlight = true,
                    onClick = { onSelect(opt.domain) },
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = t(Str.ExternalWarnShort),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.error,
            )
            Spacer(Modifier.height(10.dp))
            TextButton(
                text = t(Str.Close),
                onClick = { dlgShow = false; onDismiss() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun GroupHeader(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

@Composable
private fun RootOptionRow(
    domain: String,
    selected: Boolean,
    highlight: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = domain,
            fontSize = 16.sp, fontFamily = FontFamily.Monospace,
            color = when {
                selected -> MiuixTheme.colorScheme.primary
                highlight -> MiuixTheme.colorScheme.error
                else -> MiuixTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
        )
        if (highlight) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MiuixTheme.colorScheme.errorContainer)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(
                    t(Str.RootDomainExternalTag),
                    color = MiuixTheme.colorScheme.error,
                    fontSize = 10.sp,
                )
            }
        }
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
