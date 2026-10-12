# 没有开启 R8 压缩，这里主要留给以后要用的时候。
# kotlinx.serialization 自带 consumer rules，OkHttp 也一样。
-dontwarn okhttp3.**
-dontwarn okio.**
-keepclassmembers class io.nekoshizuku.dnshe.data.** {
    *** Companion;
}
