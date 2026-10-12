// packer：InstallerKit 的**图形界面打包工具** —— 这才是「这个工具」本身。
//
// 和 :app 的区别：
//   :app     = 安装程序本体（装到用户机器上、双击起来是安装向导）
//   :packer  = 打包工具（你自己用，打开填表 → 点打包 → 出 Setup.exe）
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.compose")
}

dependencies {
    implementation(project(":kit-core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
}

base {
    archivesName.set("installerkit-packer")
}

compose.desktop {
    application {
        mainClass = "io.nekoshizuku.pack.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.AppImage)
            packageName = "InstallerKitPacker"
            packageVersion = "1.0.0"
            description = "InstallerKit packaging tool (GUI)"
            vendor = "Shizuku Studio"
            copyright = "(c) 2026 Shizuku Studio"

            windows {
                menuGroup = "InstallerKitPacker"
                upgradeUuid = "2D7E4B19-8A3C-4F52-9E6B-1C0A5F7D3E88"
                iconFile.set(project.file("icons/app.ico"))
            }
        }
    }
}

/** 无头自检：探测逻辑 + 配置往返 */
tasks.register<JavaExec>("verifyPacker") {
    group = "verification"
    description = "运行 packer 的无头自检"
    mainClass.set("io.nekoshizuku.pack.verify.VerifyPackerKt")
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}
