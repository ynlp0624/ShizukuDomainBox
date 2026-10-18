package io.nekoshizuku.dnshe.ui

import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.nekoshizuku.dnshe.data.ApiException
import io.nekoshizuku.dnshe.data.MailApi
import io.nekoshizuku.dnshe.data.MailFolder
import io.nekoshizuku.dnshe.data.MailboxAccount
import io.nekoshizuku.dnshe.data.MailboxInfo
import io.nekoshizuku.dnshe.i18n.Str
import io.nekoshizuku.dnshe.i18n.t
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit

/**
 * 邮箱账号管理：列表 + 增删改。
 *
 * 这里只管**本机的 token**，不会去动服务器上的邮箱。
 */
@Composable
fun MailAccountsScreen(
    mailboxes: List<MailboxAccount>,
    currentId: String?,
    api: MailApi,
    onBack: () -> Unit,
    onSelect: (MailboxAccount) -> Unit,
    onEdit: (MailboxAccount?) -> Unit,
    onDelete: (MailboxAccount) -> Unit,
    onToast: (String) -> Unit,
) {
    var confirming by remember { mutableStateOf<MailboxAccount?>(null) }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = t(Str.MailAccounts),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = t(Str.Back), modifier = Modifier.size(24.dp))
                    }
                },
                color = MiuixTheme.colorScheme.background,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEdit(null) }) {
                Icon(MiuixIcons.Add, contentDescription = t(Str.MailAddAccount), modifier = Modifier.size(24.dp))
            }
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            Text(
                text = t(Str.MailAccountsDesc),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (mailboxes.isEmpty()) {
                EmptyState(
                    text = t(Str.MailNoAccount),
                    actionLabel = t(Str.MailAddAccount),
                    onAction = { onEdit(null) },
                )
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(mailboxes, key = { it.id }) { mb ->
                    MailboxRow(
                        mailbox = mb,
                        isCurrent = mb.id == currentId,
                        onClick = { onSelect(mb) },
                        onEdit = { onEdit(mb) },
                        onDelete = { confirming = mb },
                    )
                }
            }
        }
    }

    confirming?.let { target ->
        ConfirmDialog(
            title = t(Str.MailDeleteAccount),
            text = t(Str.MailDeleteAccountDesc) + "\n\n" + target.address,
            confirmLabel = t(Str.Delete),
            destructive = true,
            onConfirm = {
                confirming = null
                onDelete(target)
            },
            onDismiss = { confirming = null },
        )
    }
}

