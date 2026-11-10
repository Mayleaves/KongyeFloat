import java.util.Properties

plugins { id("com.android.application") }

val signingFile = rootProject.file("signing.properties")
val localSigning = Properties().apply { if (signingFile.exists()) signingFile.inputStream().use { load(it) } }

android {
    namespace = "com.kongye.floatball"
    compileSdk { version = release(37) { minorApiLevel = 0 } }
    defaultConfig {
        applicationId = "com.kongye.floatball"
        minSdk = 26
        targetSdk = 36
        versionCode = 76
        versionName = "3.0.1"
    }
    // Keep a universal build and emit smaller, functionally identical device builds.
    splits {
        abi {
            isEnable = providers.gradleProperty("kongyeSplitAbis").orNull == "true"
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true
        }
    }
    packaging { jniLibs { useLegacyPackaging = true } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    if (signingFile.exists()) {
        signingConfigs.create("localRelease") {
            storeFile = rootProject.file(localSigning.getProperty("storeFile"))
            storePassword = localSigning.getProperty("storePassword")
            keyAlias = localSigning.getProperty("keyAlias")
            keyPassword = localSigning.getProperty("keyPassword")
        }
    }
    buildTypes {
        debug {
            if (signingFile.exists()) signingConfig = signingConfigs.getByName("localRelease")
        }
        release {
            isMinifyEnabled = false
            if (signingFile.exists()) signingConfig = signingConfigs.getByName("localRelease")
        }
    }
}

dependencies {
    implementation(project(":paddle-ocr"))
    // Bundled model: screenshot pixels are recognized on the phone, no cloud OCR.
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
    implementation("com.google.mlkit:text-recognition-korean:16.0.1")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:language-id:17.0.6")
}
