pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AppDeVendas"

include(":shared", ":androidApp")

// Pasta já existente no projeto: Mobile (Android).
project(":androidApp").projectDir = file("Mobile")
