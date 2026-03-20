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

# Preserve line numbers for crash reports (Crashlytics, Play Console, logcat)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# OkHttp platform-specific SSL classes (optional dependencies)
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE

# ═══════════════════════════════════════════════════════════════════════
# Sardine WebDAV library
# ═══════════════════════════════════════════════════════════════════════
# Sardine uses JAXB-style XML annotations for WebDAV PROPFIND responses.
# Only keep the model/handler classes that Sardine needs for XML parsing.
-keep class com.thegrizzlylabs.sardineandroid.model.** { *; }
-keep class com.thegrizzlylabs.sardineandroid.handler.** { *; }
-keep class com.thegrizzlylabs.sardineandroid.DavResource { *; }
-keep class com.thegrizzlylabs.sardineandroid.impl.** { *; }
-keepclassmembers class com.thegrizzlylabs.sardineandroid.** {
    public <methods>;
}
-dontwarn org.w3c.dom.ElementTraversal

# ═══════════════════════════════════════════════════════════════════════
# Coroutines — keep only what's needed for reflection
# ═══════════════════════════════════════════════════════════════════════
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ═══════════════════════════════════════════════════════════════════════
# Gson — only keep Gson's reflection infrastructure, not the entire library
# ═══════════════════════════════════════════════════════════════════════
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
# Gson uses TypeToken via reflection for generic type resolution
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
# Keep @SerializedName annotation values
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ═══════════════════════════════════════════════════════════════════════
# App-specific rules: Only keep what Gson/reflection needs
# ═══════════════════════════════════════════════════════════════════════

# 🔧 v1.8.2: Granulare Regeln statt breiter Wildcard
# 🔧 v1.10.0 Audit: BackupData-Fix, tote data/-Regel entfernt
# 🔧 v2.1.0 Audit: Gson-Regeln verschärft, Sardine granularer

# 1) DATA MODELS — Gson braucht Feldnamen + Konstruktoren
#    NoteRaw ist Note$Companion$NoteRaw (Companion-verschachtelt!)
-keep class dev.dettmer.simplenotes.models.** { *; }

# 2) BACKUP — BackupData + AppSettings für Gson-Serialisierung
-keep class dev.dettmer.simplenotes.backup.BackupData { *; }
-keep class dev.dettmer.simplenotes.backup.AppSettings { *; }

# 3) WORKMANAGER — instanziiert SyncWorker via Reflection
-keep class dev.dettmer.simplenotes.sync.SyncWorker { *; }

# 4) BROADCAST RECEIVERS — via AndroidManifest registriert
-keep class dev.dettmer.simplenotes.** extends android.content.BroadcastReceiver { *; }

# 5) ACTIVITIES & APPLICATION — Android-Framework instanziiert via Reflection
-keep class dev.dettmer.simplenotes.SimpleNotesApplication { *; }
-keep class dev.dettmer.simplenotes.** extends android.app.Activity { *; }

# v1.7.1: Suppress TextInclusionStrategy warnings on older Android versions
-dontwarn android.text.Layout$TextInclusionStrategy

# ═══════════════════════════════════════════════════════════════════════
# v1.8.1: Widget & Compose Fixes
# ═══════════════════════════════════════════════════════════════════════

# Glance Widget ActionCallbacks (instanziiert via Reflection durch actionRunCallback<T>())
-keep class dev.dettmer.simplenotes.widget.*Action { *; }
-keep class * implements androidx.glance.appwidget.action.ActionCallback { *; }

# Compose Text Layout: Nur TextLayoutResult behalten (für onTextLayout-Callbacks)
-keep class androidx.compose.ui.text.TextLayoutResult { *; }
