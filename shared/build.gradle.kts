plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("app.cash.sqldelight")
}

kotlin {
    android {
        namespace = "br.app.vendas.shared"
        compileSdk {
            version = release(37) { minorApiLevel = 1 }
        }
        minSdk = 26
    }

    // O app é só Android. Este alvo JVM existe apenas para rodar os testes no computador
    // (banco SQLite em memória), sem precisar de celular ou emulador.
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api("app.cash.sqldelight:runtime:2.4.1")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
        }
        androidMain.dependencies {
            // Para tratar o botão/gesto "voltar" do Android.
            implementation("androidx.activity:activity-compose:1.13.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        // Os testes do repositório rodam na JVM com um banco SQLite em memória.
        val desktopTest by getting {
            dependencies {
                implementation("app.cash.sqldelight:sqlite-driver:2.4.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            }
        }
    }
}

sqldelight {
    databases {
        create("BancoDeVendas") {
            packageName.set("br.app.vendas.db")
        }
    }
}
