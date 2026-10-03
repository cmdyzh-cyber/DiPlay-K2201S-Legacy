plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.shihab.diplay.legacyprobe"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.shihab.diplay.legacyprobe"
        minSdk = 19
        targetSdk = 28
        versionCode = 2
        versionName = "0.2-api19-usb-probe"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}
