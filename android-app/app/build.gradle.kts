import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing is configured only if keystore.properties exists (it is
// git-ignored and holds the secrets). Debug builds and clones without the
// keystore still build — they just fall back to the debug signature.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.radoslove.interslavic"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.radoslove.interslavic"
        minSdk = 24
        targetSdk = 34
        versionCode = 31
        versionName = "3.1"
    }

    // F-Droid reproducible builds reject the AGP "Dependency metadata"
    // signing block. Strip it so the published APK matches F-Droid's build.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            // Android refuses to replace an installed app when the signature
            // differs, so a debug build could not land on a phone that already
            // had the released (release-key) version - it just says
            // "App not installed" with no reason given. A separate application
            // id lets both live side by side: the published keyboard keeps
            // working while a test build is being tried next to it.
            applicationIdSuffix = ".debug"
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
