package io.nekoshizuku.dnshe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.nekoshizuku.dnshe.data.Lang
import io.nekoshizuku.dnshe.i18n.Str
import io.nekoshizuku.dnshe.i18n.t
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 更新说明弹窗。
 *
 * 触发时机：App 启动时比对「上次看到的版本」和当前版本，不一致就弹。
 * 也可以在设置里手动打开（看历史更新）。
 */
@Composable
fun ReleaseNotesDialog(
    note: ReleaseNote,
    lang: Lang,
    onDismiss: () -> Unit,
    /** 是否还有更多历史版本可看 */
    hasHistory: Boolean = false,
    onOpenHistory: (() -> Unit)? = null,
) {
    var show by remember { mutableStateOf(true) }

    OverlayDialog(
        show = show,
        title = note.title(lang),
        renderInRootScaffold = true,
        onDismissRequest = { show = false; onDismiss() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // 版本号 + 日期
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (note.major) MiuixTheme.colorScheme.primaryContainer
                            else MiuixTheme.colorScheme.surfaceContainer
                        )
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "v${note.version}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (note.major) MiuixTheme.colorScheme.onPrimaryContainer
                        else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = note.date,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                if (note.major) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (lang == Lang.EN) "Major update" else "重要更新",
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 条目
            note.items.forEach { item ->
                NoteRow(item, lang)
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(6.dp))

            // 按钮
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (hasHistory && onOpenHistory != null) {
                    TextButton(
                        text = if (lang == Lang.EN) "History" else "历史版本",
                        onClick = {
                            show = false
                            onOpenHistory()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                TextButton(
                    text = t(Str.Ok),
                    onClick = { show = false; onDismiss() },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NoteRow(item: NoteItem, lang: Lang) {
    val badgeColor = when (item.kind) {
        NoteItem.Kind.NEW -> MiuixTheme.colorScheme.primaryContainer
        NoteItem.Kind.FIX -> MiuixTheme.colorScheme.errorContainer
        NoteItem.Kind.IMPROVE -> MiuixTheme.colorScheme.surfaceContainer
    }
    val textColor = when (item.kind) {
        NoteItem.Kind.NEW -> MiuixTheme.colorScheme.onPrimaryContainer
        NoteItem.Kind.FIX -> MiuixTheme.colorScheme.error
        NoteItem.Kind.IMPROVE -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    }

    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(badgeColor)
                .padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            Text(
                text = item.badge(lang),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = textColor,
            )
        }
        Spacer(Modifier.width(9.dp))
        Text(
            text = item.text(lang),
            fontSize = 13.sp,
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 历史更新列表（从设置里进）。
 */
@Composable
fun ReleaseHistoryScreen(
    lang: Lang,
    onBack: () -> Unit,
) {
    top.yukonga.miuix.kmp.basic.Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
        topBar = {
            top.yukonga.miuix.kmp.basic.TopAppBar(
                title = if (lang == Lang.EN) "Release notes" else "更新日志",
                navigationIcon = {
                    top.yukonga.miuix.kmp.basic.IconButton(onClick = onBack) {
                        top.yukonga.miuix.kmp.basic.Icon(
                            MiuixIcons.Back,
                            contentDescription = t(Str.Back),
                            modifier = Modifier.width(24.dp).height(24.dp),
                        )
                    }
                },
                color = MiuixTheme.colorScheme.background,
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            RELEASE_NOTES.forEach { note ->
                androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = note.title(lang),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "v${note.version}",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = note.date,
                                fontSize = 11.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        note.items.forEach { item ->
                            NoteRow(item, lang)
                            Spacer(Modifier.height(7.dp))
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                androidx.compose.material3.HorizontalDivider(
                    color = MiuixTheme.colorScheme.dividerLine,
                )
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}
