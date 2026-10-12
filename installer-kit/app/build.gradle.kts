// app：把 kit-core（以后还有 kit-ui）组装成「安装程序本体」的壳工程。
//
// ⚠️ jpackage 的参数文件按系统编码读，packageName / description / menuGroup
// 写中文会直接报 "Input length = 1"，所以这一层全部用 ASCII。
// 中文的产品名由配置（installer.config.json）在运行期写进快捷方式和注册表。
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

dependencies {
    // 界面全在 kit-ui，逻辑全在 kit-core；这个壳只负责「分发 + 开窗」
    implementation(project(":kit-ui"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
}

// 这个 jar 会被注入到产品的 app-image 里，名字要一眼认得出来
base {
    archivesName.set("kit-installer")
}

compose.desktop {
    application {
        mainClass = "io.nekoshizuku.kit.app.MainKt"
        nativeDistributions {
            // 只出 app-image：真正的安装包由 tools\build-installer.ps1 组装
            targetFormats(TargetFormat.AppImage)
            packageName = "InstallerKit"
            packageVersion = "1.0.0"
            description = "InstallerKit setup wizard shell"
            vendor = "Nekoshizuku"
            copyright = "(c) 2026 Nekoshizuku"

            windows {
                menuGroup = "InstallerKit"
                upgradeUuid = "C4F1B2A7-6E3D-4B90-8F17-5D2E9A0C7B34"
                iconFile.set(project.file("icons/app.ico"))
            }
        }
    }
}
