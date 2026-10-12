// samples/demo-app：一个最小的 Compose Desktop 主程序。
//
// 它的存在意义是**回归测试**：改坏了 kit 之后，用它走一遍
// 「构建 app-image → 组装 Setup.exe → 安装 → 卸载」，就知道有没有踩坏东西。
// HANDOFF 第 4 节特意标注：一定要先做 demo-app，不然改坏了没有回归手段。
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
}

compose.desktop {
    application {
        mainClass = "io.nekoshizuku.demo.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.AppImage)
            // ASCII！中文会炸（见 HANDOFF 第 3 节第 1 条）
            packageName = "KitDemoBox"
            packageVersion = "1.0.0"
            description = "InstallerKit regression demo app"
            vendor = "Nekoshizuku"
            copyright = "(c) 2026 Nekoshizuku"

            windows {
                menuGroup = "KitDemoBox"
                upgradeUuid = "9E2A7C41-3B85-4F60-A1D9-7C4B8E0F5263"
                iconFile.set(project.file("icons/app.ico"))
            }
        }
    }
}
