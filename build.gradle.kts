allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://api.xposed.info/")
    }
}

plugins {
    kotlin("android") version "1.8.0" apply false
    id("com.android.application") version "8.1.0-alpha02" apply false
    id("androidx.navigation.safeargs") version "2.5.3" apply false
}
