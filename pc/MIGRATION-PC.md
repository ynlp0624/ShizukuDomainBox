# PC 端 Miuix 迁移记录

> 工程 `D:\dnshe-pc` · 备份 `D:\dnshe-pc-backup-material3`
> 与安卓端使用**同一套工具链和同一套迁移策略**

---

## 一、工具链（与安卓端一致）

| 组件 | 旧 | 新 |
|---|---|---|
| Gradle | 8.7 | **9.8.1** |
| JDK | 17 | **21.0.2** |
| Kotlin | 2.0.20 | **2.4.20** |
| Compose Multiplatform | 1.7.0 | **1.12.0** |
| kotlinx-serialization | 1.7.3 | 1.9.0 |
| kotlinx-coroutines | 1.8.1 | 1.10.2 |
| Miuix | — | **0.9.4** |

工具位置：
```
D:\Android\gradle-9.8.1\
D:\Android\jdk-21.0.2\
```

构建命令：
```bat
set JAVA_HOME=D:\Android\jdk-21.0.2
D:\Android\gradle-9.8.1\bin\gradle.bat -p D:\dnshe-pc :desktop:run
```

## 二、迁移策略：同样的「咽喉式迁移」

桌面端的所有页面也都通过 `desktop/ui/Common.kt` 取样式，所以**只改这一层**，
`DomainsView` / `AccountsView` / `ToolsView` / `SettingsView` 等 2500 行页面代码
不用动就整体换皮了。

### 已完成的文件

| 文件 | 状态 | 说明 |
|---|---|---|
| `ui/Theme.kt` | ✅ | Material3 + Miuix 双主题嵌套，品牌蓝作 Miuix primary |
| `ui/Common.kt` | ✅ | 22 个公共组件全部换成 Miuix 实现 |
| `ui/AppRoot.kt` | ✅ | 侧边栏图标换 `miuix-icons`，取色换 Miuix |

### Common.kt 组件映射

| 原组件 | 现实现 |
|---|---|
| `SectionTitle` | Miuix `Text` + `onSurfaceVariantSummary` 配色 |
| `AppCard` | Miuix `Card` + **显式 `insideMargin`**（默认是 0！） |
| `InfoRow` | Miuix `Text` / `IconButton` |
| `StatusPill` | 自绘 Box + Miuix 取色 |
| `AppTextField` | Miuix `TextField` + `useLabelAsPlaceholder` |
| `SettingSwitchRow` | Miuix `Switch` |
| `LoadingBox` / `ErrorBox` | Miuix `CircularProgressIndicator` / `TextButton` |
| `InlineMessage` | Miuix `Card` + `CardDefaults.defaultColors(color=...)` |
| `OverlayDialog`（自研） | 保留自定义浮层结构，取色与圆角改 Miuix |
| `DialogButtons` | Miuix `Button` / `TextButton` |
| `Divider` | Miuix `HorizontalDivider` |
| `ToolbarButton` | Miuix `TextButton` |

## 三、桌面端特有的注意点

### 1. 名字冲突

PC 端**自己实现过一个 `OverlayDialog`**（窗口内浮层，不是独立窗口），
而 Miuix 也有一个同名的 `OverlayDialog`。
处理方式：这个文件里**不 import Miuix 的那个**，继续用自研的，
只把取色和圆角改成 Miuix 的。已在代码注释里标明。

### 2. 桌面布局与手机不同

安卓是底部导航，PC 是**左侧竖栏**。Miuix 有 `NavigationRail` 可以用，
但它的内容区带 `scrollState`，`Spacer(Modifier.weight(1f))` 这种"底部固定区"的写法
在里面不一定生效。
**当前做法**：保留桌面化的自定义侧栏布局，只把**图标换成 `miuix-icons`、
取色换成 Miuix**，风险最小、观感也够。

### 3. Miuix Card 的两个坑（和安卓端一样）

- `InsideMargin` 默认 `PaddingValues(0.dp)` → 必须自己给内边距
- 内部 `propagateMinConstraints = true` 与 `Row.weight()` 组合会算小高度 → 避开

## 四、验证结果

| 项 | 结果 |
|---|---|
| `:desktop:compileKotlin` | ✅ BUILD SUCCESSFUL |
| `:desktop:run` 启动 | ✅ 窗口正常 |
| 实拍截图 | `screens/pc-miuix-01.png`（Common 换完）、`pc-miuix-02.png`（侧栏也换完） |
| 真实数据加载 | ✅ 6 个域名 + 配额 4/10 |

## 五、待完成

| 项 | 待办 |
|---|---|
| 顶部工具条 | 主界面顶部的「刷新 / 注册子域名」按钮区可换成 Miuix `TopAppBar`（带 actions） |
| 各页面内的 `MaterialTheme` 引用 | 还有约 200 处，按需逐步换 |
| 安装程序界面 | `InstallView.kt` / `UninstallView.kt` 也是 Compose，同样可以换 Miuix |
| 清理 | 全部迁完后可删 `compose.material3` 与 `compose.materialIconsExtended` 依赖 |
