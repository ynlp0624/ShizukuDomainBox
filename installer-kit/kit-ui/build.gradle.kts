// kit-ui：Compose Desktop 向导（可复用组件 + 可直接用的向导）。
// 只依赖 kit-core，不碰任何产品代码。
plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

dependencies {
    // api：app 壳只用得到 kit-ui 的向导函数，但顺手把 kit-core 的类型也带出来
    api(project(":kit-core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
}

/**
 * 无头自检：中英文案 key 对齐、每条 getter 都有文案、品牌色解析、素材路径判断。
 * 不建窗口、不加载 Skiko，纯函数级别。
 */
tasks.register<JavaExec>("verifyUi") {
    group = "verification"
    description = "运行 kit-ui 的无头自检"
    mainClass.set("io.nekoshizuku.kit.ui.verify.VerifyUiKt")
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}
