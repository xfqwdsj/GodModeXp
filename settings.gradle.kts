pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }

    plugins {
        kotlin("android") version "1.8.0"
        id("com.android.application") version "8.1.0-alpha01"
        id("androidx.navigation.safeargs") version "2.5.3"
    }
}

include(":app", ":libxservicemanager")
