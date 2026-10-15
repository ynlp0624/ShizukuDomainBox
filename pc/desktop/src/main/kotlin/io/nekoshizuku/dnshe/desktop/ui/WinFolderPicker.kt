package io.nekoshizuku.dnshe.desktop.ui

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.ptr.PointerByReference
import java.awt.Window
import java.io.File

/**
 * Windows 原生「选择文件夹」对话框。
 *
 * **为什么不用 Swing 的 JFileChooser**：
 * 在 jpackage 打出来的精简运行时里，JFileChooser 会在内部抛
 * `Cannot invoke "java.awt.Image.getWidth(...)" because "<local6>" is null`
 * （它去取系统图标时拿到 null），结果是对话框根本弹不出来。
 * 用原生的 SHBrowseForFolder 既躲开这个坑，观感也和系统一致。
 */
object WinFolderPicker {

    private const val BIF_RETURNONLYFSDIRS = 0x0001
    private const val BIF_NEWDIALOGSTYLE = 0x0040   // 新版可调整大小的对话框
    private const val BIF_EDITBOX = 0x0010          // 带路径输入框

    @Structure.FieldOrder(
        "hwndOwner", "pidlRoot", "pszDisplayName", "lpszTitle",
        "ulFlags", "lpfn", "lParam", "iImage",
    )
    class BrowseInfo : Structure() {
        @JvmField var hwndOwner: WinDef.HWND? = null
        @JvmField var pidlRoot: Pointer? = null
        @JvmField var pszDisplayName: Pointer? = null
        @JvmField var lpszTitle: WString? = null
        @JvmField var ulFlags: WinDef.UINT = WinDef.UINT(0)
        @JvmField var lpfn: Pointer? = null
        @JvmField var lParam: WinDef.LPARAM = WinDef.LPARAM(0)
        @JvmField var iImage: Int = 0
    }

    private interface Shell32 : com.sun.jna.Library {
        fun SHBrowseForFolderW(bi: BrowseInfo): Pointer?
        fun SHGetPathFromIDListW(pidl: Pointer, pszPath: Pointer): Boolean
    }

    private val shell32: Shell32 by lazy {
        Native.load("shell32", Shell32::class.java)
    }

    /**
     * 弹出原生文件夹选择框。
     * @param owner 父窗口（可为 null）
     * @param title 对话框上方的提示文字
     * @param initial 初始目录
     * @return 选中的目录；取消或失败返回 null
     */
    fun choose(owner: Window?, title: String, initial: File? = null): File? {
        // 有的场景要先初始化 COM（SHBrowseForFolder 内部会用到）
        var needUninit = false
        runCatching {
            val hr = Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED)
            needUninit = hr == com.sun.jna.platform.win32.WinError.S_OK
        }

        try {
            val displayBuf = Memory(260L * 2)   // MAX_PATH 宽字符
            val bi = BrowseInfo().apply {
                hwndOwner = owner?.let {
                    runCatching { WinDef.HWND(Native.getWindowPointer(it)) }.getOrNull()
                }
                pidlRoot = null
                pszDisplayName = displayBuf
                lpszTitle = WString(title)
                ulFlags = WinDef.UINT((BIF_RETURNONLYFSDIRS or BIF_NEWDIALOGSTYLE or BIF_EDITBOX).toLong())
                lpfn = null
                lParam = WinDef.LPARAM(0)
                iImage = 0
            }
            bi.write()

            val pidl = shell32.SHBrowseForFolderW(bi) ?: return null
            try {
                val out = Memory(260L * 2)
                if (!shell32.SHGetPathFromIDListW(pidl, out)) return null
                val path = out.getWideString(0)
                val f = File(path)
                return if (f.isDirectory) f else null
            } finally {
                Ole32.INSTANCE.CoTaskMemFree(pidl)
            }
        } catch (t: Throwable) {
            // 失败就让上层走备选方案
            return null
        } finally {
            if (needUninit) runCatching { Ole32.INSTANCE.CoUninitialize() }
        }
    }
}
