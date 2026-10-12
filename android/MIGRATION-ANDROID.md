# 安卓端 Miuix 迁移 · 工具链升级记录

> 完成时间 2026-10-12 · 工程 `D:\dnshe` · 备份 `D:\dnshe-backup-v1.1.1`

---

## 一、最终工具链（已验证可出包 + 真机运行）

| 组件 | 旧 | 新 |
|---|---|---|
| Gradle | 8.7 | **9.8.1** |
| JDK | 17 | **21.0.2** |
| AGP | 8.6.1 | **9.4.1** |
| Kotlin | 2.0.20（独立插件） | **AGP 内置**（无需插件） |
| compileSdk | 34 | **37**（platform android-37.2） |
| targetSdk | 34 | 36 |
| minSdk | 26 | 26（不变） |
| Compose | BOM 2024.09.00 | **BOM 2026.09.00**（= compose 1.12.x） |
| Miuix | — | **0.9.4** |

APK 体积：7.47 MB → **11.26 MB**（Miuix + Compose 1.12 带来的增长）

## 二、新增的工具位置

```
D:\Android\gradle-9.8.1\                         Gradle 9.8.1
D:\Android\jdk-21.0.2\                           JDK 21.0.2（★ 必需）
D:\Android\Sdk\platforms\android-37.2\           SDK Platform 37.2
D:\Android\Sdk\build-tools\37.0.0\               Build Tools 37.0.0
```

## 三、踩到的坑（按遇到顺序）

### 1. 找不到 `platforms;android-37`
用 `sdkmanager --list` 才看到包名带小版本号：`android-37.0 / 37.1 / 37.2`。
**正确包名是 `platforms;android-37.2`**（错误提示里说的「for example 37.2」就是这个意思）。

### 2. AGP 9 内置 Kotlin，不能再应用 `org.jetbrains.kotlin.android`
报错：
```
The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since AGP 9.0.
```
**处理**：
- 根 `build.gradle.kts` 和模块里都删掉 `id("org.jetbrains.kotlin.android")`
- `android { kotlinOptions { } }` 改成顶层 `kotlin { compilerOptions { } }`
- `jvmTarget` **不用设**，默认跟随 `android.compileOptions.targetCompatibility`
- `org.jetbrains.kotlin.plugin.compose` 和 `plugin.serialization` **仍要保留**
- 想临时退回：`gradle.properties` 里设 `android.builtInKotlin=false` + `android.newDsl=false`

### 3. Miuix 的 AAR metadata 要求 compileSdk ≥ 37
```
Dependency 'top.yukonga.miuix.kmp:miuix-preference-android:0.9.4' requires ...
compile against version 37 or later of the Android APIs. :app is currently compiled against android-36.
```
即使只是引入依赖、还没用任何 Miuix 组件，这个检查也会触发。

### 4. JetBrains Compose 1.12.0 ↔ androidx.compose 1.12.0
依赖对齐关系（从 module metadata 里挖出来的）：
```
org.jetbrains.compose.foundation:foundation:1.12.0  →  androidx.compose.foundation:foundation:1.12.0
org.jetbrains.compose.ui:ui:1.12.0                  →  androidx.compose.ui:ui:1.12.0
org.jetbrains.compose.runtime:runtime:1.12.0        →  androidx.compose.runtime:runtime:1.12.0
```
用 `compose-bom:2026.09.00` 就能满足（里面已经是 1.12.x）。

### 5. `material-icons-core` 停在 1.7.8
Compose 1.12 时代这个库已经不再跟进版本，最高就是 1.7.8。
过渡期先加 `androidx.compose.material:material-icons-core:1.7.8` 顶着，
等 UI 换成 `miuix-icons` 后就可以删掉。

---

## 四、当前 `app/build.gradle.kts` 关键片段

```kotlin
plugins {
    id("com.android.application")
    // AGP 9 起 Kotlin 编译已内置，不再需要 org.jetbrains.kotlin.android
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    compileSdk = 37          // Miuix 强制要求
    defaultConfig {
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "2.0.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures { compose = true; buildConfig = true }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core:1.7.8")  // 过渡用

    // Miuix
    implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-icons:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-preference:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-squircle:0.9.4")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
```

## 五、构建命令（换了 JDK 和 Gradle）

```bat
set JAVA_HOME=D:\Android\jdk-21.0.2
set ANDROID_HOME=D:\Android\Sdk
set ANDROID_SDK_ROOT=D:\Android\Sdk

D:\Android\gradle-9.8.1\bin\gradle.bat -p D:\dnshe assembleRelease
```

## 六、验证结果

| 项 | 结果 |
|---|---|
| `:app:compileReleaseKotlin` | ✅ BUILD SUCCESSFUL |
| `assembleRelease` | ✅ 4m20s，11.26 MB |
| APK 信息 | versionCode 4 / versionName 2.0.0 / compileSdk 37 / targetSdk 36 |
| 签名 | ✅ V3.0，证书 SHA-256 `231774de…`（与旧版同一把钥匙） |
| Miuix 已打进包 | ✅ dex 里能搜到 `top/yukonga/miuix/kmp/*` 包 |
| 模拟器装机运行 | ✅ 无崩溃，旧界面正常显示真实 API 数据 |

