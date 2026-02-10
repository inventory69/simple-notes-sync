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

# Gson data models (serialized/deserialized via reflection)
-keep class dev.dettmer.simplenotes.models.Note { *; }
-keep class dev.dettmer.simplenotes.models.Note$NoteRaw { *; }
-keep class dev.dettmer.simplenotes.models.ChecklistItem { *; }
-keep class dev.dettmer.simplenotes.models.DeletionRecord { *; }
-keep class dev.dettmer.simplenotes.models.DeletionTracker { *; }
-keep class dev.dettmer.simplenotes.backup.BackupData { *; }
-keep class dev.dettmer.simplenotes.backup.BackupResult { *; }

# Keep enum values (used in serialization and widget state)
-keepclassmembers enum dev.dettmer.simplenotes.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# v1.7.1: Suppress TextInclusionStrategy warnings on older Android versions
# This class only exists on API 35+ but Compose handles the fallback gracefully
-dontwarn android.text.Layout$TextInclusionStrategy
