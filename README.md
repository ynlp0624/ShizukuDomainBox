<div align="center">

# 雫的域名盒 · ShizukuDomainBox

**DNSHE 免费域名的多账号管理工具**

安卓 + Windows 双端 · 界面基于 Miuix（HyperOS 风格）

[官网](https://dnshe.shizuku.plus) · [下载](#下载) · [功能](#功能)

</div>

---

## 这是什么

[DNSHE](https://my.dnshe.com/) 免费域名的第三方管理工具。把多个账号收进一个盒子，
不用再反复登录网页后台改解析。

- **安卓版** —— Kotlin + Jetpack Compose 原生，界面用 [Miuix](https://github.com/compose-miuix-ui/miuix) 做 HyperOS 观感
- **Windows 版** —— Kotlin + Compose Multiplatform，自带 Java 运行时与自制安装程序
- 两端**数据格式完全互通**，备份 JSON 可以互相导入

> 本项目是第三方工具，与 DNSHE 官方没有隶属关系。

## 下载

到 [Releases](../../releases) 页面下载：

| 平台 | 文件 | 大小 | 系统要求 |
|---|---|---|---|
| Android | `ShizukuDomainBox-Android-v2.0.0.apk` | 约 11 MB | Android 8.0+ |
| Windows | `ShizukuDomainBox-Setup-v2.0.0.exe` | 约 96 MB | Windows 10/11 x64 |

也可以直接从官网下载：<https://dnshe.shizuku.plus>

## 功能

| 功能 | 说明 |
|---|---|
| **多账号管理** | 任意数量的 DNSHE 账号，一键切换，各自独立保存密钥与默认根域名 |
| **子域名注册** | 在自己的根域名下注册免费子域名；官方 / 外部根域名分开标注，外部域名有风险提示 |
| **DNS 记录管理** | A / AAAA / CNAME / MX / TXT / NS / SRV / CAA 全类型增删改查 |
| **配额与密钥** | 实时查看域名配额、API 密钥列表与调用次数 |
| **WHOIS 查询** | 查任意域名的注册状态 |
| **永久升级中心** | 查看进度、提交申请、用邀请码助力 |
| **JSON 备份** | 明文 / 口令加密（PBKDF2-HMAC-SHA256 + AES-256-GCM），两端互通 |
| **中英双语** | 界面语言随时切换 |

## 安全性

- 账号密钥**只存在本机**：安卓用 Keystore、Windows 用 DPAPI，按当前用户加密
- **没有任何中间服务器**，唯一的网络请求就是直连 DNSHE 官方 API
- **不上报任何遥测数据**

## 项目结构

```
android/        安卓版（Kotlin + Jetpack Compose + Miuix）
  app/src/main/java/io/nekoshizuku/dnshe/
    data/       数据层：API 客户端、模型、加密存储、根域名表
    i18n/       中英文字符串表
    ui/         界面层（Miuix 组件）

pc/             Windows 版（Kotlin + Compose Multiplatform）
  core/         与安卓共享的业务逻辑：API、模型、DPAPI 加密、Windows 集成
  desktop/      桌面界面层（Miuix 组件）+ 自研安装程序
  tools/        打包脚本

site/           官网（纯静态 HTML/CSS/JS，零依赖）

installer-kit/  通用的 Windows 安装包制作工具（从本项目抽出来的）
```

## 从源码构建

### 环境

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | **21** | Miuix 的 class 文件是 Java 21，**必须 21+** |
| Gradle | 9.8.1 | |
| Android SDK | Platform 37 / Build-Tools 37 | 安卓版需要；`compileSdk` 必须 ≥ 37（Miuix 的 AAR metadata 强制检查） |

### 安卓版

```bash
export JAVA_HOME=/path/to/jdk-21
./gradlew -p android assembleRelease
# 产物：android/app/build/outputs/apk/release/app-release.apk
```

需要自备签名密钥：在 `android/` 下建 `keystore.properties`：

```properties
storeFile=../keystore/your.jks
storePassword=***
keyAlias=***
keyPassword=***
```

（仓库里**不含**任何签名密钥）

### Windows 版

```bash
export JAVA_HOME=/path/to/jdk-21
./gradlew -p pc :desktop:run                 # 直接运行
./gradlew -p pc :desktop:createDistributable # 生成 app-image
```

自带一个无头自检任务，用来验证 API / DPAPI / 备份互通：

```bash
./gradlew -p pc :desktop:verifyCore --args="check"
```

## 技术要点

几个踩过的坑，对做同类工具的人可能有帮助：

1. **jpackage 的 `--icon` 只管 exe 文件图标**，窗口标题栏和任务栏图标必须程序运行时
   自己设（`Window(icon = ...)` + `window.iconImages` 多尺寸），否则显示 Java 默认的咖啡杯。
2. **AGP 9 起 Kotlin 支持已内置**，不能再单独应用 `org.jetbrains.kotlin.android` 插件；
   `android.kotlinOptions{}` 要迁移到顶层 `kotlin.compilerOptions{}`。
3. **Miuix 的两个坑**：`Card` 的 `InsideMargin` 默认是 `PaddingValues(0.dp)`（必须自己给内边距）；
   `Card` 内部带 `propagateMinConstraints`，和外层 `Row.weight()` 组合会算小高度。
4. **jpackage 的参数文件按系统编码读**，`packageName` / `description` 里写中文会直接报
   `Input length = 1` 构建失败，所以 jpackage 层全用 ASCII。
5. **运行中的 JVM 会锁住自己的 jar**，卸载时删不掉 `app/*.jar`，得靠延迟批处理循环重试。

更多细节见 `android/MIGRATION-ANDROID.md`、`pc/MIGRATION-PC.md`、`site/DEPLOY.md`。

## 许可

代码以 MIT 许可发布，详见 [LICENSE](LICENSE)。
「雫的域名盒」为个人项目，与 DNSHE 官方无隶属关系。
