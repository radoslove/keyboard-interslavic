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

// Debug destinations are machine-specific and must never enter source control.
val diagnosticsFile = rootProject.file("diagnostics.local.properties")
val diagnosticsProps = Properties().apply {
    if (diagnosticsFile.exists()) diagnosticsFile.inputStream().use { load(it) }
}
fun javaString(value: String): String = "\"" + value
    .replace("\\", "\\\\").replace("\"", "\\\"")
    .replace("\r", "\\r").replace("\n", "\\n") + "\""

android {
    namespace = "com.radoslove.interslavic"
    compileSdk = 34

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.radoslove.interslavic"
        minSdk = 24
        targetSdk = 34
        versionCode = 34
        versionName = "3.4"
        buildConfigField("String", "CRASH_REPORT_URL", "\"\"")
        buildConfigField("String", "GESTURE_REPORT_URLS", "\"\"")
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
            buildConfigField("String", "CRASH_REPORT_URL",
                javaString(diagnosticsProps.getProperty("crashReportUrl", "")))
            buildConfigField("String", "GESTURE_REPORT_URLS",
                javaString(diagnosticsProps.getProperty("gestureReportUrls", "")))
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
