package io.nekoshizuku.kit

import java.io.File

/**
 * 系统路径。全部走环境变量，取不到就退回合理默认值——
 * 这样在测试进程里也能跑（哪怕环境变量被清掉）。
 */
object WinEnv {

    private fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

    val userHome: File get() = File(System.getProperty("user.home") ?: "C:\\")

    fun programFiles(): File = File(env("ProgramFiles") ?: "C:\\Program Files")

    fun localAppData(): File = File(env("LOCALAPPDATA") ?: userHome.path)

    fun roamingAppData(): File = File(env("APPDATA") ?: userHome.path)

    fun userProfile(): File = File(env("USERPROFILE") ?: userHome.path)

    fun publicDir(): File = File(env("PUBLIC") ?: "C:\\Users\\Public")

    fun programData(): File = File(env("ProgramData") ?: "C:\\ProgramData")

    /** 临时目录（IExpress 自解压出来的目录也在这儿） */
    fun tempDir(): File = File(System.getProperty("java.io.tmpdir") ?: "C:\\Windows\\Temp")

    fun desktopDir(perUser: Boolean): File =
        if (perUser) File(userProfile(), "Desktop")
        else File(publicDir(), "Desktop")

    fun startMenuDir(perUser: Boolean): File =
        if (perUser) File(roamingAppData(), "Microsoft\\Windows\\Start Menu\\Programs")
        else File(programData(), "Microsoft\\Windows\\Start Menu\\Programs")
}
