import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/**
 * Bluetooth helper – a second, tiny APK.
 *
 * Android 13 made BluetoothAdapter.enable()/disable() a no-op for apps that TARGET API 33+.
 * The restriction depends on targetSdk, not on the Android version of the phone, so this
 * module deliberately targets API 32 and is still allowed to switch Bluetooth silently.
 * The main app (targetSdk 35) asks this helper through a signature-checked ContentProvider.
 */
android {
    namespace = "pl.shortcuts.bthelper"
    compileSdk = 35

    defaultConfig {
        applicationId = "pl.shortcuts.bthelper"
        minSdk = 29
        //noinspection ExpiredTargetSdkVersion,OldTargetApi – intentional, see comment above. Do NOT raise to 33+.
        targetSdk = 32
        versionCode = 1
        versionName = "1.0.0"
    }

    // Must be signed with the SAME key as the main app – the helper rejects callers with other signatures.
    val keystorePropsFile = rootProject.file("keystore.properties")
    val keystoreProps = Properties().apply {
        if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
    }
    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint {
        // The old target is the whole point of this module.
        disable += setOf("ExpiredTargetSdkVersion", "OldTargetApi")
    }
}
