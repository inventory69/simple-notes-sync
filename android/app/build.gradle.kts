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
        versionCode = 27  // 🆕 v2.0.0
        versionName = "2.0.0"  // 🆕 v2.0.0

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
        viewBinding = false
        buildConfig = true  // Enable BuildConfig generation
        compose = true  // v1.5.0: Jetpack Compose für Settings Redesign
    }

    // v2.1.0: Remove debug artifacts from release APK
    packaging {
        resources {
            excludes += setOf(
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
                "kotlin/**/*.kotlin_builtins",
                "META-INF/*.kotlin_module",
                "META-INF/versions/**",
            )
        }
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

    lint {
        baseline = file("lint-baseline.xml")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    // Existing (bleiben so)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)

    // Splash Screen API (Android 12+)
    implementation(libs.androidx.core.splashscreen)

    // WebDAV
    implementation(libs.sardine.android) {
        exclude(group = "xpp3", module = "xpp3")
    }
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.gson)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // 🔐 v1.7.0: AndroidX Security Crypto für Backup-Verschlüsselung
    implementation(libs.androidx.security.crypto)

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
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // Testing (bleiben so)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.arch.core.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// ✅ v1.6.1: ktlint reaktiviert nach Code-Cleanup
ktlint {
    android = true
    outputToConsole = true
    ignoreFailures = false
    enableExperimentalRules = false
    
    filter {
        exclude("**/generated/**")
        exclude("**/build/**")
        exclude("**/*.kts")
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