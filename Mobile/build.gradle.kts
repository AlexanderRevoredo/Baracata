import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

// Chave de assinatura do APK (pasta "assinatura", fora do código). Sem ela, só o APK de teste é gerado.
val dadosDaChave = Properties().apply {
    val arquivo = rootProject.file("assinatura/keystore.properties")
    if (arquivo.exists()) arquivo.inputStream().use { load(it) }
}

android {
    namespace = "br.app.vendas"
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }

    defaultConfig {
        applicationId = "br.app.vendas"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.0"
    }

    signingConfigs {
        if (dadosDaChave.isNotEmpty()) {
            create("baracata") {
                storeFile = rootProject.file("assinatura/baracata.jks")
                storePassword = dadosDaChave.getProperty("senha")
                keyAlias = "baracata"
                keyPassword = dadosDaChave.getProperty("senha")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfigs.findByName("baracata")?.let { signingConfig = it }
        }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("app.cash.sqldelight:android-driver:2.4.1")
    // Leitor de código de barras (ZXing) que roda dentro do próprio app, sem depender do Google Play Services.
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}
