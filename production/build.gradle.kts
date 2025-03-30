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
            signingConfig = signingConfigs.getByName("localhost")
        }
        create("development") {
            applicationIdSuffix = ".development"
            versionNameSuffix = "-development"
            buildConfigField("String", "CODE_ENV", "\"development\"")
            signingConfig = signingConfigs.getByName("staging")
        }
        create("staging") {
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            buildConfigField("String", "CODE_ENV", "\"staging\"")
            signingConfig = signingConfigs.getByName("staging")
        }
        create("production") {
            applicationIdSuffix = ""
            versionNameSuffix = ""
            buildConfigField("String", "CODE_ENV", "\"production\"")
            signingConfig = signingConfigs.getByName("production")
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