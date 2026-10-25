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
        versionCode = 116
        versionName = "1.16"
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

dependencies {
    // PDF split / extract / lock / unlock
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    // QR and barcode encode/decode
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-devanagari:16.0.1")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
}