@Composable
private fun MailboxRow(
    mailbox: MailboxAccount,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = mailbox.address.ifBlank { mailbox.name },
                        fontSize = 15.sp,
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isCurrent) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                    )
                    if (isCurrent) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(MiuixTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 7.dp, vertical = 1.dp),
                        ) {
                            Text(
                                t(Str.MailCurrent),
                                fontSize = 10.sp,
                                color = MiuixTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = mailbox.maskedToken,
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                if (mailbox.memo.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = mailbox.memo,
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(MiuixIcons.Edit, contentDescription = t(Str.Edit), modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(
                    MiuixIcons.Delete,
                    contentDescription = t(Str.Delete),
                    modifier = Modifier.size(18.dp),
                    tint = MiuixTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * 添加 / 编辑邮箱的对话框。
 *
 * 关键：保存时会**自动探测 mailboxId**（用户通常只知道 token 和地址）。
 */


/**
 * 修正 token 的常见误输入。
 *
 * 中文输入法 / 模拟器会把 `_` 打成 `-`，
 * 而 mail.v.cd 的 token 一定是 `lq_` 前缀。
 */
private fun fixToken(raw: String): String {
    // token 只含 lq_ 前缀 + base64url 字符。
    // 中文输入法/模拟器会把 _ 打成 -，这里统一纠正。
    val c = normalizeInput(raw).replace('-', '_')
    return if (c.length > 3 && c.startsWith("lq", ignoreCase = true) && c[2] != '_') {
        "lq_" + c.substring(3)
    } else c
}

/**
 * 修正 mailboxId 的常见误输入。
 *
 * 中文输入法 / 模拟器会把 `_` 打成 `-` 或空格，
 * 而服务端的 ID 一定是 `mbx_` 前缀，所以直接纠正。
 */
private fun fixMailboxId(raw: String): String {
    var c = normalizeInput(raw)
    // 把前缀后面第一个非字母数字字符统一成 _
    if (c.length > 3 && c.startsWith("mbx", ignoreCase = true)) {
        val rest = c.substring(3)
        // 跳过可能的分隔符
        val body = rest.dropWhile { !it.isLetterOrDigit() }
        c = "mbx_" + body
    }
    return c
}

@Composable
fun MailboxEditorDialog(
    initial: MailboxAccount?,
    api: MailApi,
    onSave: (MailboxAccount) -> Unit,
    onDismiss: () -> Unit,
    onToast: (String) -> Unit,
) {
    val isNew = initial == null
    var address by remember { mutableStateOf(initial?.address ?: "") }
    var token by remember { mutableStateOf(initial?.token ?: "") }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var mailboxId by remember { mutableStateOf(initial?.mailboxId ?: "") }
    var memo by remember { mutableStateOf(initial?.memo ?: "") }
    var busy by remember { mutableStateOf(false) }
    var show by remember { mutableStateOf(true) }
    /** 非空时显示「选一个邮箱」列表 */
    var picked by remember { mutableStateOf<List<MailboxInfo>?>(null) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    // 从剪贴板一键填入 token（token 很长，手打很痛苦）
    fun pasteToken() {
        val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val text = cm?.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(ctx)?.toString()?.trim().orEmpty()
        if (text.isBlank()) {
            onToast(t(Str.ClipboardNoKey))
            return
        }
        // 只取第一行，避免用户复制了一大段
        token = text.lineSequence().firstOrNull { it.trim().startsWith("lq_") }?.trim()
            ?: text.lineSequence().first().trim()
        onToast(t(Str.Copied))
    }

    fun save() {
        val cleanToken = fixToken(token)
        if (cleanToken.isBlank()) {
            onToast(t(Str.MailTokenRequired))
            return
        }
        // token 必须以 lq_ 开头（提前拦截，省得白跑一次网络）
        if (!cleanToken.startsWith("lq_")) {
            onToast(t(Str.MailTokenInvalid))
            return
        }
        busy = true
        scope.launch {
            var resolvedId = normalizeInput(mailboxId)
            var resolvedAddr = normalizeInput(address)

            // 没填 mailboxId 就向官方 /me/mailboxes 要一次
            if (resolvedId.isBlank() || resolvedAddr.isBlank()) {
                val boxes = runCatching { api.listAllMyMailboxes(cleanToken) }
                    .getOrElse { e ->
                        busy = false
                        if (e is ApiException && e.httpStatus == 401) {
                            onToast(t(Str.MailVerifyFail))
                        } else {
                            onToast(t(Str.MailLoadFailed) + ": " + (e.message ?: ""))
                        }
                        return@launch
                    }

                if (boxes.isEmpty()) {
                    busy = false
                    onToast(t(Str.MailNoMailboxOnToken))
                    return@launch
                }

                // 地址能对上就用那个
                val matched = boxes.firstOrNull {
                    it.address.equals(resolvedAddr, ignoreCase = true)
                }
                when {
                    matched != null -> {
                        resolvedId = matched.id
                        resolvedAddr = matched.address
                    }
                    resolvedAddr.isBlank() && boxes.size == 1 -> {
                        resolvedId = boxes[0].id
                        resolvedAddr = boxes[0].address
                    }
                    else -> {
                        // 多个邮箱又对不上地址，弹选择框
                        busy = false
                        picked = boxes
                        return@launch
                    }
                }
            }

            busy = false
            onSave(
                (initial ?: MailboxAccount()).copy(
                    name = name.trim().ifBlank { resolvedAddr.substringBefore("@") },
                    address = resolvedAddr,
                    token = cleanToken,
                    mailboxId = resolvedId,
                    memo = memo.trim(),
                )
            )
            show = false
            onDismiss()
        }
    }

    /** 令牌下有多个邮箱时，让用户挑一个 */
    fun savePicked(box: MailboxInfo) {
        picked = null
        busy = true
        scope.launch {
            busy = false
            onSave(
                (initial ?: MailboxAccount()).copy(
                    name = name.trim().ifBlank { box.address.substringBefore("@") },
                    address = box.address,
                    token = fixToken(token),
                    mailboxId = box.id,
                    memo = memo.trim(),
                )
            )
            show = false
            onDismiss()
        }
    }

    // 令牌下有多个邮箱：先让用户挑一个
    picked?.let { boxes ->
        OverlayDialog(
            show = true,
            title = t(Str.MailPickMailbox),
            renderInRootScaffold = true,
            onDismissRequest = { picked = null },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = t(Str.MailPickMailboxHint, boxes.size),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(10.dp))
                boxes.forEach { box ->
                    TextButton(
                        text = box.address + if (box.quotaMb > 0) "  ·  ${box.quotaMb} MB" else "",
                        onClick = { savePicked(box) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(6.dp))
                TextButton(
                    text = t(Str.Cancel),
                    onClick = { picked = null },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        return
    }

    if (!show) return

    OverlayDialog(
        show = show,
        title = if (isNew) t(Str.MailAddAccount) else t(Str.MailEditAccount),
        renderInRootScaffold = true,
        onDismissRequest = { show = false; onDismiss() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
        ) {
            // 按钮放顶部：键盘弹出时会遮住底部，放下面点不到
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    text = t(Str.Cancel),
                    onClick = { show = false; onDismiss() },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = if (busy) "…" else t(Str.Save),
                    onClick = { save() },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            AppTextField(
                value = address,
                onValueChange = { address = normalizeInput(it) },
                label = t(Str.MailAddress),
                hint = t(Str.MailAddressHint),
            )
            Spacer(Modifier.height(10.dp))
            AppTextField(
                value = token,
                onValueChange = { token = fixToken(it) },
                label = t(Str.MailToken),
                hint = t(Str.MailTokenHint),
                isSecret = true,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    text = t(Str.PasteFromClipboard),
                    onClick = { pasteToken() },
                )
            }
            Spacer(Modifier.height(4.dp))
            AppTextField(
                value = mailboxId,
                onValueChange = { raw -> mailboxId = fixMailboxId(raw) },
                label = t(Str.MailMailboxId),
                hint = t(Str.MailMailboxIdHint),
            )
            Spacer(Modifier.height(10.dp))
            AppTextField(
                value = name,
                onValueChange = { name = it },
                label = t(Str.MailNameOptional),
            )
            Spacer(Modifier.height(16.dp))


        }
    }
}
