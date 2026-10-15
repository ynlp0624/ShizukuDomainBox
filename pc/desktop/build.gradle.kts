import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    // Miuix 0.9.x 编译目标是 Java 21
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    // 调 Windows 原生文件夹选择器（SHBrowseForFolder），绕开 jpackage 运行时里
    // JFileChooser 弹不出来的问题；jna 本来就随 compose.desktop 传递进来，显式声明只是让它进编译期
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")

    // ---- Miuix（HyperOS 风格组件库）----
    implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-icons:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-preference:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-squircle:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-blur:0.9.4")
}

compose.desktop {
    application {
        mainClass = "io.nekoshizuku.dnshe.desktop.MainKt"
        nativeDistributions {
            // 只出 app-image，安装包由我们自研的安装程序负责
            targetFormats(TargetFormat.AppImage)
            packageName = "ShizukuDomainBox"
            packageVersion = "2.0.0"
            // 注意：jpackage 的参数文件按系统编码读，写中文会报 "Input length = 1"，
            // 所以这里全部用 ASCII；显示用的中文名由我们自己的安装程序写进快捷方式和注册表。
            description = "DNSHE free domain multi-account manager"
            vendor = "Nekoshizuku"
            copyright = "(c) 2026 Nekoshizuku"

            windows {
                menuGroup = "ShizukuDomainBox"
                upgradeUuid = "7B1A6B0E-5D2C-4E77-9E4B-2A6D9F3C8A11"
                dirChooser = true
                perUserInstall = true
                shortcut = true
                iconFile.set(project.file("icons/app.ico"))
            }

            modules("java.net.http", "jdk.crypto.ec", "java.naming", "jdk.unsupported")
        }
    }
}

/** 无头自检任务：验证 API / DPAPI / JSON 备份 */
tasks.register<JavaExec>("verifyCore") {
    group = "verification"
    description = "运行 core 层的无头自检"
    mainClass.set("io.nekoshizuku.dnshe.desktop.verify.VerifyCoreKt")
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}
