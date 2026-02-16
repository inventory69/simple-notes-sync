# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# OkHttp platform-specific SSL classes (optional dependencies)
# These are platform-specific implementations that OkHttp uses optionally
# We don't need them for Android, so we ignore warnings about missing classes
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE

# Sardine WebDAV library
-keep class com.thegrizzlylabs.sardineandroid.** { *; }
-dontwarn org.w3c.dom.ElementTraversal

# Keep WebDAV related classes
-keepclassmembers class * {
    @com.thegrizzlylabs.sardineandroid.* *;
}

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ═══════════════════════════════════════════════════════════════════════
# App-specific rules: Only keep what Gson/reflection needs
# ═══════════════════════════════════════════════════════════════════════

# 🔧 v1.8.2: Granulare Regeln statt breiter Wildcard
# Ersetzt die v1.8.1-Notlösung (-keep class dev.dettmer.simplenotes.** { *; })
# die JEGLICHES Tree-Shaking verhinderte → APK > 5MB.

# 1) DATA MODELS — Gson braucht Feldnamen + Konstruktoren
#    NoteRaw ist Note$Companion$NoteRaw (Companion-verschachtelt!)
-keep class dev.dettmer.simplenotes.models.** { *; }
-keep class dev.dettmer.simplenotes.data.** { *; }

# 2) WORKMANAGER — instanziiert SyncWorker via Reflection
-keep class dev.dettmer.simplenotes.sync.SyncWorker { *; }

# 3) BROADCAST RECEIVERS — via AndroidManifest registriert
-keep class dev.dettmer.simplenotes.widget.NoteWidgetReceiver { *; }
-keep class dev.dettmer.simplenotes.** extends android.content.BroadcastReceiver { *; }

# 4) ACTIVITIES & APPLICATION — Android-Framework instanziiert via Reflection
-keep class dev.dettmer.simplenotes.SimpleNotesApplication { *; }
-keep class dev.dettmer.simplenotes.** extends android.app.Activity { *; }
-keep class dev.dettmer.simplenotes.** extends androidx.fragment.app.Fragment { *; }

# v1.7.1: Suppress TextInclusionStrategy warnings on older Android versions
# This class only exists on API 35+ but Compose handles the fallback gracefully
-dontwarn android.text.Layout$TextInclusionStrategy

# ═══════════════════════════════════════════════════════════════════════
# v1.8.1: Widget & Compose Fixes
# ═══════════════════════════════════════════════════════════════════════

# Glance Widget ActionCallbacks (instanziiert via Reflection durch actionRunCallback<T>())
# Ohne diese Rule findet R8 die Klassen nicht zur Laufzeit → Widget-Crash
-keep class dev.dettmer.simplenotes.widget.*Action { *; }
-keep class dev.dettmer.simplenotes.widget.*Receiver { *; }

# Compose Text Layout: Verhindert dass R8 onTextLayout-Callbacks
# als Side-Effect-Free optimiert (behebt Gradient-Regression)
-keepclassmembers class androidx.compose.foundation.text.** {
    <methods>;
}
-keep class androidx.compose.ui.text.TextLayoutResult { *; }
