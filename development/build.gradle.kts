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
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        delete("debug")

        delete("release")

        create("development") {
            storeFile = file("keystores/development.keystore")
            storePassword = "developmentdevelopment"
            keyAlias = "development"
            keyPassword = "developmentdevelopment"
        }
    }

    buildTypes {
        delete("debug")

        delete("release")

        create("development") {
            applicationIdSuffix = ".development"
            versionNameSuffix = "-development"
            signingConfig = signingConfigs.getByName("development")
            buildConfigField("String", "CODE_ENV", "\"development\"")
            resValue("string", "app_name", "codedorian - dev")
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
