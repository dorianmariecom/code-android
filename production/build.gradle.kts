plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.codedorian"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.codedorian"
        minSdk = 28
        targetSdk = 35
        versionCode = 7
        versionName = "1.7"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    signingConfigs {
        delete("debug")

        delete("release")

        create("production") {
            storeFile = file("keystores/production.keystore")
            storePassword = "productionproduction"
            keyAlias = "production"
            keyPassword = "productionproduction"
        }
    }

    buildTypes {
        delete("debug")

        delete("release")

        create("production") {
            applicationIdSuffix = ""
            versionNameSuffix = ""
            buildConfigField("String", "CODE_ENV", "\"production\"")
            resValue("string", "app_name", "codedorian")
            signingConfig = signingConfigs.getByName("production")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.hotwire.core)
    implementation(libs.hotwire.navigation.fragments)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.messaging.ktx)
    implementation(libs.keyboard.visibility.event)
}