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
        versionCode = 27
        versionName = "1.19.2"
    }
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
    // Bundled model: screenshot pixels are recognized on the phone, no cloud OCR.
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    implementation("com.google.mlkit:translate:17.0.3")
}
