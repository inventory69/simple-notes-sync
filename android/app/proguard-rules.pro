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
-keepclassmembers class kotlinx.** {
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

# 🔧 v1.8.1 FIX: Breite Regel verwenden statt spezifischer Klassen
# 
# GRUND: NoteRaw ist eine private data class innerhalb von Note.Companion.
# Der JVM-Klassenname ist Note$Companion$NoteRaw, NICHT Note$NoteRaw.
# Die spezifische Regel griff nicht → R8 obfuskierte NoteRaw-Felder
# → Gson konnte keine JSON-Felder matchen → ALLE Notizen unlesbar!
#
# Sichere Lösung: Alle App-Klassen behalten (wie in v1.7.2).
# APK-Größenoptimierung kann in v1.9.0 sicher evaluiert werden.
-keep class dev.dettmer.simplenotes.** { *; }

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

# Glance Widget State (Preferences-basiert, intern via Reflection)
-keep class androidx.glance.appwidget.state.** { *; }
-keep class androidx.datastore.preferences.** { *; }

# Compose Text Layout: Verhindert dass R8 onTextLayout-Callbacks
# als Side-Effect-Free optimiert (behebt Gradient-Regression)
-keepclassmembers class androidx.compose.foundation.text.** {
    <methods>;
}
-keep class androidx.compose.ui.text.TextLayoutResult { *; }
