"""给空状态补上正式矢量图标（替代原来的 emoji），并清理多余空格。"""
import io

REPL = [
    # 安卓
    (r"D:\dnshe\app\src\main\java\io\nekoshizuku\dnshe\ui\ToolsScreens.kt", [
        ("EmptyState( text = t(Str.Empty))",
         "EmptyState(text = t(Str.Empty), icon = { StateIcon(painterResource(R.drawable.ic_chart)) })"),
        ("EmptyState( text = t(Str.NoAccountForTool))",
         "EmptyState(text = t(Str.NoAccountForTool), icon = { StateIcon(Icons.Filled.Lock) })"),
        ("EmptyState( text = t(Str.KeysEmpty), actionLabel = t(Str.CreateKey), onAction = { creating = true })",
         "EmptyState(\n                    text = t(Str.KeysEmpty),\n                    actionLabel = t(Str.CreateKey),\n                    onAction = { creating = true },\n                    icon = { StateIcon(painterResource(R.drawable.ic_key)) },\n                )"),
        ("EmptyState( text = t(Str.UpgradeEmpty))",
         "EmptyState(text = t(Str.UpgradeEmpty), icon = { StateIcon(painterResource(R.drawable.ic_upgrade)) })"),
    ]),
    (r"D:\dnshe\app\src\main\java\io\nekoshizuku\dnshe\ui\DomainsScreen.kt", [
        ("EmptyState( text = t(Str.RecordsEmpty), actionLabel = t(Str.AddRecord), onAction = { creatingRecord = true })",
         "EmptyState(\n                            text = t(Str.RecordsEmpty),\n                            actionLabel = t(Str.AddRecord),\n                            onAction = { creatingRecord = true },\n                            icon = { StateIcon(painterResource(R.drawable.ic_dns)) },\n                        )"),
    ]),
    (r"D:\dnshe\app\src\main\java\io\nekoshizuku\dnshe\ui\SettingsScreen.kt", [
        ("EmptyState( text = t(Str.LogsEmpty))",
         "EmptyState(text = t(Str.LogsEmpty), icon = { StateIcon(Icons.Filled.List) })"),
    ]),
    # PC 端（logo 用 painterResource("app-logo.png") 的 WrappedLogo）
    (r"D:\dnshe-pc\desktop\src\main\kotlin\io\nekoshizuku\dnshe\desktop\ui\ToolsView.kt", [
        ("EmptyState(emoji = \"\", text = t(Str.Empty))", "EmptyState(text = t(Str.Empty))"),
    ]),
]

for path, pairs in REPL:
    text = io.open(path, "r", encoding="utf-8").read()
    total = 0
    for old, new in pairs:
        if old in text:
            text = text.replace(old, new)
            total += 1
        else:
            print("  [miss]", path.split("\\")[-1], "->", old[:60])
    io.open(path, "w", encoding="utf-8", newline="\n").write(text)
    print(path.split("\\")[-1], "replaced", total)
