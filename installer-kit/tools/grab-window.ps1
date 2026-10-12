param(
    [string]$Out = "D:\installer-kit\build\shot.png",
    [string]$ProcName = "Setup",
    # 按窗口标题找窗口（子串匹配）。WinForms / Win32 对话框常常是被 powershell.exe、
    # mshta.exe 这类宿主托管的，光按进程名会挑错 —— 这时用 -Title "安装 我的程序"。
    [string]$Title = "",
    # 点击序列：相对**窗口截图像素**的坐标，成对给出（和 -Out 出来的图对得上）。
    #   -Click "946,690"                点一次
    #   -Click "946,690,946,690"        点两次（比如连点「下一步」走过两步）
    #
    # ⚠️ 收成字符串是故意的：用 `powershell -File` 调用时，`-Click 946,690` 会被
    #    当成**一个字符串**"946,690"传进来，绑不到 [int[]] 上 —— 参数绑不上不会报错，
    #    结果是点击循环压根不执行（踩过，白查了半天）。
    [string]$Click = "",
    [int]$ClickDelayMs = 1500,
    # 默认用 PostMessage 直接把点击投递给窗口（不碰真实鼠标、不用抢前台）；
    # 万一某些控件不吃 PostMessage，加 -Physical 改用真实鼠标事件。
    [switch]$Physical
)

Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class WinGrab {
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT lpRect);
    [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr hWnd, out RECT lpRect);
    [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr hWnd, ref POINT lpPoint);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int X, int Y);
    [DllImport("user32.dll")] public static extern void mouse_event(uint dwFlags, uint dx, uint dy, uint dwData, IntPtr dwExtraInfo);
    [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam);
    [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr hWnd, IntPtr hWndInsertAfter, int X, int Y, int cx, int cy, uint uFlags);
    public static readonly IntPtr HWND_TOPMOST = new IntPtr(-1);
    public static readonly IntPtr HWND_NOTOPMOST = new IntPtr(-2);
    public const uint SWP_NOMOVE = 0x0002;
    public const uint SWP_NOSIZE = 0x0001;
    public const uint WM_MOUSEMOVE = 0x0200;
    public const uint WM_LBUTTONDOWN = 0x0201;
    public const uint WM_LBUTTONUP = 0x0202;
    public const uint MK_LBUTTON = 0x0001;
    public const uint LEFTDOWN = 0x0002;
    public const uint LEFTUP = 0x0004;
    public static IntPtr XY(int x, int y) { return (IntPtr)((y << 16) | (x & 0xFFFF)); }
    [StructLayout(LayoutKind.Sequential)]
    public struct RECT { public int Left; public int Top; public int Right; public int Bottom; }
    [StructLayout(LayoutKind.Sequential)]
    public struct POINT { public int X; public int Y; }
}
"@

$p = $null
if ($Title) {
    # 按窗口标题找 —— WinForms 向导是被 powershell.exe 托管的，
    # 光按进程名找会挑到「最新启动的那个 powershell」，也就是本脚本自己。
    $p = Get-Process -ErrorAction SilentlyContinue |
        Where-Object { $_.MainWindowHandle -ne 0 -and $_.MainWindowTitle -like "*$Title*" } |
        Sort-Object StartTime | Select-Object -Last 1
} else {
    $p = Get-Process -Name $ProcName -ErrorAction SilentlyContinue |
        Where-Object { $_.MainWindowHandle -ne 0 } |
        Sort-Object StartTime | Select-Object -Last 1
}
if (-not $p) { Write-Host "找不到窗口（ProcName=$ProcName Title=$Title）"; exit 2 }
$hwnd = $p.MainWindowHandle
Write-Host "抓到窗口：$($p.ProcessName) / $($p.MainWindowTitle)"

[void][WinGrab]::SetForegroundWindow($hwnd)
Start-Sleep -Milliseconds 1200

