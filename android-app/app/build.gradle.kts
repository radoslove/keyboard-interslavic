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
// Every debug build carries its own test number, in its version AND in both
// visible names. Two test builds that are both called "(test)" cannot be told
// apart on the phone - and an install that silently UPDATED the previous test
// build (no welcome screen) looked exactly like a fresh one. Test builds count
// towards the NEXT release: bump TEST_BUILD for every build handed to a phone,
// and TEST_BASE when a release is cut. Release builds do not use this, so
// F-Droid's reproducible build is unaffected.
val TEST_BASE = "3.5"
val TEST_BUILD = 2
val testVersion = "$TEST_BASE.$TEST_BUILD"

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
            // BOTH labels: app_name is what the installer shows, ime_name labels
            // the SERVICE and is what the system keyboard list shows.
            // Short on purpose: the keyboard list truncates, and the version is
            // the part that matters.
            val testLabel = "MS test $testVersion"
            resValue("string", "app_name", testLabel)
            resValue("string", "ime_name", testLabel)
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

// The version shown in Settings -> Apps: the test number, not the last release.
androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.outputs.forEach { it.versionName.set("$testVersion-test") }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}
