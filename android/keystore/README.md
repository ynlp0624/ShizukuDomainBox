# 签名密钥说明

> ⚠️ **这个目录里的 `.jks` 与工程根目录的 `keystore.properties` 都是敏感文件，
> 绝对不要提交到公开仓库。** 仓库的 `.gitignore` 已经把它们排除了。

## 为什么要自己保管好

Android 用签名来认定「是不是同一个 App」。以后要出新版本（覆盖升级），
必须用**同一把密钥**签名，否则系统会拒绝安装，用户只能先卸载旧版（数据会丢）。

Windows 端的安装程序也用同一套命名习惯（`upgradeUuid`），
换 UUID 会被系统当成另一个程序。

## 怎么自建一套

```bash
keytool -genkeypair -v \
  -keystore keystore/your-release.jks \
  -alias youralias \
  -keyalg RSA -keysize 4096 -sigalg SHA384withRSA \
  -validity 10950 \
  -dname "CN=Your Name, OU=Personal, O=YourOrg, C=CN"
```

然后在工程根目录建 `keystore.properties`（**不要提交**）：

```properties
storeFile=keystore/your-release.jks
storePassword=<你的口令>
keyAlias=<你的别名>
keyPassword=<你的口令>
```

Gradle 会自动读取这个文件；文件不存在时构建出的 APK 是**未签名**的。

## 查看已有密钥

```bash
keytool -list -v -keystore keystore/your-release.jks
```

## 构建

```bash
# 注意：Miuix 要求 JDK 21+
export JAVA_HOME=/path/to/jdk-21
./gradlew assembleRelease
```
