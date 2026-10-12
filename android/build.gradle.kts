// 迁移到 Miuix 后的工具链版本
//   Gradle 9.8.1 / JDK 21 / AGP 9.4.1（内置 Kotlin）/ Compose 1.12
//   注意：AGP 9 起 Kotlin 支持已内置，不能再单独应用 org.jetbrains.kotlin.android
plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.20" apply false
}
