plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)  // v1.5.0: Jetpack Compose Compiler
    alias(libs.plugins.ktlint)  // ✅ v1.6.1: Reaktiviert nach Code-Cleanup
    alias(libs.plugins.detekt)
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
        versionCode = 22  // 🔧 v1.8.2: Sync-Stuck Fix, SSL Certs, APK Size, Widget Scroll, Keyboard
        versionName = "1.8.2"  // 🔧 v1.8.2: Stability & Polish Release

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    
    // Disable Google dependency metadata for F-Droid/IzzyOnDroid compatibility
    dependenciesInfo {
        includeInApk = false  // Removes DEPENDENCY_INFO_BLOCK from APK
        includeInBundle = false  // Also disable for AAB (Google Play)
    }
    
    // Product Flavors for F-Droid and standard builds
    // Note: APK splits are disabled to ensure single APK output
    flavorDimensions += "distribution"
    productFlavors {
        create("fdroid") {
            dimension = "distribution"
            // F-Droid builds have no proprietary dependencies
            // All dependencies in this project are already FOSS-compatible
            // No APK splits - F-Droid expects single universal APK
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
        debug {
            // ⚡ v1.3.1: Debug-Builds können parallel zur Release-App installiert werden
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
            
            // Optionales separates Icon-Label für Debug-Builds
            resValue("string", "app_name_debug", "Simple Notes (Debug)")
        }
        
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
        compose = true  // v1.5.0: Jetpack Compose für Settings Redesign
    }
    
    // v1.7.0: Mock Android framework classes in unit tests (Log, etc.)
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    
    // v1.5.0 Hotfix: Strong Skipping Mode für bessere 120Hz Performance
    // v1.6.1: Feature ist ab dieser Kotlin/Compose Version bereits Standard
    // composeCompiler { }

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

    // SwipeRefreshLayout für Pull-to-Refresh
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // 🔐 v1.7.0: AndroidX Security Crypto für Backup-Verschlüsselung
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // ═══════════════════════════════════════════════════════════════════════
    // v1.5.0: Jetpack Compose für Settings Redesign
    // ═══════════════════════════════════════════════════════════════════════
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // ═══════════════════════════════════════════════════════════════════════
    // 🆕 v1.8.0: Homescreen Widgets
    // ═══════════════════════════════════════════════════════════════════════
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

    // Testing (bleiben so)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// ✅ v1.6.1: ktlint reaktiviert nach Code-Cleanup
ktlint {
    android = true
    outputToConsole = true
    ignoreFailures = true  // Parser-Probleme in WebDavSyncService.kt und build.gradle.kts
    enableExperimentalRules = false
    
    filter {
        exclude("**/generated/**")
        exclude("**/build/**")
        // Legacy adapters with ktlint parser issues
        exclude("**/adapters/NotesAdapter.kt")
        exclude("**/SettingsActivity.kt")
    }
}

// ⚡ v1.3.1: detekt-Konfiguration
detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    baseline = file("$rootDir/config/detekt/baseline.xml")
    
    // Parallel-Verarbeitung für schnellere Checks
    parallel = true
}

// 📋 v1.8.0: Copy F-Droid changelogs to assets for post-update dialog
// Single source of truth: F-Droid changelogs are reused in the app
tasks.register<Copy>("copyChangelogsToAssets") {
    description = "Copies F-Droid changelogs to app assets for post-update dialog"
    
    from("$rootDir/../fastlane/metadata/android") {
        include("*/changelogs/*.txt")
    }
    
    into("$projectDir/src/main/assets/changelogs")
    
    // Preserve directory structure: en-US/20.txt, de-DE/20.txt
    eachFile {
        val parts = relativePath.segments
        if (parts.size >= 3) {
            // parts[0] = locale (en-US, de-DE)
            // parts[1] = "changelogs"
            // parts[2] = version file (20.txt)
            relativePath = RelativePath(true, parts[0], parts[2])
        }
    }
    
    includeEmptyDirs = false
}

// Run before preBuild to ensure changelogs are available
tasks.named("preBuild") {
    dependsOn("copyChangelogsToAssets")
}