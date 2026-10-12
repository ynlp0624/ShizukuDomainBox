package io.nekoshizuku.dnshe.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 全局浮层宿主。
 *
 * 桌面版把对话框渲染在窗口最上层（盖住侧边栏），
 * 这样任何页面都能用 LocalDialogHost.current.show { 对话框 } 弹东西。
 */
class OverlayHost {
    var dialog: (@Composable () -> Unit)? by mutableStateOf(null)
        private set

    fun show(content: @Composable () -> Unit) {
        dialog = content
    }

    fun dismiss() {
        dialog = null
    }
}

val LocalOverlayHost = staticCompositionLocalOf<OverlayHost> {
    error("OverlayHost 没有提供")
}