$r = New-Object WinGrab+RECT
[void][WinGrab]::GetWindowRect($hwnd, [ref]$r)
$w = $r.Right - $r.Left
$h = $r.Bottom - $r.Top
if ($w -le 0 -or $h -le 0) { Write-Host "bad window rect"; exit 3 }

# 客户区原点：窗口截图坐标 → 客户区坐标 要减掉这个偏移
$origin = New-Object WinGrab+POINT
[void][WinGrab]::ClientToScreen($hwnd, [ref]$origin)
$offX = $origin.X - $r.Left
$offY = $origin.Y - $r.Top

# 把 "946,690,946,690" 这种字符串拆成坐标序列
$coords = @()
if ($Click.Trim().Length -gt 0) {
    $coords = @($Click -split '[,;\s]+' | Where-Object { $_.Trim().Length -gt 0 } | ForEach-Object { [int]$_ })
}
if ($coords.Count % 2 -ne 0) { Write-Host "点击坐标要成对出现，收到 $($coords.Count) 个"; exit 4 }

for ($i = 0; $i + 1 -lt $coords.Count; $i += 2) {
    $cx = $coords[$i] - $offX
    $cy = $coords[$i + 1] - $offY
    Write-Host ("click window({0},{1}) -> client({2},{3})" -f $coords[$i], $coords[$i + 1], $cx, $cy)

    if ($Physical) {
        # Compose/Skiko 不吃 PostMessage 投递的鼠标消息，只能来真的：
        # 先把窗口临时置顶（否则可能点到别的窗口上），再移光标 + 按下抬起。
        [void][WinGrab]::SetWindowPos($hwnd, [WinGrab]::HWND_TOPMOST, 0, 0, 0, 0,
            [WinGrab]::SWP_NOMOVE -bor [WinGrab]::SWP_NOSIZE)
        Start-Sleep -Milliseconds 400
        [void][WinGrab]::SetForegroundWindow($hwnd)
        Start-Sleep -Milliseconds 400
        [void][WinGrab]::SetCursorPos($r.Left + $Click[$i], $r.Top + $Click[$i + 1])
        Start-Sleep -Milliseconds 200
        [WinGrab]::mouse_event([WinGrab]::LEFTDOWN, 0, 0, 0, [IntPtr]::Zero)
        Start-Sleep -Milliseconds 80
        [WinGrab]::mouse_event([WinGrab]::LEFTUP, 0, 0, 0, [IntPtr]::Zero)
        Start-Sleep -Milliseconds 300
        [void][WinGrab]::SetWindowPos($hwnd, [WinGrab]::HWND_NOTOPMOST, 0, 0, 0, 0,
            [WinGrab]::SWP_NOMOVE -bor [WinGrab]::SWP_NOSIZE)
    } else {
        [void][WinGrab]::PostMessage($hwnd, [WinGrab]::WM_MOUSEMOVE, [IntPtr]::Zero, [WinGrab]::XY($cx, $cy))
        Start-Sleep -Milliseconds 120
        [void][WinGrab]::PostMessage($hwnd, [WinGrab]::WM_LBUTTONDOWN, [IntPtr][WinGrab]::MK_LBUTTON, [WinGrab]::XY($cx, $cy))
        Start-Sleep -Milliseconds 80
        [void][WinGrab]::PostMessage($hwnd, [WinGrab]::WM_LBUTTONUP, [IntPtr]::Zero, [WinGrab]::XY($cx, $cy))
    }
    Start-Sleep -Milliseconds $ClickDelayMs
    [void][WinGrab]::GetWindowRect($hwnd, [ref]$r)
    $w = $r.Right - $r.Left
    $h = $r.Bottom - $r.Top
}

$bmp = New-Object System.Drawing.Bitmap($w, $h)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.CopyFromScreen($r.Left, $r.Top, 0, 0, $bmp.Size)
$bmp.Save($Out, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose()
$bmp.Dispose()
Write-Host ("saved {0}  {1}x{2}" -f $Out, $w, $h)
exit 0
