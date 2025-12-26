plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

import java.util.Properties
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

android {
    namespace = "dev.dettmer.simplenotes"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.dettmer.simplenotes"
        minSdk = 24
        targetSdk = 36
        versionCode = 3  // 🔥 Bugfix: Spurious Sync Error Notifications + Sync Icon Bug
        versionName = "1.1.1"  // 🔥 Bugfix: Server-Erreichbarkeits-Check + Notification-Improvements

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // 🔥 NEU: Build Date für About Screen
        buildConfigField("String", "BUILD_DATE", "\"${getBuildDate()}\"")
    }
    
    // Enable multiple APKs per ABI for smaller downloads
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = true  // Also generate universal APK
        }
    }
    
    // Product Flavors for F-Droid and standard builds
    flavorDimensions += "distribution"
    productFlavors {
        create("fdroid") {
            dimension = "distribution"
            // F-Droid builds have no proprietary dependencies
            // All dependencies in this project are already FOSS-compatible
        }
        
        create("standard") {
            dimension = "distribution"
            // Standard builds can include Play Services in the future if needed
        }
    }

    // Signing configuration for release builds
    signingConfigs {
        create("release") {
            // Load keystore configuration from key.properties file
            val keystorePropertiesFile = rootProject.file("key.properties")
            if (keystorePropertiesFile.exists()) {
                val keystoreProperties = Properties()
                keystoreProperties.load(FileInputStream(keystorePropertiesFile))
                
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use release signing config if available, otherwise debug
            signingConfig = if (rootProject.file("key.properties").exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true  // Enable BuildConfig generation
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // Existing (bleiben so)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Splash Screen API (Android 12+)
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Unsere Dependencies (DIREKT mit Versionen - viel einfacher!)
    implementation("com.github.thegrizzlylabs:sardine-android:0.8") {
        exclude(group = "xpp3", module = "xpp3")
    }
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // LocalBroadcastManager für UI Refresh
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")

    // Testing (bleiben so)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// 🔥 NEU: Helper function für Build Date
fun getBuildDate(): String {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return dateFormat.format(Date())
}