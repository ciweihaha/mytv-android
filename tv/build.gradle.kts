import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.FilterConfiguration
import java.io.File
import java.io.FileInputStream
import java.net.URI
import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sentry.android.gradle)
}

// 签名配置原来在根脚本的 appConfig 里（allprojects + extra 桥接），
// AGP 9 新 DSL 下那套桥接取不到，挪到 tv 模块里直接写。
val keystorePropertiesFile = rootProject.file("signing.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "top.yogiczy.mytv.tv"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.github.mytv.android"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // VERSION_CODE 未设置时默认 1：原先 "${System.getenv("VERSION_CODE")}".toInt()
        // 在未设环境变量时是 "null".toInt()，配置阶段直接抛 NumberFormatException，
        // 本地克隆后 ./gradlew assembleDebug 根本跑不起来（CI 设了该变量所以没暴露）
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = "1.1.1.${System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1}"//.${System.getenv("COMMIT_HASH")}"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "SENTRY_DSN", "\"${getProperty("sentry.dsn") ?: System.getenv("SENTRY_DSN")}\"")
        // 发版 tag（如 V3.0.0.242）：Release 工作流在打 tag 构建时把 tag 名写进来，
        // 更新检查拿它和线上最新版比——内部版本号 1.1.1.N 和 tag 的 3.0.0.N 是
        // 两套编号，直接比内部版永远报“有新版”。未设置时是空串（不是 "null"，
        // 别重蹈 SENTRY_DSN 字面量 null 的覆辙），更新检查退回比内部版。
        buildConfigField("String", "RELEASE_TAG", "\"${System.getenv("RELEASE_TAG") ?: ""}\"")
    }

    signingConfigs {
        val localKeystore = rootProject.file("keystore.jks")
        val userKeystore = file(
            System.getenv("KEYSTORE") ?: keystoreProperties.getProperty("storeFile")
            ?: "keystore.jks"
        )

        create("release") {
            storeFile = if (userKeystore.exists()) userKeystore else localKeystore
            storePassword = System.getenv("KEYSTORE_PASSWORD")
                ?: keystoreProperties.getProperty("storePassword")
            keyAlias = System.getenv("KEY_ALIAS") ?: keystoreProperties.getProperty("keyAlias")
            keyPassword =
                System.getenv("KEY_PASSWORD") ?: keystoreProperties.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug{
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 本地没有 keystore 时用默认 debug 签名：debug 原先固定走 release
            // signingConfig，无 keystore 的环境下 assembleDebug 签名阶段直接失败；
            // 有 keystore（CI 会生成一次性 keystore）时行为不变
            signingConfig = signingConfigs.findByName("release")
                ?.takeIf { it.storeFile?.exists() == true }
                ?: signingConfigs.getByName("debug")
        }
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        // 单元测试里 android.util.Log 等系统调用返回默认值而不是抛 Stub!，
        // 否则用到 Logger 的 ViewModel 无法在 JVM 单测里跑（core/data 同款）
        unitTests.isReturnDefaultValues = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    flavorDimensions += listOf("version")
    productFlavors {
        create("original") {
            dimension = "version"
        }

        create("disguised") {
            dimension = "version"
            applicationId = "com.chinablue.tv"
        }
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

// 旧版 applicationVariants 在 AGP 9 新 DSL 里已删，改名走新版 Variant API。
// 文件名格式与原来一致：mytv-android-tv-版本-架构-sdk最低版本-风味.apk
androidComponents {
    onVariants { variant ->
        val appVariant = variant as? ApplicationVariant ?: return@onVariants
        appVariant.outputs.forEach { output ->
            // 新版 Variant API 里拿不到 versionName/minSdk，直接用与 defaultConfig 相同的取值。
            val ver = "1.1.1.${System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1}"
            val minSdk = libs.versions.minSdk.get().toInt()
            val abi = output.filters.find { it.filterType == FilterConfiguration.FilterType.ABI }?.identifier ?: "all"
            val flavor = appVariant.flavorName

            output.outputFileName.set(
                "mytv-android-${project.name}-$ver-${abi}-sdk$minSdk-${flavor}.apk"
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation.base)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.androidx.material.icons.extended)

    // 播放器
    val mediaSettingsFile = file("../../media/core_settings.gradle")
    if (mediaSettingsFile.exists()) {
        implementation(project(":media3:lib-exoplayer"))
        implementation(project(":media3:lib-exoplayer-hls"))
        implementation(project(":media3:lib-exoplayer-rtsp"))
        implementation(project(":media3:lib-exoplayer-dash"))
        implementation(project(":media3:lib-ui"))
    } else {
        implementation(libs.androidx.media3.exoplayer)
        implementation(libs.androidx.media3.exoplayer.hls)
        implementation(libs.androidx.media3.exoplayer.rtsp)
        implementation(libs.androidx.media3.exoplayer.dash)
        implementation(libs.androidx.media3.ui)
    }
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.datasource.rtmp)
    implementation(libs.androidx.media3.exoplayer.smoothstreaming)

    // 二维码
    implementation(libs.qrose)

    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.coil.network.okhttp)

    implementation(libs.okhttp)
    implementation(libs.androidasync)

    implementation(libs.tinypinyin)

    // AI 实时字幕：推理用 sherpa-onnx，AAR 放在 tv/libs/ 下（构建时
    // 由下面的 fileTree("libs", "*.aar") 引用）。sherpa-onnx 不在 Maven
    // Central 上，JitPack 坐标也解析不到（pom 404），所以不走依赖
    // 坐标、直接用本地 AAR。
    // AAR 不进 git（48MB，GitHub API 推不上去、git 直推也被出口代理
    // 拦）：构建时如果 tv/libs/ 下没有它，下面的 downloadSherpaOnnxAar
    // 任务会从官方 release 现下（CI 能直连 GitHub），并按 SHA-256 验
    // 证；本地已有就跳过。换版本时改下面的版本号 + SHA-256 即可。
    // 重要：千万别再单独引 onnxruntime-android——AAR 里自带
    // libonnxruntime.so（1.27.0），libsherpa-onnx-jni.so 要的正是
    // 它提供的 OrtGetApiBase@VERS_1.27.0（已用 readelf 核对过）；
    // 另引一个 onnxruntime-android（比如 1.22.0）会打包两份
    // libonnxruntime.so、符号版本对不上，运行时链接失败。
    // 换 sherpa-onnx 版本时，换 tv/libs/ 下的 AAR 文件即可（注意
    // 新版 AAR 自带的 onnxruntime 版本可能变，以它为准）。
    implementation(libs.commons.compress)

    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:util"))
    implementation(project(":ijkplayer-java"))
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.junit)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

sentry {
    org.set("mytv-android")
    projectName.set("mytv")
    authToken.set(getProperty("sentry.auth_token") ?: System.getenv("SENTRY_AUTH_TOKEN"))
    autoUploadProguardMapping = false
}

fun getProperty(key: String): String? {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.exists()) {
        val properties = Properties()
        properties.load(FileInputStream(propertiesFile))

        return properties.getProperty(key)
    }

    return null
}

// AI 实时字幕：sherpa-onnx AAR 构建时下载（不进 git，48MB 推不上去）。
// 官方 release：https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.4
// tv/libs/ 下已有同名文件且 SHA-256 对得上就跳过；否则现下并校验。
// 失败时构建直接报错，别静默编出一个缺原生库的包。
val sherpaOnnxAarFile = file("libs/sherpa-onnx-1.13.4.aar")
val sherpaOnnxAarUrls = listOf(
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.4/sherpa-onnx-1.13.4.aar",
    "https://gh-proxy.com/https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.4/sherpa-onnx-1.13.4.aar",
)
val sherpaOnnxAarSha256 = "03f9c4df965f21c71269365a7951a7f23b5696fddd093fa318c80d65550ab780"

tasks.register("downloadSherpaOnnxAar") {
    description = "下载 sherpa-onnx AAR 到 tv/libs（已有且 SHA-256 正确时跳过）"
    group = "build setup"
    outputs.file(sherpaOnnxAarFile)
    doLast {
        val digest = MessageDigest.getInstance("SHA-256")
        fun sha256Of(f: File): String =
            digest.digest(f.readBytes()).joinToString("") { "%02x".format(it) }
        if (sherpaOnnxAarFile.exists() && sha256Of(sherpaOnnxAarFile) == sherpaOnnxAarSha256) {
            logger.lifecycle("sherpa-onnx AAR 已存在且校验通过，跳过下载")
            return@doLast
        }
        sherpaOnnxAarFile.parentFile.mkdirs()
        val tmp = File(sherpaOnnxAarFile.parentFile, "sherpa-onnx-1.13.4.aar.tmp")
        // 按顺序试地址（官方直链 → 国内镜像），每个地址最多重试 2 次；
        // 下完验 SHA-256，不对就删了换下一个，全失败才报错。
        var lastError: Exception? = null
        var downloaded = false
        for (url in sherpaOnnxAarUrls) {
            repeat(2) { attempt ->
                if (downloaded) return@repeat
                try {
                    logger.lifecycle("正在下载 sherpa-onnx AAR（第 ${attempt + 1} 次）：$url")
                    tmp.delete()
                    URI(url).toURL().openStream().use { input ->
                        tmp.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (sha256Of(tmp) != sherpaOnnxAarSha256) {
                        throw GradleException("SHA-256 不符")
                    }
                    downloaded = true
                } catch (ex: Exception) {
                    lastError = ex
                    logger.warn("下载失败（$url，第 ${attempt + 1} 次）：${ex.message}")
                    tmp.delete()
                }
            }
            if (downloaded) break
        }
        if (!downloaded) {
            tmp.delete()
            throw GradleException("下载 sherpa-onnx AAR 失败（已试 ${sherpaOnnxAarUrls.size} 个地址）：${lastError?.message}，请检查网络后重试", lastError)
        }
        if (!tmp.renameTo(sherpaOnnxAarFile)) {
            tmp.copyTo(sherpaOnnxAarFile, overwrite = true)
            tmp.delete()
        }
        logger.lifecycle("sherpa-onnx AAR 下载完成并校验通过")
    }
}

// 所有需要 AAR 的构建先保证它在位（本地已有时任务直接跳过，不联网）。
tasks.configureEach {
    if (name.startsWith("pre") && (name.endsWith("Build") || name == "preBuild")) {
        dependsOn(tasks.named("downloadSherpaOnnxAar"))
    }
}
