# ═══════════════════════════════════════════════════════════════════════
# simple-notes-sync — ProGuard / R8 Configuration
#
# Audit-Referenz:
#   project-docs/simple-notes-sync/v2.3.0/audit-proguard-r8.md
#
# Stand: v2.3.0 (Audit 2026-04-18)
# ═══════════════════════════════════════════════════════════════════════

# ─── Crash-Report-Attribute ──────────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes Signature
-keepattributes *Annotation*

# ─── Library-Warnings (optional/transitive Deps) ─────────────────────
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE
-dontwarn org.w3c.dom.ElementTraversal
-dontwarn android.text.Layout$TextInclusionStrategy
-dontwarn sun.misc.**

# ═══════════════════════════════════════════════════════════════════════
# Sardine WebDAV (com.thegrizzlylabs:sardine-android — KEINE consumer rules)
# ═══════════════════════════════════════════════════════════════════════
# SimpleXML Reflection für PROPFIND-Parsing — Modell- und Handler-Klassen
# müssen vollständig erhalten bleiben.
-keep class com.thegrizzlylabs.sardineandroid.model.** { *; }
-keep class com.thegrizzlylabs.sardineandroid.handler.** { *; }
-keep class com.thegrizzlylabs.sardineandroid.impl.** { *; }
-keep class com.thegrizzlylabs.sardineandroid.DavResource { *; }
# Public-API-Surface — nur das Sardine-Interface explizit (interne Subpackages
# durch obige Klassen-Keeps abgedeckt).
-keepclassmembers interface com.thegrizzlylabs.sardineandroid.Sardine {
    public <methods>;
}
-keepclassmembers class com.thegrizzlylabs.sardineandroid.DavResource {
    public <methods>;
    <init>(...);
}

# ═══════════════════════════════════════════════════════════════════════
# Tink (transitiv via androidx.security:security-crypto — KEINE consumer rules)
# ═══════════════════════════════════════════════════════════════════════
# R8 traces the full call-graph from EncryptedSharedPreferences.create() →
# MasterKey → AeadConfig.register() → Registry → KeyManagers correctly.
# Protobuf deserialization in GeneratedMessageLite uses reflection for
# field access — keep fields on all proto message subclasses.
# No broad class-keeps needed: R8's code analysis handles reachability.
-keepclassmembers class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite {
    <fields>;
}

# ═══════════════════════════════════════════════════════════════════════
# Gson (com.google.code.gson — gson.pro consumer rules vorhanden, App ergänzt)
# ═══════════════════════════════════════════════════════════════════════
-keep class * extends com.google.gson.reflect.TypeToken
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ═══════════════════════════════════════════════════════════════════════
# App-Modelle (Gson-serialisiert)
# ═══════════════════════════════════════════════════════════════════════
# Klassennamen dürfen obfuskiert werden (fromJson(json, X::class.java)
# übersetzt das Class-Literal automatisch). Gson liest Felder über
# Reflection — diese müssen erhalten bleiben.
-keep,allowobfuscation class dev.dettmer.simplenotes.models.** { <init>(...); }
-keepclassmembers class dev.dettmer.simplenotes.models.** {
    <fields>;
}
# NoteRaw ist Note$Companion$NoteRaw — durch das ** Pattern abgedeckt.

# ═══════════════════════════════════════════════════════════════════════
# App-Backup-Datenklassen
# ═══════════════════════════════════════════════════════════════════════
-keep,allowobfuscation class dev.dettmer.simplenotes.backup.BackupData { <init>(...); }
-keep,allowobfuscation class dev.dettmer.simplenotes.backup.AppSettings { <init>(...); }
-keepclassmembers class dev.dettmer.simplenotes.backup.BackupData { <fields>; }
-keepclassmembers class dev.dettmer.simplenotes.backup.AppSettings { <fields>; }

# ═══════════════════════════════════════════════════════════════════════
# WorkManager — SyncWorker wird per FQN aus WorkRequest instanziiert
# ═══════════════════════════════════════════════════════════════════════
-keep class dev.dettmer.simplenotes.sync.SyncWorker { *; }

# ═══════════════════════════════════════════════════════════════════════
# Glance Widgets
# ═══════════════════════════════════════════════════════════════════════
# GlanceAppWidget-Subklassen + ComposableSingletons-Anker (Glance-Compose-Compiler).
# *Action-Pattern deckt alle ActionCallback-Implementierungen ab
# (Toggle…, Show…, Refresh, Open…).
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
-keep class dev.dettmer.simplenotes.widget.*Action { *; }

# ═══════════════════════════════════════════════════════════════════════
# Footer — was bereits durch andere Quellen abgedeckt ist (NICHT duplizieren!)
# ═══════════════════════════════════════════════════════════════════════
# • Manifest-Komponenten (Application/Activity/Receiver/Provider/Service)
#   → AAPT-generierte aapt_rules.txt (siehe build/outputs/mapping/.../configuration.txt)
# • kotlinx-coroutines (MainDispatcherFactory, CoroutineExceptionHandler, …)
#   → kotlinx-coroutines-core consumer-rules.pro
# • Compose UI (TextLayoutResult, Modifier-Elements, …)
#   → androidx.compose.ui consumer-rules.pro
# • OkHttp (PublicSuffixDatabase, animal_sniffer dontwarn)
#   → okhttp3 META-INF/proguard/okhttp3.pro
# • Gson (TypeToken, *Annotation*, @JsonAdapter, @Expose-Felder)
#   → gson META-INF/proguard/gson.pro
# • FileProvider, WorkManager-SystemForegroundService
#   → AAR consumer rules
#
# Wenn du eine neue Library einführst, prüfe `find ~/.gradle/caches -path "*<lib>*"
# -name "*.pro"`. Falls keine consumer-rules vorhanden sind, App-spezifische Rules
# hier ergänzen + im Audit-Dokument vermerken.
# ═══════════════════════════════════════════════════════════════════════
