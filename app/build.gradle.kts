plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.callrec"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.callrec"
        minSdk = 26
        targetSdk = 34
        versionCode = 11
        versionName = "0.11"
    }

    // A fixed key is used for every build so a new APK installs over the old one and keeps the data.
    // This is a throw-away test key. Do not reuse it for the final app.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
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
