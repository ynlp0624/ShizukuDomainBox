package io.nekoshizuku.kit

/**
 * jpackage 启动器配置（`app\<名字>.cfg`）的文本处理。
 *
 * 这是整个工具**最关键的一小块**：jpackage 启动器按「和自己同名的 .cfg」读配置，
 * 而 cfg 里
 *
 * ```ini
 * [Application]
 * app.mainclass=<入口类>
 * app.classpath=$APPDIR\xxx.jar
 * ```
 *
 * 决定入口。所以只要造出 `Setup.cfg` / `Uninstall.cfg`（改 mainclass + 追加 classpath +
 * 追加 `[ArgOptions] arguments=...`），同一个 exe 就多出安装/卸载两种身份，
 * **而产品自己那份 cfg 一个字都不用动**。
 *
 * ⚠️ 原有的 `[Application]` / `[JavaOptions]` 段必须原样保留，只在末尾追加 `[ArgOptions]`。
 */
object JpackageCfg {

    const val SECTION_APPLICATION = "[Application]"
    const val SECTION_ARG_OPTIONS = "[ArgOptions]"
    const val KEY_MAINCLASS = "app.mainclass"
    const val KEY_CLASSPATH = "app.classpath"
    const val KEY_ARGUMENTS = "arguments"

    private const val CRLF = "\r\n"

    private fun toLines(text: String): MutableList<String> =
        text.replace("\r\n", "\n").split("\n").toMutableList()

    private fun join(lines: List<String>): String = lines.joinToString(CRLF).trimEnd() + CRLF

    fun readMainClass(text: String): String? =
        toLines(text).firstOrNull { it.trimStart().startsWith("$KEY_MAINCLASS=", true) }
            ?.substringAfter('=')?.trim()?.takeIf { it.isNotEmpty() }

    fun readClasspath(text: String): List<String> =
        toLines(text).filter { it.trimStart().startsWith("$KEY_CLASSPATH=", true) }
            .map { it.substringAfter('=').trim() }
            .filter { it.isNotEmpty() }

    fun hasArgOptions(text: String): Boolean =
        toLines(text).any { it.trim().equals(SECTION_ARG_OPTIONS, true) }

    /**
     * 把 `[Application]` 段的 `app.mainclass` 换成我们的入口。
     * 段内没有这一行时插到 `[Application]` 后面；连 `[Application]` 都没有就原样返回。
     */
    fun overrideMainClass(text: String, mainClass: String): String {
        if (mainClass.isBlank()) return text
        val lines = toLines(text)
        val idx = lines.indexOfFirst { it.trimStart().startsWith("$KEY_MAINCLASS=", true) }
        if (idx >= 0) {
            lines[idx] = "$KEY_MAINCLASS=$mainClass"
        } else {
            val appIdx = lines.indexOfFirst { it.trim().equals(SECTION_APPLICATION, true) }
            if (appIdx < 0) return text
            lines.add(appIdx + 1, "$KEY_MAINCLASS=$mainClass")
        }
        return join(lines)
    }

    /**
     * 追加 classpath 条目（形如 `$APPDIR\yyy.jar`），已经在里面的不重复加。
     * 插在最后一条 `app.classpath=` 后面，保证仍留在 `[Application]` 段内。
     */
    fun appendClasspath(text: String, entries: List<String>): String {
        val wanted = entries.map { it.trim() }.filter { it.isNotEmpty() }
        if (wanted.isEmpty()) return text

        val lines = toLines(text)
        val existing = lines.filter { it.trimStart().startsWith("$KEY_CLASSPATH=", true) }
            .map { it.substringAfter('=').trim() }
        val missing = wanted.filter { w -> existing.none { it.equals(w, ignoreCase = true) } }
        if (missing.isEmpty()) return text

        var anchor = lines.indexOfLast { it.trimStart().startsWith("$KEY_CLASSPATH=", true) }
        if (anchor < 0) anchor = lines.indexOfFirst { it.trimStart().startsWith("$KEY_MAINCLASS=", true) }
        if (anchor < 0) anchor = lines.indexOfFirst { it.trim().equals(SECTION_APPLICATION, true) }
        if (anchor < 0) return text

        lines.addAll(anchor + 1, missing.map { "$KEY_CLASSPATH=$it" })
        return join(lines)
    }

    /**
     * 追加 `[ArgOptions] arguments=...`。
     * 已经有 `[ArgOptions]` 段就只替换它的 `arguments=` 行（避免重复段）。
     */
    fun appendArguments(text: String, arguments: String): String {
        if (arguments.isBlank()) return text
        val lines = toLines(text)
        val sectionIdx = lines.indexOfFirst { it.trim().equals(SECTION_ARG_OPTIONS, true) }

        if (sectionIdx >= 0) {
            val argIdx = (sectionIdx + 1 until lines.size)
                .firstOrNull { lines[it].trimStart().startsWith("$KEY_ARGUMENTS=", true) }
            if (argIdx != null) lines[argIdx] = "$KEY_ARGUMENTS=$arguments"
            else lines.add(sectionIdx + 1, "$KEY_ARGUMENTS=$arguments")
            return join(lines)
        }

        val body = lines.joinToString(CRLF).trimEnd()
        return body + CRLF + CRLF + SECTION_ARG_OPTIONS + CRLF + "$KEY_ARGUMENTS=$arguments" + CRLF
    }

    /**
     * 一次性生成「另一种身份」的 cfg：
     * 原 cfg 原样保留 → 换 mainclass → 补 classpath → 追加 arguments。
     */
    fun deriveIdentity(
        baseCfg: String,
        mainClass: String,
        extraClasspath: List<String>,
        arguments: String,
    ): String {
        var text = baseCfg
        text = overrideMainClass(text, mainClass)
        text = appendClasspath(text, extraClasspath)
        text = appendArguments(text, arguments)
        return text
    }
}
