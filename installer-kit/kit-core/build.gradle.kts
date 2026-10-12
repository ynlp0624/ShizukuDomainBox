// kit-core：纯 Kotlin/JVM，不含任何 UI 依赖。
// 别的项目可以直接依赖它来做命令行打包工具。
plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}

/**
 * 无头自检：验证 cfg 改写 / 模式判定 / 命令行解析 / 配置读写 / 路径推导。
 * 刻意不用 JUnit —— 这台机器要尽量离线跑，少一个依赖少一个坑。
 */
tasks.register<JavaExec>("verifyCore") {
    group = "verification"
    description = "运行 kit-core 的无头自检"
    mainClass.set("io.nekoshizuku.kit.verify.VerifyCoreKt")
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}