## 七、UI 重写进度

### 已完成

| 文件 | 状态 | 说明 |
|---|---|---|
| `Theme.kt` | ✅ | Material3 + Miuix 双主题嵌套，可逐屏迁移；品牌蓝 #3E6FD9 作为 Miuix primary |
| `Common.kt` | ✅ | 15 个公共组件全改 Miuix；`ConfirmDialog` / `ChoiceDialog` 换成 `OverlayDialog` |
| `AppRoot.kt` | ✅ | 底部导航 → Miuix `NavigationBar` + `miuix-icons`；Snackbar → Miuix；挂载 `MiuixPopupHost()` |
| `DomainsScreen.kt` | ◐ | 域名页的 Scaffold / TopAppBar / FAB / IconButton 已换 Miuix；表单与对话框待办 |

**咽喉式迁移**是关键策略：所有页面都通过 `Common.kt` 里的 `AppCard` / `SectionTitle` /
`KeyValueRow` / `StatusPill` / `AppTextField` / `SettingSwitchRow` / `EmptyState` /
`ErrorBox` / `LoadingBox` 等取样式，所以只改几个全局文件，4000 行页面就整体变成了 HyperOS 观感。

### 弹窗方案（已验证）

Miuix 的 `OverlayDialog` 需要状态宿主才能渲染，两种接法：

1. 用 Miuix 的 `Scaffold` 包根（它自带 `MiuixPopupHost()`）
2. **我们的做法**：在 `AppRoot` 的根 `Box` 里直接挂
   ```kotlin
   import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost
   ...
   MiuixPopupHost()
   ```
   然后在 `OverlayDialog(..., renderInRootScaffold = false)` 里关掉「渲染到根 Scaffold」。
   这样不用改根布局的 insets 逻辑，风险最小。

效果：从底部滑上来的圆角卡片，选中项蓝色高亮 + 对勾，完全是 HyperOS 的样子。

### 底部导航图标映射（miuix-icons）

Miuix 的 `NavigationBarItem` 要求 `icon: ImageVector`，不能用 drawable，所以换成 miuix-icons：

| Tab | 图标 |
|---|---|
| 域名 | `MiuixIcons.Layers` |
| 账号 | `MiuixIcons.Contacts` |
| 工具 | `MiuixIcons.GridView` |
| 设置 | `MiuixIcons.Settings` |

引入方式（扩展属性，两个 import 都要）：
```kotlin
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Layers
```

**图标尺寸要显式给**：Miuix 的 `IconButton` 不会给内部 Icon 加尺寸约束，
不写 `Modifier.size(24.dp)` 图标会显示得很小。

### 待完成

| 项 | 待办 |
|---|---|
| `AccountsScreen` / `ToolsScreens` / `SettingsScreen` | 页面内 Scaffold/TopAppBar/FAB/Button 换 Miuix（约 240 处引用） |
| `RootDomainUi.kt` | 根域名选择弹窗 → `OverlayListPopup`（40 处） |
| 详情页顶栏 | 配额/密钥/Whois/升级/日志/关于 → Miuix `SmallTopAppBar` |
| 清理 | 全部迁完后删 `material3` 与 `material-icons-core` 依赖（APK 能瘦不少） |

### Common.kt 迁移要点

- Miuix 的 `Card` **`InsideMargin` 默认是 `PaddingValues(0.dp)`**，必须自己给内边距，
  否则内容全部贴边。已统一用 `PaddingValues(horizontal = 14.dp, vertical = 12.dp)`。
- Miuix 的 `Card` 内部是带 `propagateMinConstraints = true` 的 Box，
  和外层 `Row` 的 `Modifier.weight(1f)` 组合时**高度会被算小**，文字溢出卡片。
  `InfoTile` 因此改成手写 `Box + clip + background`，不用 Card。
- Miuix `TextField` 没有 `placeholder`，改用 `label` + `useLabelAsPlaceholder = true`
  更贴近 HyperOS 的观感。
- Miuix `Button` / `TextButton` 的 `TextButton` 第一个参数是 `text: String`，
  不是尾随 lambda 的内容槽。
- 颜色取 `MiuixTheme.colorScheme.*`；说明文字用 `onSurfaceVariantSummary`
  （不要用 Material 的 `onSurfaceVariant`，Miuix 里没有这个字段）。

### 验证方式

每改一个文件就跑：

```bat
set JAVA_HOME=D:\Android\jdk-21.0.2
D:\Android\gradle-9.8.1\bin\gradle.bat -p D:\dnshe assembleRelease
adb install -r app\build\outputs\apk\release\app-release.apk
adb shell am force-stop io.nekoshizuku.dnshe
adb shell am start -n io.nekoshizuku.dnshe/.MainActivity
adb shell screencap -p /sdcard/x.png && adb pull /sdcard/x.png 本地路径
```

截图存档在 `D:\miuix-trial\screens\`：
- `toolchain-upgraded.png` —— 工具链升级后（旧 UI）
- `ui-02b.png` —— Common.kt 首批迁移后（发现卡片贴边 + InfoTile 被裁）
- `ui-03.png` —— 修好内边距与 InfoTile 后
