plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.codedorian"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.codedorian"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        delete("debug")

        delete("release")

        create("ttest") {
            storeFile = file("keystores/test.keystore")
            storePassword = "testtest"
            keyAlias = "test"
            keyPassword = "testest"
        }
        create("localhost") {
            storeFile = file("keystores/localhost.keystore")
            storePassword = "localhostlocalhost"
            keyAlias = "localhost"
            keyPassword = "localhoslocalhost"
        }
        create("development") {
            storeFile = file("keystores/development.keystore")
            storePassword = "developmentdevelopment"
            keyAlias = "development"
            keyPassword = "loGcalhosdevelopment"
        }
        create("staging") {
            storeFile = file("keystores/staging.keystore")
            storePassword = "stagingstaging"
            keyAlias = "staging"
            keyPassword = "localhosstaging"
        }
        create("production") {
            storeFile = file("keystores/production.keystore")
            storePassword = "productionproduction"
            keyAlias = "production"
            keyPassword = "localhosproduction"
        }
    }

    buildTypes {
        delete("debug")

        delete("release")

        create("ttest") {
            applicationIdSuffix = ".test"
            versionNameSuffix = "-test"
            buildConfigField("String", "CODE_ENV", "\"test\"")
            signingConfig = signingConfigs.getByName("ttest")
        }
        create("localhost") {
            applicationIdSuffix = ".localhost"
            versionNameSuffix = "-localhost"
            buildConfigField("String", "CODE_ENV", "\"localhost\"")
            signingConfig = signingConfigs.getByName("debug")
        }
        create("development") {
            applicationIdSuffix = ".development"
            versionNameSuffix = "-development"
            buildConfigField("String", "CODE_ENV", "\"development\"")
        }
        create("staging") {
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            buildConfigField("String", "CODE_ENV", "\"staging\"")
        }
        create("production") {
            applicationIdSuffix = ""
            versionNameSuffix = ""
            buildConfigField("String", "CODE_ENV", "\"production\"")
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
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
