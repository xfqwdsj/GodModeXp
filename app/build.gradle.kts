plugins {
    kotlin("android")
    id("com.android.application")
    id("androidx.navigation.safeargs")
}

android {
    namespace = "xyz.xfqlittlefan.godmode"
    compileSdk = 33
    buildToolsVersion = "33.0.1"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    defaultConfig {
        applicationId = "xyz.xfqlittlefan.godmode"
        minSdk = 21
        targetSdk = 33
        versionCode = 22
        versionName = "3.0.0-beta1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildFeatures {
        aidl = true
        buildConfig = true
    }

    androidResources {
        additionalParameters += listOf("--allow-reserved-package-id", "--package-id", "0x95")
    }
}

repositories {
    maven("https://raw.github.com/embarkmobile/zxing-android-minimal/mvn-repo/maven-repository/")
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(project(":libxservicemanager"))
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.navigation:navigation-fragment:2.5.3")
    implementation("androidx.navigation:navigation-ui:2.5.3")
    implementation("androidx.appcompat:appcompat:1.6.0")
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    implementation("androidx.fragment:fragment:1.5.5")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.legacy:legacy-support-v13:1.0.0")
    implementation("androidx.legacy:legacy-preference-v14:1.0.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.google.android.material:material:1.9.0-alpha01")
    implementation("com.github.bumptech.glide:glide:4.14.2")
    implementation("dev.rikka.rikkax.insets:insets:1.3.0")
    implementation("androidx.core:core-ktx:1.9.0")
    compileOnly("de.robv.android.xposed:api:82")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1") {
        exclude("com.android.support", "support-annotations")
    }
    annotationProcessor("com.github.bumptech.glide:compiler:4.14.2")
}
