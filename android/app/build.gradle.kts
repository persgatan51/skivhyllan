plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "se.persgatan.skivhyllan"
    compileSdk = 35

    defaultConfig {
        applicationId = "se.persgatan.skivhyllan"
        minSdk = 26
        targetSdk = 35
        versionCode = runNumber
        versionName = "1.0.$runNumber"
    }

    // Fast signeringsnyckel i repot, så att nya versioner kan installeras ovanpå den gamla.
    signingConfigs {
        getByName("debug") {
            storeFile = file("../debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
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
    // FileProvider, så att kameraappen kan spara fotot åt oss i full upplösning.
    implementation("androidx.core:core:1.13.1")
    // Googles ML Kit: läser streckkod och text i telefonen, gratis och utan nätverk (modellerna följer med appen).
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
