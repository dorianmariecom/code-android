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
        versionCode = 306
        versionName = "3.6"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    flavorDimensions += "environment"

    signingConfigs {
        create("ttest") {
            storeFile = file("keystores/test.keystore")
            storePassword = "testtest"
            keyAlias = "test"
            keyPassword = "testtest"
        }

        create("localhost") {
            storeFile = file("keystores/localhost.keystore")
            storePassword = "localhostlocalhost"
            keyAlias = "localhost"
            keyPassword = "localhostlocalhost"
        }

        create("development") {
            storeFile = file("keystores/development.keystore")
            storePassword = "developmentdevelopment"
            keyAlias = "development"
            keyPassword = "developmentdevelopment"
        }

        create("staging") {
            storeFile = file("keystores/staging.keystore")
            storePassword = "stagingstaging"
            keyAlias = "staging"
            keyPassword = "stagingstaging"
        }

        create("production") {
            storeFile = file("keystores/production.keystore")
            storePassword = "productionproduction"
            keyAlias = "production"
            keyPassword = "productionproduction"
        }
    }

    productFlavors {
        create("ttest") {
            dimension = "environment"
            applicationIdSuffix = ".test"
            versionNameSuffix = "-test"
            buildConfigField("String", "CODE_ENV", "\"test\"")
            resValue("string", "app_name", "test")
            signingConfig = signingConfigs.getByName("ttest")
        }

        create("localhost") {
            dimension = "environment"
            applicationIdSuffix = ".localhost"
            versionNameSuffix = "-localhost"
            buildConfigField("String", "CODE_ENV", "\"localhost\"")
            resValue("string", "app_name", "localhost")
            signingConfig = signingConfigs.getByName("localhost")
        }

        create("development") {
            dimension = "environment"
            applicationIdSuffix = ".development"
            versionNameSuffix = "-development"
            buildConfigField("String", "CODE_ENV", "\"development\"")
            resValue("string", "app_name", "development")
            signingConfig = signingConfigs.getByName("development")
        }

        create("staging") {
            dimension = "environment"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            buildConfigField("String", "CODE_ENV", "\"staging\"")
            resValue("string", "app_name", "staging")
            signingConfig = signingConfigs.getByName("staging")
        }

        create("production") {
            dimension = "environment"
            buildConfigField("String", "CODE_ENV", "\"production\"")
            resValue("string", "app_name", "codedorian")
            signingConfig = signingConfigs.getByName("production")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }

        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
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
