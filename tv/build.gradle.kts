import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.FilterConfiguration
import java.io.FileInputStream
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
        versionCode = "${System.getenv("VERSION_CODE")}".toInt()
        versionName = "1.1.1.${System.getenv("VERSION_CODE")}"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "SENTRY_DSN", "\"${getProperty("sentry.dsn") ?: System.getenv("SENTRY_DSN")}\"")
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
            signingConfig = signingConfigs.getByName("release")
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
            val ver = "1.1.1.${System.getenv("VERSION_CODE")}"
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

    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:util"))
    implementation(project(":ijkplayer-java"))
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
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
