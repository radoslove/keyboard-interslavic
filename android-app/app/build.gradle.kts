plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.radoslove.interslavic"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.radoslove.interslavic"
        minSdk = 24
        targetSdk = 34
        versionCode = 4
        versionName = "0.4"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    implementation("androidx.core:core-ktx:1.13.1")
}
