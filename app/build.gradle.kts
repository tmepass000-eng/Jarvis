import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.jarvis.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jarvis.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.3.0-phase3"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // Fixed dev key so every CI build has the same signature and installs as an update.
    signingConfigs {
        getByName("debug") {
            storeFile = file("jarvis-dev.p12")
            storePassword = "jarvis123"
            keyAlias = "jarvis"
            keyPassword = "jarvis123"
            storeType = "pkcs12"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Offline wake word
    implementation("com.alphacephei:vosk-android:0.3.47@aar")
    implementation("net.java.dev.jna:jna:5.13.0@aar")

    // Offline TTS (Piper voices). The AAR is fetched by the downloadSherpaAar task / CI, not committed.
    implementation(files("libs/sherpa-onnx-1.13.8.aar"))
    implementation("org.apache.commons:commons-compress:1.27.1")

    // Room: set up now, used for the wake event log; leads and pipeline come in later phases.
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}

// Downloads the Vosk small English model (~40 MB, Apache 2.0) into assets once, before the first build.
val downloadVoskModel by tasks.registering {
    val assetsDir = file("src/main/assets")
    val modelDir = File(assetsDir, "model-en-us")
    outputs.dir(modelDir)
    onlyIf { !File(modelDir, "uuid").exists() }
    doLast {
        val zip = File(layout.buildDirectory.get().asFile, "vosk-model.zip")
        zip.parentFile.mkdirs()
        URI("https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip").toURL()
            .openStream().use { input -> zip.outputStream().use { input.copyTo(it) } }
        copy {
            from(zipTree(zip))
            into(assetsDir)
        }
        File(assetsDir, "vosk-model-small-en-us-0.15").renameTo(modelDir)
        File(modelDir, "uuid").writeText("jarvis-model-en-us-0.15")
        zip.delete()
    }
}
tasks.named("preBuild") { dependsOn(downloadVoskModel) }

// sherpa-onnx Android library (Apache 2.0), downloaded once.
val downloadSherpaAar by tasks.registering {
    val aar = file("libs/sherpa-onnx-1.13.8.aar")
    outputs.file(aar)
    onlyIf { !aar.exists() }
    doLast {
        aar.parentFile.mkdirs()
        URI("https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar").toURL()
            .openStream().use { input -> aar.outputStream().use { input.copyTo(it) } }
    }
}
tasks.named("preBuild") { dependsOn(downloadSherpaAar) }
