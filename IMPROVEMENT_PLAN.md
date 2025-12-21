# 🎯 Simple Notes Sync - Verbesserungsplan

**Erstellt am:** 21. Dezember 2025  
**Ziel:** UX-Verbesserungen, Material Design 3, Deutsche Lokalisierung, F-Droid Release

---

## 📋 Übersicht der Probleme & Lösungen

---

## 🆕 NEU: Server-Backup Wiederherstellung

### ❗ Neue Anforderung: Notizen vom Server wiederherstellen

**Problem:**
- User kann keine vollständige Wiederherstellung vom Server machen
- Wenn lokale Daten verloren gehen, keine einfache Recovery
- Nützlich bei Gerätewechsel oder nach App-Neuinstallation

**Lösung:**

#### UI-Komponente (Settings)
```kotlin
// SettingsActivity.kt - Button hinzufügen
<com.google.android.material.button.MaterialButton
    android:id="@+id/buttonRestoreFromServer"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:text="@string/restore_from_server"
    app:icon="@drawable/ic_cloud_download"
    style="@style/Widget.Material3.Button.TonalButton" />

// Click Handler
buttonRestoreFromServer.setOnClickListener {
    showRestoreConfirmationDialog()
}

private fun showRestoreConfirmationDialog() {
    MaterialAlertDialogBuilder(this)
        .setTitle("Vom Server wiederherstellen?")
        .setMessage(
            "⚠️ WARNUNG:\n\n" +
            "• Alle lokalen Notizen werden gelöscht\n" +
            "• Alle Notizen vom Server werden heruntergeladen\n" +
            "• Diese Aktion kann nicht rückgängig gemacht werden\n\n" +
            "Fortfahren?"
        )
        .setIcon(R.drawable.ic_warning)
        .setPositiveButton("Wiederherstellen") { _, _ ->
            restoreFromServer()
        }
        .setNegativeButton("Abbrechen", null)
        .show()
}

private fun restoreFromServer() {
    lifecycleScope.launch {
        try {
            // Show progress dialog
            val progressDialog = MaterialAlertDialogBuilder(this@SettingsActivity)
                .setTitle("Wiederherstelle...")
                .setMessage("Lade Notizen vom Server...")
                .setCancelable(false)
                .create()
            progressDialog.show()
            
            val syncService = WebDavSyncService(this@SettingsActivity)
            val result = syncService.restoreFromServer()
            
            progressDialog.dismiss()
            
            if (result.isSuccess) {
                MaterialAlertDialogBuilder(this@SettingsActivity)
                    .setTitle("✅ Wiederherstellung erfolgreich")
                    .setMessage("${result.restoredCount} Notizen vom Server wiederhergestellt")
                    .setPositiveButton("OK") { _, _ ->
                        // Trigger MainActivity refresh
                        val intent = Intent("dev.dettmer.simplenotes.NOTES_CHANGED")
                        LocalBroadcastManager.getInstance(this@SettingsActivity)
                            .sendBroadcast(intent)
                    }
                    .show()
            } else {
                showErrorDialog(result.errorMessage ?: "Unbekannter Fehler")
            }
        } catch (e: Exception) {
            showErrorDialog(e.message ?: "Wiederherstellung fehlgeschlagen")
        }
    }
}
```

#### Backend-Logik (WebDavSyncService)
```kotlin
// WebDavSyncService.kt
data class RestoreResult(
    val isSuccess: Boolean,
    val restoredCount: Int = 0,
    val errorMessage: String? = null
)

suspend fun restoreFromServer(): RestoreResult = withContext(Dispatchers.IO) {
    try {
        val serverUrl = prefs.getString(Constants.KEY_SERVER_URL, null)
        val username = prefs.getString(Constants.KEY_USERNAME, null)
        val password = prefs.getString(Constants.KEY_PASSWORD, null)
        
        if (serverUrl.isNullOrEmpty() || username.isNullOrEmpty() || password.isNullOrEmpty()) {
            return@withContext RestoreResult(
                isSuccess = false,
                errorMessage = "Server nicht konfiguriert"
            )
        }
        
        // List all remote files
        val sardine = Sardine()
        sardine.setCredentials(username, password)
        
        val remoteFiles = sardine.list(serverUrl)
            .filter { it.name.endsWith(".json") && !it.isDirectory }
        
        if (remoteFiles.isEmpty()) {
            return@withContext RestoreResult(
                isSuccess = false,
                errorMessage = "Keine Notizen auf dem Server gefunden"
            )
        }
        
        val restoredNotes = mutableListOf<Note>()
        
        // Download each note
        for (file in remoteFiles) {
            try {
                val content = sardine.get(file.href).toString(Charsets.UTF_8)
                val note = Note.fromJson(content)
                restoredNotes.add(note)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse ${file.name}: ${e.message}")
                // Continue with other files
            }
        }
        
        if (restoredNotes.isEmpty()) {
            return@withContext RestoreResult(
                isSuccess = false,
                errorMessage = "Keine gültigen Notizen gefunden"
            )
        }
        
        // Clear local storage and save all notes
        withContext(Dispatchers.Main) {
            storage.clearAll()
            restoredNotes.forEach { note ->
                storage.saveNote(note.copy(syncStatus = SyncStatus.SYNCED))
            }
        }
        
        RestoreResult(
            isSuccess = true,
            restoredCount = restoredNotes.size
        )
        
    } catch (e: Exception) {
        Log.e(TAG, "Restore failed", e)
        RestoreResult(
            isSuccess = false,
            errorMessage = e.message ?: "Verbindungsfehler"
        )
    }
}
```

#### Storage Update
```kotlin
// NotesStorage.kt - Methode hinzufügen
fun clearAll() {
    val file = File(context.filesDir, NOTES_FILE)
    if (file.exists()) {
        file.delete()
    }
    // Create empty notes list
    saveAllNotes(emptyList())
}
```

**Betroffene Dateien:**
- `android/app/src/main/java/dev/dettmer/simplenotes/SettingsActivity.kt`
- `android/app/src/main/java/dev/dettmer/simplenotes/sync/WebDavSyncService.kt`
- `android/app/src/main/java/dev/dettmer/simplenotes/storage/NotesStorage.kt`
- `android/app/src/main/res/layout/activity_settings.xml`
- `android/app/src/main/res/values/strings.xml`
- `android/app/src/main/res/drawable/ic_cloud_download.xml` (neu)

**Zeitaufwand:** 2-3 Stunden

**Strings hinzufügen:**
```xml
<string name="restore_from_server">Vom Server wiederherstellen</string>
<string name="restore_confirmation_title">Vom Server wiederherstellen?</string>
<string name="restore_confirmation_message">⚠️ WARNUNG:\n\n• Alle lokalen Notizen werden gelöscht\n• Alle Notizen vom Server werden heruntergeladen\n• Diese Aktion kann nicht rückgängig gemacht werden\n\nFortfahren?</string>
<string name="restoring">Wiederherstelle...</string>
<string name="restoring_message">Lade Notizen vom Server...</string>
<string name="restore_success_title">✅ Wiederherstellung erfolgreich</string>
<string name="restore_success_message">%d Notizen vom Server wiederhergestellt</string>
<string name="restore_error_not_configured">Server nicht konfiguriert</string>
<string name="restore_error_no_notes">Keine Notizen auf dem Server gefunden</string>
<string name="restore_error_invalid_notes">Keine gültigen Notizen gefunden</string>
```

---

### 1️⃣ Server-Status Aktualisierung ⚠️ HOCH
**Problem:**  
- Server-Status wird nicht sofort nach erfolgreichem Verbindungstest grün
- User muss App neu öffnen oder Focus ändern

**Lösung:**
```kotlin
// In SettingsActivity.kt nach testConnection()
private fun testConnection() {
    lifecycleScope.launch {
        try {
            showToast("Teste Verbindung...")
            val syncService = WebDavSyncService(this@SettingsActivity)
            val result = syncService.testConnection()
            
            if (result.isSuccess) {
                showToast("Verbindung erfolgreich!")
                checkServerStatus() // ✅ HIER HINZUFÜGEN
            } else {
                showToast("Verbindung fehlgeschlagen: ${result.errorMessage}")
                checkServerStatus() // ✅ Auch bei Fehler aktualisieren
            }
        } catch (e: Exception) {
            showToast("Fehler: ${e.message}")
            checkServerStatus() // ✅ Auch bei Exception
        }
    }
}
```

**Betroffene Dateien:**
- `android/app/src/main/java/dev/dettmer/simplenotes/SettingsActivity.kt`

**Zeitaufwand:** 15 Minuten

---

### 2️⃣ Auto-Save Indikator im Editor ⚠️ HOCH
**Problem:**  
- User erkennt nicht, dass automatisch gespeichert wird
- Save-Button fehlt → Verwirrung
- Keine visuelle Rückmeldung über Speicher-Status

**Lösung A: Auto-Save mit Indikator (Empfohlen)**
```kotlin
// NoteEditorActivity.kt
private var autoSaveJob: Job? = null
private lateinit var saveStatusTextView: TextView

private fun setupAutoSave() {
    val textWatcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            // Cancel previous save job
            autoSaveJob?.cancel()
            
            // Show "Speichere..."
            saveStatusTextView.text = "💾 Speichere..."
            saveStatusTextView.setTextColor(getColor(android.R.color.darker_gray))
            
            // Debounce: Save after 2 seconds of no typing
            autoSaveJob = lifecycleScope.launch {
                delay(2000)
                saveNoteQuietly()
                
                // Show "Gespeichert ✓"
                saveStatusTextView.text = "✓ Gespeichert"
                saveStatusTextView.setTextColor(getColor(android.R.color.holo_green_dark))
                
                // Hide after 2 seconds
                delay(2000)
                saveStatusTextView.text = ""
            }
        }
        // ... beforeTextChanged, onTextChanged
    }
    
    editTextTitle.addTextChangedListener(textWatcher)
    editTextContent.addTextChangedListener(textWatcher)
}

private fun saveNoteQuietly() {
    val title = editTextTitle.text?.toString()?.trim() ?: ""
    val content = editTextContent.text?.toString()?.trim() ?: ""
    
    if (title.isEmpty() && content.isEmpty()) return
    
    val note = if (existingNote != null) {
        existingNote!!.copy(
            title = title,
            content = content,
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING
        )
    } else {
        Note(
            title = title,
            content = content,
            deviceId = DeviceIdGenerator.getDeviceId(this),
            syncStatus = SyncStatus.LOCAL_ONLY
        ).also { existingNote = it }
    }
    
    storage.saveNote(note)
}
```

**Layout Update:**
```xml
<!-- activity_editor.xml - Oben in Toolbar oder darunter -->
<TextView
    android:id="@+id/textViewSaveStatus"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text=""
    android:textSize="14sp"
    android:layout_gravity="center"
    android:padding="8dp" />
```

**Alternative B: Save-Button behalten + Auto-Save**
- Button zeigt "Gespeichert ✓" nach Auto-Save
- Button disabled wenn keine Änderungen

**Betroffene Dateien:**
- `android/app/src/main/java/dev/dettmer/simplenotes/NoteEditorActivity.kt`
- `android/app/src/main/res/layout/activity_editor.xml`
- `android/app/src/main/res/values/strings.xml`

**Zeitaufwand:** 1-2 Stunden

---

### 3️⃣ GitHub Releases auf Deutsch ⚠️ MITTEL
**Problem:**  
- Release Notes sind auf Englisch
- Asset-Namen teilweise englisch
- Zielgruppe ist deutsch

**Lösung:**
```yaml
# .github/workflows/build-production-apk.yml

# Asset-Namen schon auf Deutsch ✓

# Release Body übersetzen:
body: |
  # 📝 Produktions-Release: Simple Notes Sync v${{ env.VERSION_NAME }}
  
  ## 📊 Build-Informationen
  
  - **Version:** ${{ env.VERSION_NAME }}+${{ env.BUILD_NUMBER }}
  - **Build-Datum:** ${{ env.COMMIT_DATE }}
  - **Commit:** ${{ env.SHORT_SHA }}
  - **Umgebung:** 🟢 **PRODUKTION**
  
  ---
  
  ## 📋 Änderungen
  
  ${{ env.COMMIT_MSG }}
  
  ---
  
  ## 📦 Download & Installation
  
  ### Welche APK sollte ich herunterladen?
  
  | Dein Gerät | Diese APK herunterladen | Größe | Kompatibilität |
  |------------|-------------------------|-------|----------------|
  | 🤷 Unsicher? | `simple-notes-sync-v${{ env.VERSION_NAME }}-universal.apk` | ~3 MB | Funktioniert auf allen Geräten |
  | Modern (ab 2018) | `simple-notes-sync-v${{ env.VERSION_NAME }}-arm64-v8a.apk` | ~2 MB | Schneller, kleiner |
  | Ältere Geräte | `simple-notes-sync-v${{ env.VERSION_NAME }}-armeabi-v7a.apk` | ~2 MB | Ältere ARM-Chips |
  
  ### 📲 Installationsschritte
  1. Lade die passende APK aus den Assets herunter
  2. Aktiviere "Aus unbekannten Quellen installieren" in den Android-Einstellungen
  3. Öffne die heruntergeladene APK-Datei
  4. Folge den Installationsanweisungen
  5. Konfiguriere die WebDAV-Einstellungen in der App
  
  ---
  
  ## ⚙️ Funktionen
  
  - ✅ Automatische WebDAV-Synchronisation alle 30 Minuten (~0,4% Akku/Tag)
  - ✅ Intelligente Gateway-Erkennung (automatische Heimnetzwerk-Erkennung)
  - ✅ Material Design 3 Benutzeroberfläche
  - ✅ Privatsphäre-fokussiert (kein Tracking, keine Analytics)
  - ✅ Offline-First Architektur
  
  ---
  
  ## 🔄 Aktualisierung von vorheriger Version
  
  Installiere diese APK einfach über die bestehende Installation - alle Daten und Einstellungen bleiben erhalten.
  
  ---
  
  ## 📱 Obtanium - Automatische Updates
  
  Erhalte automatische Updates mit [Obtanium](https://github.com/ImranR98/Obtanium/releases/latest).
  
  **Einrichtung:**
  1. Installiere Obtanium über den obigen Link
  2. Füge die App mit dieser URL hinzu: `https://github.com/inventory69/simple-notes-sync`
  3. Aktiviere automatische Updates
  
  ---
  
  ## 🆘 Support
  
  Bei Problemen oder Fragen bitte ein Issue auf GitHub öffnen.
  
  ---
  
  ## 🔒 Datenschutz & Sicherheit
  
  - Alle Daten werden über deinen eigenen WebDAV-Server synchronisiert
  - Keine Analytics oder Tracking von Drittanbietern
  - Keine Internet-Berechtigungen außer für WebDAV-Sync
  - Alle Sync-Vorgänge verschlüsselt (HTTPS)
  - Open Source - prüfe den Code selbst
  
  ---
  
  ## 🛠️ Technische Details
  
  - **Sprache:** Kotlin
  - **UI:** Material Design 3
  - **Sync:** WorkManager + WebDAV
  - **Target SDK:** Android 16 (API 36)
  - **Min SDK:** Android 8.0 (API 26)
```

**Betroffene Dateien:**
- `.github/workflows/build-production-apk.yml`

**Zeitaufwand:** 30 Minuten

---

### 4️⃣ Material Design 3 - Vollständige Migration ⚠️ HOCH

**Basierend auf:** `MATERIAL_DESIGN_3_MIGRATION.md` Plan

**Problem:**  
- Aktuelles Design ist Material Design 2
- Keine Dynamic Colors (Material You)
- Veraltete Komponenten und Farb-Palette
- Keine Android 12+ Features

**Ziel:**
- ✨ Dynamische Farben aus Wallpaper (Material You)
- 🎨 Modern Design Language
- 🔲 Größere Corner Radius (16dp)
- 📱 Material Symbols Icons
- 📝 Material 3 Typography
- 🌓 Perfekter Dark Mode
- ♿ Bessere Accessibility

---

#### Phase 4.1: Theme & Dynamic Colors (15 Min)

**themes.xml:**
```xml
<resources>
    <!-- Base Material 3 Theme -->
    <style name="Base.Theme.SimpleNotes" parent="Theme.Material3.DayNight.NoActionBar">
        <!-- Dynamic Colors (Android 12+) -->
        <item name="colorPrimary">@color/md_theme_primary</item>
        <item name="colorOnPrimary">@color/md_theme_onPrimary</item>
        <item name="colorPrimaryContainer">@color/md_theme_primaryContainer</item>
        <item name="colorOnPrimaryContainer">@color/md_theme_onPrimaryContainer</item>
        
        <item name="colorSecondary">@color/md_theme_secondary</item>
        <item name="colorOnSecondary">@color/md_theme_onSecondary</item>
        <item name="colorSecondaryContainer">@color/md_theme_secondaryContainer</item>
        <item name="colorOnSecondaryContainer">@color/md_theme_onSecondaryContainer</item>
        
        <item name="colorTertiary">@color/md_theme_tertiary</item>
        <item name="colorOnTertiary">@color/md_theme_onTertiary</item>
        <item name="colorTertiaryContainer">@color/md_theme_tertiaryContainer</item>
        <item name="colorOnTertiaryContainer">@color/md_theme_onTertiaryContainer</item>
        
        <item name="colorError">@color/md_theme_error</item>
        <item name="colorOnError">@color/md_theme_onError</item>
        <item name="colorErrorContainer">@color/md_theme_errorContainer</item>
        <item name="colorOnErrorContainer">@color/md_theme_onErrorContainer</item>
        
        <item name="colorSurface">@color/md_theme_surface</item>
        <item name="colorOnSurface">@color/md_theme_onSurface</item>
        <item name="colorSurfaceVariant">@color/md_theme_surfaceVariant</item>
        <item name="colorOnSurfaceVariant">@color/md_theme_onSurfaceVariant</item>
        
        <item name="colorOutline">@color/md_theme_outline</item>
        <item name="colorOutlineVariant">@color/md_theme_outlineVariant</item>
        
        <item name="android:colorBackground">@color/md_theme_background</item>
        <item name="colorOnBackground">@color/md_theme_onBackground</item>
        
        <!-- Shapes -->
        <item name="shapeAppearanceSmallComponent">@style/ShapeAppearance.SimpleNotes.SmallComponent</item>
        <item name="shapeAppearanceMediumComponent">@style/ShapeAppearance.SimpleNotes.MediumComponent</item>
        <item name="shapeAppearanceLargeComponent">@style/ShapeAppearance.SimpleNotes.LargeComponent</item>
        
        <!-- Status bar -->
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:windowLightStatusBar">true</item>
    </style>
    
    <style name="Theme.SimpleNotes" parent="Base.Theme.SimpleNotes" />
    
    <!-- Shapes -->
    <style name="ShapeAppearance.SimpleNotes.SmallComponent" parent="ShapeAppearance.Material3.SmallComponent">
        <item name="cornerFamily">rounded</item>
        <item name="cornerSize">12dp</item>
    </style>
    
    <style name="ShapeAppearance.SimpleNotes.MediumComponent" parent="ShapeAppearance.Material3.MediumComponent">
        <item name="cornerFamily">rounded</item>
        <item name="cornerSize">16dp</item>
    </style>
    
    <style name="ShapeAppearance.SimpleNotes.LargeComponent" parent="ShapeAppearance.Material3.LargeComponent">
        <item name="cornerFamily">rounded</item>
        <item name="cornerSize">24dp</item>
    </style>
</resources>
```

**colors.xml (Material 3 Baseline - Grün/Natur Theme):**
```xml
<resources>
    <!-- Light Theme Colors -->
    <color name="md_theme_primary">#006C4C</color>
    <color name="md_theme_onPrimary">#FFFFFF</color>
    <color name="md_theme_primaryContainer">#89F8C7</color>
    <color name="md_theme_onPrimaryContainer">#002114</color>
    
    <color name="md_theme_secondary">#4D6357</color>
    <color name="md_theme_onSecondary">#FFFFFF</color>
    <color name="md_theme_secondaryContainer">#CFE9D9</color>
    <color name="md_theme_onSecondaryContainer">#0A1F16</color>
    
    <color name="md_theme_tertiary">#3D6373</color>
    <color name="md_theme_onTertiary">#FFFFFF</color>
    <color name="md_theme_tertiaryContainer">#C1E8FB</color>
    <color name="md_theme_onTertiaryContainer">#001F29</color>
    
    <color name="md_theme_error">#BA1A1A</color>
    <color name="md_theme_onError">#FFFFFF</color>
    <color name="md_theme_errorContainer">#FFDAD6</color>
    <color name="md_theme_onErrorContainer">#410002</color>
    
    <color name="md_theme_background">#FBFDF9</color>
    <color name="md_theme_onBackground">#191C1A</color>
    
    <color name="md_theme_surface">#FBFDF9</color>
    <color name="md_theme_onSurface">#191C1A</color>
    <color name="md_theme_surfaceVariant">#DCE5DD</color>
    <color name="md_theme_onSurfaceVariant">#404943</color>
    
    <color name="md_theme_outline">#707973</color>
    <color name="md_theme_outlineVariant">#BFC9C2</color>
</resources>
```

**values-night/colors.xml (neu erstellen):**
```xml
<resources>
    <!-- Dark Theme Colors -->
    <color name="md_theme_primary">#6DDBAC</color>
    <color name="md_theme_onPrimary">#003826</color>
    <color name="md_theme_primaryContainer">#005138</color>
    <color name="md_theme_onPrimaryContainer">#89F8C7</color>
    
    <color name="md_theme_secondary">#B3CCBD</color>
    <color name="md_theme_onSecondary">#1F352A</color>
    <color name="md_theme_secondaryContainer">#354B40</color>
    <color name="md_theme_onSecondaryContainer">#CFE9D9</color>
    
    <color name="md_theme_tertiary">#A5CCE0</color>
    <color name="md_theme_onTertiary">#073543</color>
    <color name="md_theme_tertiaryContainer">#254B5B</color>
    <color name="md_theme_onTertiaryContainer">#C1E8FB</color>
    
    <color name="md_theme_error">#FFB4AB</color>
    <color name="md_theme_onError">#690005</color>
    <color name="md_theme_errorContainer">#93000A</color>
    <color name="md_theme_onErrorContainer">#FFDAD6</color>
    
    <color name="md_theme_background">#191C1A</color>
    <color name="md_theme_onBackground">#E1E3DF</color>
    
    <color name="md_theme_surface">#191C1A</color>
    <color name="md_theme_onSurface">#E1E3DF</color>
    <color name="md_theme_surfaceVariant">#404943</color>
    <color name="md_theme_onSurfaceVariant">#BFC9C2</color>
    
    <color name="md_theme_outline">#8A938C</color>
    <color name="md_theme_outlineVariant">#404943</color>
</resources>
```

**MainActivity.kt - Dynamic Colors aktivieren:**
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Enable Dynamic Colors (Android 12+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        DynamicColors.applyToActivityIfAvailable(this)
    }
    
    setContentView(R.layout.activity_main)
    // ...
}

// Import hinzufügen:
import com.google.android.material.color.DynamicColors
import android.os.Build
```

**Betroffene Dateien:**
- `android/app/src/main/res/values/themes.xml`
- `android/app/src/main/res/values/colors.xml`
- `android/app/src/main/res/values-night/colors.xml` (neu)
- `android/app/src/main/java/dev/dettmer/simplenotes/MainActivity.kt`

**Zeitaufwand:** 15 Minuten

---

#### Phase 4.2: MainActivity Layout (10 Min)

**activity_main.xml - Material 3 Update:**
```xml
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/coordinator"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <com.google.android.material.appbar.AppBarLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content">

        <com.google.android.material.appbar.MaterialToolbar
            android:id="@+id/toolbar"
            android:layout_width="match_parent"
            android:layout_height="?attr/actionBarSize"
            android:elevation="0dp"
            app:title="@string/app_name"
            app:titleTextAppearance="@style/TextAppearance.Material3.HeadlineMedium" />

    </com.google.android.material.appbar.AppBarLayout>

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/recyclerView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:clipToPadding="false"
        android:paddingVertical="8dp"
        app:layout_behavior="@string/appbar_scrolling_view_behavior" />

    <!-- Empty State -->
    <com.google.android.material.card.MaterialCardView
        android:id="@+id/cardEmptyState"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:layout_margin="32dp"
        android:visibility="gone"
        app:cardElevation="0dp"
        app:cardCornerRadius="24dp"
        app:cardBackgroundColor="?attr/colorSurfaceVariant">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="48dp"
            android:gravity="center">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="📝"
                android:textSize="72sp"
                android:layout_marginBottom="16dp" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/no_notes_yet"
                android:textAppearance="@style/TextAppearance.Material3.HeadlineSmall"
                android:gravity="center"
                android:layout_marginBottom="8dp" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Tippe auf + um deine erste Notiz zu erstellen"
                android:textAppearance="@style/TextAppearance.Material3.BodyMedium"
                android:textColor="?attr/colorOnSurfaceVariant"
                android:gravity="center" />

        </LinearLayout>

    </com.google.android.material.card.MaterialCardView>

    <!-- Extended FAB -->
    <com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
        android:id="@+id/fabAddNote"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|end"
        android:layout_margin="16dp"
        android:text="@string/add_note"
        app:icon="@drawable/ic_add_24" />

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

**Betroffene Dateien:**
- `android/app/src/main/res/layout/activity_main.xml`
- `android/app/src/main/java/dev/dettmer/simplenotes/MainActivity.kt`

**Zeitaufwand:** 10 Minuten

---

#### Phase 4.3: Note Item Card (10 Min)

**item_note.xml - Material 3 Card:**
```xml
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    style="@style/Widget.Material3.CardView.Elevated"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_margin="8dp"
    app:cardElevation="2dp"
    app:cardCornerRadius="16dp"
    android:clickable="true"
    android:focusable="true">

    <androidx.constraintlayout.widget.ConstraintLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="16dp">

        <TextView
            android:id="@+id/textViewTitle"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:text="Notiz Titel"
            android:textAppearance="@style/TextAppearance.Material3.TitleMedium"
            android:textColor="?attr/colorOnSurface"
            android:maxLines="2"
            android:ellipsize="end"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintEnd_toStartOf="@id/imageViewSyncStatus"
            app:layout_constraintTop_toTopOf="parent" />

        <ImageView
            android:id="@+id/imageViewSyncStatus"
            android:layout_width="20dp"
            android:layout_height="20dp"
            android:layout_marginStart="8dp"
            app:tint="?attr/colorOnSurfaceVariant"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintTop_toTopOf="parent" />

        <TextView
            android:id="@+id/textViewContent"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:text="Notiz Inhalt Vorschau..."
            android:textAppearance="@style/TextAppearance.Material3.BodyMedium"
            android:textColor="?attr/colorOnSurfaceVariant"
            android:maxLines="3"
            android:ellipsize="end"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintTop_toBottomOf="@id/textViewTitle" />

        <TextView
            android:id="@+id/textViewTimestamp"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp"
            android:text="vor 5 Minuten"
            android:textAppearance="@style/TextAppearance.Material3.LabelSmall"
            android:textColor="?attr/colorOnSurfaceVariant"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toBottomOf="@id/textViewContent" />

    </androidx.constraintlayout.widget.ConstraintLayout>

</com.google.android.material.card.MaterialCardView>
```

**Betroffene Dateien:**
- `android/app/src/main/res/layout/item_note.xml`

**Zeitaufwand:** 10 Minuten

---

#### Phase 4.4: Editor Layout (10 Min)

**activity_editor.xml - Material 3 TextInputLayouts:**
```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fitsSystemWindows="true">

    <com.google.android.material.appbar.AppBarLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content">

        <com.google.android.material.appbar.MaterialToolbar
            android:id="@+id/toolbar"
            android:layout_width="match_parent"
            android:layout_height="?attr/actionBarSize" />

    </com.google.android.material.appbar.AppBarLayout>

    <androidx.core.widget.NestedScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:layout_behavior="@string/appbar_scrolling_view_behavior">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="16dp">

            <!-- Save Status Chip -->
            <com.google.android.material.chip.Chip
                android:id="@+id/chipSaveStatus"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_gravity="center_horizontal"
                android:layout_marginBottom="16dp"
                android:visibility="gone"
                app:chipIcon="@drawable/ic_check"
                style="@style/Widget.Material3.Chip.Assist" />

            <!-- Title Input -->
            <com.google.android.material.textfield.TextInputLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:hint="@string/title"
                android:layout_marginBottom="16dp"
                app:counterEnabled="true"
                app:counterMaxLength="100"
                app:endIconMode="clear_text"
                app:boxCornerRadiusTopStart="16dp"
                app:boxCornerRadiusTopEnd="16dp"
                app:boxCornerRadiusBottomStart="16dp"
                app:boxCornerRadiusBottomEnd="16dp"
                style="@style/Widget.Material3.TextInputLayout.OutlinedBox">

                <com.google.android.material.textfield.TextInputEditText
                    android:id="@+id/editTextTitle"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:inputType="textCapSentences"
                    android:maxLength="100"
                    android:textAppearance="@style/TextAppearance.Material3.HeadlineSmall" />

            </com.google.android.material.textfield.TextInputLayout>

            <!-- Content Input -->
            <com.google.android.material.textfield.TextInputLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:hint="@string/content"
                app:counterEnabled="true"
                app:counterMaxLength="10000"
                app:boxCornerRadiusTopStart="16dp"
                app:boxCornerRadiusTopEnd="16dp"
                app:boxCornerRadiusBottomStart="16dp"
                app:boxCornerRadiusBottomEnd="16dp"
                style="@style/Widget.Material3.TextInputLayout.OutlinedBox">

                <com.google.android.material.textfield.TextInputEditText
                    android:id="@+id/editTextContent"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:inputType="textMultiLine|textCapSentences"
                    android:gravity="top"
                    android:minLines="10"
                    android:maxLength="10000"
                    android:textAppearance="@style/TextAppearance.Material3.BodyLarge" />

            </com.google.android.material.textfield.TextInputLayout>

        </LinearLayout>

    </androidx.core.widget.NestedScrollView>

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

**Betroffene Dateien:**
- `android/app/src/main/res/layout/activity_editor.xml`

**Zeitaufwand:** 10 Minuten

---

#### Phase 4.5: Material Symbols Icons (15 Min)

**Icons erstellen in `res/drawable/`:**

1. **ic_add_24.xml**
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorOnPrimary">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/>
</vector>
```

2. **ic_sync_24.xml**
3. **ic_settings_24.xml**
4. **ic_cloud_done_24.xml**
5. **ic_cloud_sync_24.xml**
6. **ic_warning_24.xml**
7. **ic_server_24.xml**
8. **ic_person_24.xml**
9. **ic_lock_24.xml**
10. **ic_cloud_download_24.xml**
11. **ic_check_24.xml**

Tool: https://fonts.google.com/icons

**Betroffene Dateien:**
- `android/app/src/main/res/drawable/` (11 neue Icons)

**Zeitaufwand:** 15 Minuten

---

#### Phase 4.6: Splash Screen (30 Min)

**themes.xml - Splash Screen hinzufügen:**
```xml
<style name="Theme.SimpleNotes.Starting" parent="Theme.SplashScreen">
    <item name="windowSplashScreenBackground">?attr/colorPrimary</item>
    <item name="windowSplashScreenAnimatedIcon">@drawable/ic_app_logo</item>
    <item name="windowSplashScreenAnimationDuration">500</item>
    <item name="postSplashScreenTheme">@style/Theme.SimpleNotes</item>
</style>
```

**AndroidManifest.xml:**
```xml
<application
    android:theme="@style/Theme.SimpleNotes.Starting">
    
    <activity
        android:name=".MainActivity"
        android:theme="@style/Theme.SimpleNotes.Starting">
```

**MainActivity.kt:**
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    // Handle splash screen
    installSplashScreen()
    
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
}

// Import:
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
```

**build.gradle.kts:**
```kotlin
dependencies {
    implementation("androidx.core:core-splashscreen:1.0.1")
}
```

**Betroffene Dateien:**
- `android/app/src/main/res/values/themes.xml`
- `android/app/src/main/AndroidManifest.xml`
- `android/app/src/main/java/dev/dettmer/simplenotes/MainActivity.kt`
- `android/app/build.gradle.kts`

**Zeitaufwand:** 30 Minuten

---

### Material 3 Gesamtaufwand: ~90 Minuten

---

### 5️⃣ F-Droid Release Vorbereitung ⚠️ MITTEL
**Problem:**  
- Keine F-Droid Metadata vorhanden
- Keine Build-Variante ohne Google-Dependencies

**Lösung - Verzeichnisstruktur:**
```
simple-notes-sync/
├── metadata/
│   └── de-DE/
│       ├── full_description.txt
│       ├── short_description.txt
│       ├── title.txt
│       └── changelogs/
│           ├── 1.txt
│           ├── 2.txt
│           └── 3.txt
└── fastlane/
    └── metadata/
        └── android/
            └── de-DE/
                ├── images/
                │   ├── icon.png
                │   ├── featureGraphic.png
                │   └── phoneScreenshots/
                │       ├── 1.png
                │       ├── 2.png
                │       └── 3.png
                ├── full_description.txt
                ├── short_description.txt
                └── title.txt
```

**metadata/de-DE/full_description.txt:**
```
Simple Notes Sync - Deine privaten Notizen, selbst gehostet

Eine minimalistische, datenschutzfreundliche Notizen-App mit automatischer WebDAV-Synchronisation.

HAUPTMERKMALE:

🔒 Datenschutz
• Alle Daten auf deinem eigenen Server
• Keine Cloud-Dienste von Drittanbietern
• Kein Tracking oder Analytics
• Open Source - transparent und überprüfbar

☁️ Automatische Synchronisation
• WebDAV-Sync alle 30 Minuten
• Intelligente Netzwerkerkennung
• Nur im WLAN (konfigurierbar)
• Minimaler Akkuverbrauch (~0,4%/Tag)

✨ Einfach & Schnell
• Klare, aufgeräumte Benutzeroberfläche
• Blitzschnelle Notiz-Erfassung
• Offline-First Design
• Material Design 3

🔧 Flexibel
• Funktioniert mit jedem WebDAV-Server
• Nextcloud, ownCloud, Apache, etc.
• Docker-Setup verfügbar
• Konflikterkennung und -lösung

TECHNISCHE DETAILS:

• Keine Google Services benötigt
• Keine unnötigen Berechtigungen
• Minimale App-Größe (~2-3 MB)
• Android 8.0+ kompatibel
• Kotlin + Material Design 3

PERFECT FÜR:

• Schnelle Notizen und Ideen
• Einkaufslisten
• Todo-Listen
• Persönliche Gedanken
• Alle, die Wert auf Datenschutz legen

Der Quellcode ist verfügbar auf: https://github.com/inventory69/simple-notes-sync
```

**metadata/de-DE/short_description.txt:**
```
Minimalistische Notizen-App mit selbst-gehosteter WebDAV-Synchronisation
```

**metadata/de-DE/title.txt:**
```
Simple Notes Sync
```

**Build-Flavor ohne Google:**
```kotlin
// build.gradle.kts
android {
    flavorDimensions += "version"
    productFlavors {
        create("fdroid") {
            dimension = "version"
            // Keine Google/Firebase Dependencies
        }
        create("playstore") {
            dimension = "version"
            // Optional: Google Services für Analytics etc.
        }
    }
}

dependencies {
    // Base dependencies (alle Flavors)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    
    // PlayStore specific (optional)
    "playstoreImplementation"("com.google.firebase:firebase-analytics:21.5.0")
}
```

**Betroffene Dateien:**
- `metadata/` (neu)
- `fastlane/` (neu)
- `android/app/build.gradle.kts` (Flavors hinzufügen)
- Screenshots erstellen (phone + tablet)

**Zeitaufwand:** 3-4 Stunden (inkl. Screenshots)

---

### 5️⃣ Material Design 3 Theme ⚠️ HOCH
**Problem:**  
- Aktuelles Theme ist Material Design 2
- Keine Dynamic Colors (Material You)
- Veraltete Farb-Palette

**Lösung - themes.xml:**
```xml
<!-- res/values/themes.xml -->
<resources>
    <!-- Base Material 3 Theme with Dynamic Colors -->
    <style name="Base.Theme.SimpleNotes" parent="Theme.Material3.DayNight">
        <!-- Dynamic Colors (Android 12+) -->
        <item name="colorPrimary">@color/md_theme_primary</item>
        <item name="colorOnPrimary">@color/md_theme_onPrimary</item>
        <item name="colorPrimaryContainer">@color/md_theme_primaryContainer</item>
        <item name="colorOnPrimaryContainer">@color/md_theme_onPrimaryContainer</item>
        
        <item name="colorSecondary">@color/md_theme_secondary</item>
        <item name="colorOnSecondary">@color/md_theme_onSecondary</item>
        <item name="colorSecondaryContainer">@color/md_theme_secondaryContainer</item>
        <item name="colorOnSecondaryContainer">@color/md_theme_onSecondaryContainer</item>
        
        <item name="colorTertiary">@color/md_theme_tertiary</item>
        <item name="colorOnTertiary">@color/md_theme_onTertiary</item>
        <item name="colorTertiaryContainer">@color/md_theme_tertiaryContainer</item>
        <item name="colorOnTertiaryContainer">@color/md_theme_onTertiaryContainer</item>
        
        <item name="colorError">@color/md_theme_error</item>
        <item name="colorOnError">@color/md_theme_onError</item>
        <item name="colorErrorContainer">@color/md_theme_errorContainer</item>
        <item name="colorOnErrorContainer">@color/md_theme_onErrorContainer</item>
        
        <item name="colorSurface">@color/md_theme_surface</item>
        <item name="colorOnSurface">@color/md_theme_onSurface</item>
        <item name="colorSurfaceVariant">@color/md_theme_surfaceVariant</item>
        <item name="colorOnSurfaceVariant">@color/md_theme_onSurfaceVariant</item>
        
        <item name="colorOutline">@color/md_theme_outline</item>
        <item name="colorOutlineVariant">@color/md_theme_outlineVariant</item>
        
        <item name="android:colorBackground">@color/md_theme_background</item>
        <item name="colorOnBackground">@color/md_theme_onBackground</item>
        
        <!-- Shapes -->
        <item name="shapeAppearanceSmallComponent">@style/ShapeAppearance.SimpleNotes.SmallComponent</item>
        <item name="shapeAppearanceMediumComponent">@style/ShapeAppearance.SimpleNotes.MediumComponent</item>
        <item name="shapeAppearanceLargeComponent">@style/ShapeAppearance.SimpleNotes.LargeComponent</item>
        
        <!-- Typography -->
        <item name="textAppearanceHeadlineLarge">@style/TextAppearance.SimpleNotes.HeadlineLarge</item>
        <item name="textAppearanceBodyLarge">@style/TextAppearance.SimpleNotes.BodyLarge</item>
        
        <!-- Status bar -->
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:windowLightStatusBar">true</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
    </style>
    
    <style name="Theme.SimpleNotes" parent="Base.Theme.SimpleNotes" />
    
    <!-- Shapes -->
    <style name="ShapeAppearance.SimpleNotes.SmallComponent" parent="ShapeAppearance.Material3.SmallComponent">
        <item name="cornerFamily">rounded</item>
        <item name="cornerSize">12dp</item>
    </style>
    
    <style name="ShapeAppearance.SimpleNotes.MediumComponent" parent="ShapeAppearance.Material3.MediumComponent">
        <item name="cornerFamily">rounded</item>
        <item name="cornerSize">16dp</item>
    </style>
    
    <style name="ShapeAppearance.SimpleNotes.LargeComponent" parent="ShapeAppearance.Material3.LargeComponent">
        <item name="cornerFamily">rounded</item>
        <item name="cornerSize">24dp</item>
    </style>
</resources>
```

**colors.xml (Material 3 Baseline):**
```xml
<!-- res/values/colors.xml -->
<resources>
    <!-- Light Theme Colors -->
    <color name="md_theme_primary">#006C4C</color>
    <color name="md_theme_onPrimary">#FFFFFF</color>
    <color name="md_theme_primaryContainer">#89F8C7</color>
    <color name="md_theme_onPrimaryContainer">#002114</color>
    
    <color name="md_theme_secondary">#4D6357</color>
    <color name="md_theme_onSecondary">#FFFFFF</color>
    <color name="md_theme_secondaryContainer">#CFE9D9</color>
    <color name="md_theme_onSecondaryContainer">#0A1F16</color>
    
    <color name="md_theme_tertiary">#3D6373</color>
    <color name="md_theme_onTertiary">#FFFFFF</color>
    <color name="md_theme_tertiaryContainer">#C1E8FB</color>
    <color name="md_theme_onTertiaryContainer">#001F29</color>
    
    <color name="md_theme_error">#BA1A1A</color>
    <color name="md_theme_onError">#FFFFFF</color>
    <color name="md_theme_errorContainer">#FFDAD6</color>
    <color name="md_theme_onErrorContainer">#410002</color>
    
    <color name="md_theme_background">#FBFDF9</color>
    <color name="md_theme_onBackground">#191C1A</color>
    
    <color name="md_theme_surface">#FBFDF9</color>
    <color name="md_theme_onSurface">#191C1A</color>
    <color name="md_theme_surfaceVariant">#DCE5DD</color>
    <color name="md_theme_onSurfaceVariant">#404943</color>
    
    <color name="md_theme_outline">#707973</color>
    <color name="md_theme_outlineVariant">#BFC9C2</color>
</resources>
```

**Dynamic Colors aktivieren (MainActivity.kt):**
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    // Enable dynamic colors (Android 12+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        DynamicColors.applyToActivityIfAvailable(this)
    }
    
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    // ...
}
```

**Dependency hinzufügen:**
```kotlin
// build.gradle.kts
dependencies {
    implementation("com.google.android.material:material:1.11.0")
}
```

**Betroffene Dateien:**
- `android/app/src/main/res/values/themes.xml`
- `android/app/src/main/res/values/colors.xml`
- `android/app/src/main/res/values-night/colors.xml` (neu)
- `android/app/src/main/java/dev/dettmer/simplenotes/MainActivity.kt`
- `android/app/build.gradle.kts`

**Zeitaufwand:** 2-3 Stunden

---

### 6️⃣ Settings UI mit Material 3 ⚠️ MITTEL
**Problem:**  
- Plain TextInputLayouts ohne Icons
- Keine visuellen Gruppierungen
- Server-Status Wechsel nicht animiert

**Lösung - activity_settings.xml:**
```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fitsSystemWindows="true">

    <com.google.android.material.appbar.AppBarLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content">

        <com.google.android.material.appbar.MaterialToolbar
            android:id="@+id/toolbar"
            android:layout_width="match_parent"
            android:layout_height="?attr/actionBarSize"
            app:title="@string/settings"
            app:navigationIcon="?attr/homeAsUpIndicator" />

    </com.google.android.material.appbar.AppBarLayout>

    <androidx.core.widget.NestedScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:layout_behavior="@string/appbar_scrolling_view_behavior">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="16dp">

            <!-- Server Settings Card -->
            <com.google.android.material.card.MaterialCardView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="16dp"
                app:cardElevation="2dp"
                app:cardCornerRadius="16dp">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="16dp">

                    <TextView
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:text="@string/server_settings"
                        android:textAppearance="@style/TextAppearance.Material3.TitleMedium"
                        android:layout_marginBottom="16dp" />

                    <com.google.android.material.textfield.TextInputLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:hint="@string/server_url"
                        android:layout_marginBottom="12dp"
                        app:startIconDrawable="@drawable/ic_server"
                        app:helperText="z.B. https://cloud.example.com/remote.php/dav/files/username/notes"
                        style="@style/Widget.Material3.TextInputLayout.FilledBox">

                        <com.google.android.material.textfield.TextInputEditText
                            android:id="@+id/editTextServerUrl"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:inputType="textUri" />

                    </com.google.android.material.textfield.TextInputLayout>

                    <com.google.android.material.textfield.TextInputLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:hint="@string/username"
                        android:layout_marginBottom="12dp"
                        app:startIconDrawable="@drawable/ic_person"
                        style="@style/Widget.Material3.TextInputLayout.FilledBox">

                        <com.google.android.material.textfield.TextInputEditText
                            android:id="@+id/editTextUsername"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:inputType="text" />

                    </com.google.android.material.textfield.TextInputLayout>

                    <com.google.android.material.textfield.TextInputLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:hint="@string/password"
                        app:startIconDrawable="@drawable/ic_lock"
                        style="@style/Widget.Material3.TextInputLayout.FilledBox"
                        app:endIconMode="password_toggle">

                        <com.google.android.material.textfield.TextInputEditText
                            android:id="@+id/editTextPassword"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:inputType="textPassword" />

                    </com.google.android.material.textfield.TextInputLayout>

                    <!-- Server Status Chip -->
                    <com.google.android.material.chip.Chip
                        android:id="@+id/chipServerStatus"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="8dp"
                        android:text="🔍 Prüfe Server..."
                        app:chipIcon="@drawable/ic_sync"
                        style="@style/Widget.Material3.Chip.Assist" />

                    <!-- Connection Test Button -->
                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/buttonTestConnection"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="16dp"
                        android:text="@string/test_connection"
                        app:icon="@drawable/ic_check_circle"
                        style="@style/Widget.Material3.Button.TonalButton" />

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/buttonSyncNow"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="8dp"
                        android:text="@string/sync_now"
                        app:icon="@drawable/ic_sync"
                        style="@style/Widget.Material3.Button" />

                </LinearLayout>

            </com.google.android.material.card.MaterialCardView>

            <!-- Auto-Sync Card -->
            <com.google.android.material.card.MaterialCardView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="16dp"
                app:cardElevation="2dp"
                app:cardCornerRadius="16dp">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="16dp">

                    <TextView
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:text="Automatische Synchronisation"
                        android:textAppearance="@style/TextAppearance.Material3.TitleMedium"
                        android:layout_marginBottom="8dp" />

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:layout_marginBottom="12dp">

                        <LinearLayout
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:orientation="vertical">

                            <TextView
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                android:text="@string/auto_sync"
                                android:textAppearance="@style/TextAppearance.Material3.BodyLarge" />

                            <TextView
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                android:text="Synchronisiert alle 30 Minuten"
                                android:textAppearance="@style/TextAppearance.Material3.BodySmall"
                                android:textColor="?android:attr/textColorSecondary" />

                        </LinearLayout>

                        <com.google.android.material.switchmaterial.SwitchMaterial
                            android:id="@+id/switchAutoSync"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:layout_gravity="center_vertical" />

                    </LinearLayout>

                    <!-- Info Banner -->
                    <com.google.android.material.card.MaterialCardView
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        app:cardBackgroundColor="?attr/colorPrimaryContainer"
                        app:cardElevation="0dp"
                        app:cardCornerRadius="12dp">

                        <LinearLayout
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:orientation="horizontal"
                            android:padding="12dp">

                            <TextView
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:text="ℹ️"
                                android:textSize="24sp"
                                android:layout_marginEnd="12dp" />

                            <TextView
                                android:layout_width="0dp"
                                android:layout_height="wrap_content"
                                android:layout_weight="1"
                                android:text="Auto-Sync funktioniert nur im selben WLAN-Netzwerk wie dein Server. Minimaler Akkuverbrauch (~0.4%/Tag)."
                                android:textAppearance="@style/TextAppearance.Material3.BodySmall"
                                android:textColor="?attr/colorOnPrimaryContainer" />

                        </LinearLayout>

                    </com.google.android.material.card.MaterialCardView>

                </LinearLayout>

            </com.google.android.material.card.MaterialCardView>

        </LinearLayout>

    </androidx.core.widget.NestedScrollView>

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

**Animierter Server-Status (SettingsActivity.kt):**
```kotlin
private fun updateServerStatus(status: ServerStatus) {
    val chip = findViewById<Chip>(R.id.chipServerStatus)
    
    // Animate transition
    chip.animate()
        .alpha(0f)
        .setDuration(150)
        .withEndAction {
            when (status) {
                ServerStatus.CHECKING -> {
                    chip.text = "🔍 Prüfe Server..."
                    chip.chipBackgroundColor = ColorStateList.valueOf(
                        getColor(R.color.md_theme_surfaceVariant)
                    )
                    chip.setChipIconResource(R.drawable.ic_sync)
                }
                ServerStatus.REACHABLE -> {
                    chip.text = "✅ Server erreichbar"
                    chip.chipBackgroundColor = ColorStateList.valueOf(
                        getColor(R.color.md_theme_primaryContainer)
                    )
                    chip.setChipIconResource(R.drawable.ic_check_circle)
                }
                ServerStatus.UNREACHABLE -> {
                    chip.text = "❌ Nicht erreichbar"
                    chip.chipBackgroundColor = ColorStateList.valueOf(
                        getColor(R.color.md_theme_errorContainer)
                    )
                    chip.setChipIconResource(R.drawable.ic_error)
                }
                ServerStatus.NOT_CONFIGURED -> {
                    chip.text = "⚠️ Nicht konfiguriert"
                    chip.chipBackgroundColor = ColorStateList.valueOf(
                        getColor(R.color.md_theme_surfaceVariant)
                    )
                    chip.setChipIconResource(R.drawable.ic_warning)
                }
            }
            
            chip.animate()
                .alpha(1f)
                .setDuration(150)
                .start()
        }
        .start()
}

enum class ServerStatus {
    CHECKING,
    REACHABLE,
    UNREACHABLE,
    NOT_CONFIGURED
}
```

**Icons benötigt (drawable/):**
- `ic_server.xml`
- `ic_person.xml`
- `ic_lock.xml`
- `ic_check_circle.xml`
- `ic_sync.xml`
- `ic_error.xml`
- `ic_warning.xml`

**Betroffene Dateien:**
- `android/app/src/main/res/layout/activity_settings.xml`
- `android/app/src/main/java/dev/dettmer/simplenotes/SettingsActivity.kt`
- `android/app/src/main/res/drawable/` (Icons)

**Zeitaufwand:** 3-4 Stunden

---

### 7️⃣ Main Activity mit Material 3 Cards ⚠️ HOCH
**Problem:**  
- Notizen in einfachen ListItems
- Keine Elevation/Shadow
- Swipe-to-Delete fehlt

**Lösung - item_note.xml:**
```xml
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_margin="8dp"
    app:cardElevation="2dp"
    app:cardCornerRadius="16dp"
    android:clickable="true"
    android:focusable="true"
    app:rippleColor="?attr/colorPrimary">

    <androidx.constraintlayout.widget.ConstraintLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="16dp">

        <TextView
            android:id="@+id/textViewTitle"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:text="Notiz Titel"
            android:textAppearance="@style/TextAppearance.Material3.TitleMedium"
            android:textColor="?attr/colorOnSurface"
            android:maxLines="2"
            android:ellipsize="end"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintEnd_toStartOf="@id/chipSyncStatus"
            app:layout_constraintTop_toTopOf="parent" />

        <com.google.android.material.chip.Chip
            android:id="@+id/chipSyncStatus"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="☁️"
            android:textSize="12sp"
            app:chipMinHeight="24dp"
            style="@style/Widget.Material3.Chip.Assist.Elevated"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintTop_toTopOf="parent" />

        <TextView
            android:id="@+id/textViewContent"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:text="Notiz Inhalt Vorschau..."
            android:textAppearance="@style/TextAppearance.Material3.BodyMedium"
            android:textColor="?attr/colorOnSurfaceVariant"
            android:maxLines="3"
            android:ellipsize="end"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintTop_toBottomOf="@id/textViewTitle" />

        <TextView
            android:id="@+id/textViewTimestamp"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp"
            android:text="vor 5 Minuten"
            android:textAppearance="@style/TextAppearance.Material3.LabelSmall"
            android:textColor="?attr/colorOnSurfaceVariant"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toBottomOf="@id/textViewContent" />

    </androidx.constraintlayout.widget.ConstraintLayout>

</com.google.android.material.card.MaterialCardView>
```

**Swipe-to-Delete (MainActivity.kt):**
```kotlin
private fun setupRecyclerView() {
    recyclerView = findViewById(R.id.recyclerView)
    adapter = NotesAdapter { note ->
        openNoteEditor(note.id)
    }
    
    recyclerView.adapter = adapter
    recyclerView.layoutManager = LinearLayoutManager(this)
    
    // Swipe-to-Delete
    val swipeHandler = object : ItemTouchHelper.SimpleCallback(
        0,
        ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
    ) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean = false

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val position = viewHolder.adapterPosition
            val note = adapter.notes[position]
            
            // Delete with undo
            adapter.removeNote(position)
            storage.deleteNote(note.id)
            
            Snackbar.make(
                findViewById(R.id.coordinator),
                "Notiz gelöscht",
                Snackbar.LENGTH_LONG
            ).setAction("RÜCKGÄNGIG") {
                adapter.addNote(position, note)
                storage.saveNote(note)
            }.show()
        }

        override fun onChildDraw(
            c: Canvas,
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            dX: Float,
            dY: Float,
            actionState: Int,
            isCurrentlyActive: Boolean
        ) {
            val itemView = viewHolder.itemView
            
            val paint = Paint()
            paint.color = getColor(R.color.md_theme_errorContainer)
            
            // Draw background
            if (dX > 0) {
                c.drawRect(
                    itemView.left.toFloat(),
                    itemView.top.toFloat(),
                    dX,
                    itemView.bottom.toFloat(),
                    paint
                )
            } else {
                c.drawRect(
                    itemView.right.toFloat() + dX,
                    itemView.top.toFloat(),
                    itemView.right.toFloat(),
                    itemView.bottom.toFloat(),
                    paint
                )
            }
            
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }
    }
    
    ItemTouchHelper(swipeHandler).attachToRecyclerView(recyclerView)
}
```

**Empty State (activity_main.xml):**
```xml
<com.google.android.material.card.MaterialCardView
    android:id="@+id/cardEmptyState"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_gravity="center"
    android:layout_margin="32dp"
    android:visibility="gone"
    app:cardElevation="0dp"
    app:cardCornerRadius="24dp"
    app:cardBackgroundColor="?attr/colorSurfaceVariant">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="48dp"
        android:gravity="center">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="📝"
            android:textSize="72sp"
            android:layout_marginBottom="16dp" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Noch keine Notizen"
            android:textAppearance="@style/TextAppearance.Material3.HeadlineSmall"
            android:layout_marginBottom="8dp" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Tippe auf + um deine erste Notiz zu erstellen"
            android:textAppearance="@style/TextAppearance.Material3.BodyMedium"
            android:textColor="?attr/colorOnSurfaceVariant"
            android:gravity="center" />

    </LinearLayout>

</com.google.android.material.card.MaterialCardView>
```

**Extended FAB (activity_main.xml):**
```xml
<com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
    android:id="@+id/fabAddNote"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_gravity="bottom|end"
    android:layout_margin="16dp"
    android:text="Neue Notiz"
    app:icon="@drawable/ic_add" />
```

**Betroffene Dateien:**
- `android/app/src/main/res/layout/activity_main.xml`
- `android/app/src/main/res/layout/item_note.xml`
- `android/app/src/main/java/dev/dettmer/simplenotes/MainActivity.kt`
- `android/app/src/main/java/dev/dettmer/simplenotes/adapters/NotesAdapter.kt`

**Zeitaufwand:** 4-5 Stunden

---

### 8️⃣ Editor mit Material 3 ⚠️ MITTEL
**Problem:**  
- Einfache EditText-Felder
- Kein Character Counter
- Keine visuelle Trennung

**Lösung - activity_editor.xml:**
```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fitsSystemWindows="true">

    <com.google.android.material.appbar.AppBarLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content">

        <com.google.android.material.appbar.MaterialToolbar
            android:id="@+id/toolbar"
            android:layout_width="match_parent"
            android:layout_height="?attr/actionBarSize"
            app:navigationIcon="@drawable/ic_close" />

    </com.google.android.material.appbar.AppBarLayout>

    <androidx.core.widget.NestedScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:layout_behavior="@string/appbar_scrolling_view_behavior">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="16dp">

            <!-- Save Status Indicator -->
            <com.google.android.material.chip.Chip
                android:id="@+id/chipSaveStatus"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_gravity="center_horizontal"
                android:layout_marginBottom="16dp"
                android:visibility="gone"
                app:chipIcon="@drawable/ic_check"
                style="@style/Widget.Material3.Chip.Assist" />

            <!-- Title Input -->
            <com.google.android.material.textfield.TextInputLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:hint="Titel"
                android:layout_marginBottom="16dp"
                app:counterEnabled="true"
                app:counterMaxLength="100"
                style="@style/Widget.Material3.TextInputLayout.FilledBox">

                <com.google.android.material.textfield.TextInputEditText
                    android:id="@+id/editTextTitle"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:inputType="textCapSentences"
                    android:maxLength="100"
                    android:textAppearance="@style/TextAppearance.Material3.HeadlineSmall" />

            </com.google.android.material.textfield.TextInputLayout>

            <!-- Content Input -->
            <com.google.android.material.textfield.TextInputLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:hint="Deine Notiz..."
                app:counterEnabled="true"
                app:counterMaxLength="10000"
                style="@style/Widget.Material3.TextInputLayout.FilledBox">

                <com.google.android.material.textfield.TextInputEditText
                    android:id="@+id/editTextContent"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:inputType="textMultiLine|textCapSentences"
                    android:gravity="top"
                    android:minLines="10"
                    android:maxLength="10000"
                    android:textAppearance="@style/TextAppearance.Material3.BodyLarge" />

            </com.google.android.material.textfield.TextInputLayout>

            <!-- Metadata Card -->
            <com.google.android.material.card.MaterialCardView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="16dp"
                app:cardElevation="0dp"
                app:cardCornerRadius="12dp"
                app:cardBackgroundColor="?attr/colorSurfaceVariant">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="12dp">

                    <TextView
                        android:id="@+id/textViewCreatedAt"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:text="Erstellt: Heute um 14:30"
                        android:textAppearance="@style/TextAppearance.Material3.LabelSmall"
                        android:textColor="?attr/colorOnSurfaceVariant" />

                    <TextView
                        android:id="@+id/textViewModifiedAt"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="4dp"
                        android:text="Geändert: vor 2 Minuten"
                        android:textAppearance="@style/TextAppearance.Material3.LabelSmall"
                        android:textColor="?attr/colorOnSurfaceVariant" />

                </LinearLayout>

            </com.google.android.material.card.MaterialCardView>

        </LinearLayout>

    </androidx.core.widget.NestedScrollView>

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

**Betroffene Dateien:**
- `android/app/src/main/res/layout/activity_editor.xml`
- `android/app/src/main/java/dev/dettmer/simplenotes/NoteEditorActivity.kt`

**Zeitaufwand:** 2 Stunden

---

### 9️⃣ Splash Screen mit Material 3 ⚠️ NIEDRIG
**Problem:**  
- Kein moderner Splash Screen

**Lösung:**
```xml
<!-- res/values/themes.xml -->
<style name="Theme.SimpleNotes.Starting" parent="Theme.SplashScreen">
    <item name="windowSplashScreenBackground">?attr/colorPrimary</item>
    <item name="windowSplashScreenAnimatedIcon">@drawable/ic_app_logo</item>
    <item name="windowSplashScreenAnimationDuration">500</item>
    <item name="postSplashScreenTheme">@style/Theme.SimpleNotes</item>
</style>
```

```xml
<!-- AndroidManifest.xml -->
<application
    android:theme="@style/Theme.SimpleNotes.Starting">
    
    <activity
        android:name=".MainActivity"
        android:theme="@style/Theme.SimpleNotes.Starting">
```

```kotlin
// MainActivity.kt
override fun onCreate(savedInstanceState: Bundle?) {
    // Handle the splash screen transition
    installSplashScreen()
    
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
}
```

**Dependency:**
```kotlin
implementation("androidx.core:core-splashscreen:1.0.1")
```

**Betroffene Dateien:**
- `android/app/src/main/res/values/themes.xml`
- `android/app/src/main/AndroidManifest.xml`
- `android/app/src/main/java/dev/dettmer/simplenotes/MainActivity.kt`
- `android/app/build.gradle.kts`

**Zeitaufwand:** 30 Minuten

---

### 🔟 Deutsche Lokalisierung ⚠️ MITTEL
**Problem:**  
- Einige Strings noch auf Englisch
- Release Notes englisch
- Error Messages englisch

**Lösung - strings.xml vervollständigen:**
```xml
<resources>
    <!-- App -->
    <string name="app_name">Simple Notes</string>
    
    <!-- Main Activity -->
    <string name="no_notes_yet">Noch keine Notizen.\nTippe + um eine zu erstellen.</string>
    <string name="add_note">Notiz hinzufügen</string>
    <string name="sync">Synchronisieren</string>
    <string name="settings">Einstellungen</string>
    
    <!-- Editor -->
    <string name="edit_note">Notiz bearbeiten</string>
    <string name="new_note">Neue Notiz</string>
    <string name="title">Titel</string>
    <string name="content">Inhalt</string>
    <string name="save">Speichern</string>
    <string name="delete">Löschen</string>
    <string name="saving">Speichere…</string>
    <string name="saved">✓ Gespeichert</string>
    <string name="auto_save_hint">Änderungen werden automatisch gespeichert</string>
    
    <!-- Settings -->
    <string name="server_settings">Server-Einstellungen</string>
    <string name="server_url">Server URL</string>
    <string name="server_url_hint">z.B. https://cloud.example.com/remote.php/dav/files/username/notes</string>
    <string name="username">Benutzername</string>
    <string name="password">Passwort</string>
    <string name="wifi_settings">WLAN-Einstellungen</string>
    <string name="home_ssid">Heim-WLAN SSID</string>
    <string name="auto_sync">Auto-Sync aktiviert</string>
    <string name="auto_sync_description">Synchronisiert alle 30 Minuten</string>
    <string name="auto_sync_info">Auto-Sync funktioniert nur im selben WLAN-Netzwerk wie dein Server. Minimaler Akkuverbrauch (~0.4%/Tag).</string>
    <string name="test_connection">Verbindung testen</string>
    <string name="sync_now">Jetzt synchronisieren</string>
    <string name="sync_status">Sync-Status</string>
    
    <!-- Server Status -->
    <string name="server_status_checking">🔍 Prüfe Server…</string>
    <string name="server_status_reachable">✅ Server erreichbar</string>
    <string name="server_status_unreachable">❌ Nicht erreichbar</string>
    <string name="server_status_not_configured">⚠️ Nicht konfiguriert</string>
    
    <!-- Messages -->
    <string name="testing_connection">Teste Verbindung…</string>
    <string name="connection_successful">Verbindung erfolgreich!</string>
    <string name="connection_failed">Verbindung fehlgeschlagen: %s</string>
    <string name="synchronizing">Synchronisiere…</string>
    <string name="sync_successful">Erfolgreich! %d Notizen synchronisiert</string>
    <string name="sync_failed">Sync fehlgeschlagen: %s</string>
    <string name="sync_conflicts">Sync abgeschlossen. %d Konflikte erkannt!</string>
    <string name="note_saved">Notiz gespeichert</string>
    <string name="note_deleted">Notiz gelöscht</string>
    <string name="undo">RÜCKGÄNGIG</string>
    
    <!-- Dialogs -->
    <string name="delete_note_title">Notiz löschen?</string>
    <string name="delete_note_message">Diese Aktion kann nicht rückgängig gemacht werden.</string>
    <string name="cancel">Abbrechen</string>
    <string name="battery_optimization_title">Hintergrund-Synchronisation</string>
    <string name="battery_optimization_message">Damit die App im Hintergrund synchronisieren kann, muss die Akku-Optimierung deaktiviert werden.\n\nBitte wähle \'Nicht optimieren\' für Simple Notes.</string>
    <string name="open_settings">Einstellungen öffnen</string>
    <string name="later">Später</string>
    
    <!-- Errors -->
    <string name="error_title_empty">Titel oder Inhalt darf nicht leer sein</string>
    <string name="error_network">Netzwerkfehler: %s</string>
    <string name="error_server">Server-Fehler: %s</string>
    <string name="error_auth">Authentifizierung fehlgeschlagen</string>
    <string name="error_unknown">Unbekannter Fehler: %s</string>
    
    <!-- Notifications -->
    <string name="notification_channel_name">Notizen Synchronisierung</string>
    <string name="notification_channel_description">Benachrichtigungen über Sync-Status</string>
    <string name="notification_sync_success">Sync erfolgreich</string>
    <string name="notification_sync_success_text">%d Notizen synchronisiert</string>
    <string name="notification_sync_failed">Sync fehlgeschlagen</string>
    <string name="notification_sync_failed_text">%s</string>
</resources>
```

**Betroffene Dateien:**
- `android/app/src/main/res/values/strings.xml`
- `.github/workflows/build-production-apk.yml`
- Alle `.kt` Dateien mit hardcoded strings

**Zeitaufwand:** 2 Stunden

---

## 📊 Zusammenfassung & Prioritäten

### Phase 1: Kritische UX-Fixes (Sofort) ⚡
**Zeitaufwand: ~3-4 Stunden**

1. ✅ Server-Status Aktualisierung (15 min)
2. ✅ Auto-Save Indikator (1-2 h)
3. ✅ GitHub Releases auf Deutsch (30 min)
4. ✅ Server-Backup Wiederherstellung (2-3 h)

### Phase 2: Material Design 3 Migration (1 Tag) 🎨
**Zeitaufwand: ~90 Minuten**

5. ✅ Theme & Dynamic Colors (15 min)
6. ✅ MainActivity Layout (10 min)
7. ✅ Note Item Card (10 min)
8. ✅ Editor Layout (10 min)
9. ✅ Settings Layout (10 min) - aus Phase 3 vorgezogen
10. ✅ Material Icons (15 min)
11. ✅ Splash Screen (30 min)

### Phase 3: Advanced UI Features (2-3 Tage) 🚀
**Zeitaufwand: ~4-5 Stunden**

12. ✅ Swipe-to-Delete (1 h)
13. ✅ Empty State (30 min)
14. ✅ Animierte Server-Status Änderung (1 h)
15. ✅ Deutsche Lokalisierung vervollständigen (1-2 h)

### Phase 4: F-Droid Release (1 Tag) 📦
**Zeitaufwand: ~4 Stunden**

16. ✅ F-Droid Metadata (3-4 h)
17. ✅ F-Droid Build-Flavor (30 min)

---

## 🎯 Empfohlene Reihenfolge

### Woche 1: Fundament & Kritische Fixes
**Tag 1 (3-4h):** Phase 1 - Kritische UX-Fixes
- Server-Status sofort grün nach Test
- Auto-Save mit visuellem Feedback
- Deutsche Release Notes
- **Server-Backup Funktion** ← NEU & WICHTIG

**Tag 2 (1.5h):** Phase 2 Start - Material Design 3 Foundation
- Theme & Dynamic Colors aktivieren
- MainActivity Layout modernisieren
- Note Item Cards verbessern

**Tag 3 (1.5h):** Phase 2 Fortführung - Material Design 3
- Editor Layout upgraden
- Settings Layout modernisieren
- Material Icons erstellen
- Splash Screen implementieren

### Woche 2: Polish & Release
**Tag 4 (2-3h):** Phase 3 - Advanced Features
- Swipe-to-Delete mit Animation
- Empty State mit Illustration
- Server-Status Animationen
- Deutsche Strings vervollständigen

**Tag 5 (4h):** Phase 4 - F-Droid Vorbereitung
- Metadata erstellen
- Screenshots machen
- Build-Flavor konfigurieren

---

## 🆕 Neue Features Zusammenfassung

### Server-Backup Wiederherstellung
**Warum wichtig:**
- ✅ Gerätewechsel einfach
- ✅ Recovery nach App-Neuinstallation
- ✅ Datensicherheit erhöht
- ✅ User-Vertrauen gestärkt

**Wo in der UI:**
- Settings Activity → neuer Button "Vom Server wiederherstellen"
- Warn-Dialog vor Ausführung
- Progress-Dialog während Download
- Success-Dialog mit Anzahl wiederhergestellter Notizen

**Backend:**
- `WebDavSyncService.restoreFromServer()`
- `NotesStorage.clearAll()`
- Vollständiger Download aller Server-Notizen
- Überschreibt lokale Daten komplett

---

## 📝 Material Design 3 - Schnellreferenz

### Umgesetzt wird:
✅ **Dynamic Colors** - Farben aus Wallpaper (Android 12+)
✅ **Material 3 Components** - Cards, Buttons, TextInputs
✅ **16dp Corner Radius** - Modernere abgerundete Ecken
✅ **Material Symbols** - Neue Icon-Familie
✅ **Typography Scale** - Material 3 Text-Styles
✅ **Dark Mode** - Perfekt abgestimmte Nacht-Farben
✅ **Splash Screen API** - Android 12+ Native Splash

### Design-Token:
- **Primary:** Grün (#006C4C) - Natur, Notizen, Wachstum
- **Secondary:** Grau-Grün - Subtil, harmonisch
- **Surface:** Hell/Dunkel - Abhängig von Theme
- **Shapes:** Small 12dp, Medium 16dp, Large 24dp

---

## 📋 Checkliste vor Start

- [ ] Branch erstellen: `git checkout -b feature/ux-improvements`
- [ ] Backup vom aktuellen Stand
- [ ] Material 3 Dependency prüfen: `com.google.android.material:material:1.11.0`
- [ ] Android Studio aktualisiert
- [ ] Testgerät mit Android 12+ für Dynamic Colors

---

## 🧪 Testing nach Abschluss

### Manuell:
- [ ] Alle Layouts auf Smartphone (Phone)
- [ ] Alle Layouts auf Tablet
- [ ] Dark Mode überall
- [ ] Light Mode überall
- [ ] Dynamic Colors (Android 12+)
- [ ] Server-Backup: Restore funktioniert
- [ ] Server-Backup: Dialog-Texte korrekt
- [ ] Auto-Save: Indikator erscheint
- [ ] Auto-Save: Speichert nach 2s
- [ ] Server-Status: Wird sofort aktualisiert
- [ ] Swipe-to-Delete: Animation smooth
- [ ] Empty State: Zeigt sich bei 0 Notizen
- [ ] Splash Screen: Erscheint beim Start
- [ ] Alle Icons: Richtige Farbe (Tint)
- [ ] Alle Buttons: Funktionieren
- [ ] Deutsch: Keine englischen Strings mehr

### Automatisch:
- [ ] Build erfolgreich (Debug)
- [ ] Build erfolgreich (Release)
- [ ] APK Size akzeptabel (<5 MB)
- [ ] Keine Lint-Errors
- [ ] ProGuard-Regeln funktionieren

---

## 📚 Referenzen & Tools

### Material Design 3:
- [Material Design 3 Guidelines](https://m3.material.io/)
- [Material Theme Builder](https://material-foundation.github.io/material-theme-builder/)
- [Material Symbols Icons](https://fonts.google.com/icons)

### Android:
- [Splash Screen API](https://developer.android.com/develop/ui/views/launch/splash-screen)
- [Dynamic Colors](https://developer.android.com/develop/ui/views/theming/dynamic-colors)

---

## 📝 Nächste Schritte

Soll ich mit **Phase 1** (kritische UX-Fixes + Server-Backup) beginnen? 

### Was ich jetzt machen würde:

1. **Server-Backup implementieren** (2-3h)
   - Höchste Priorität: User-requested Feature
   - Kritisch für Datensicherheit
   
2. **Server-Status sofort aktualisieren** (15 min)
   - Schneller Win
   - Verbessert UX sofort
   
3. **Auto-Save Indikator** (1-2h)
   - Eliminiert Verwirrung
   - Modernes Pattern

4. **Material 3 Foundation** (90 min)
   - Theme & Colors
   - Basis für alles weitere

Diese 4 Tasks würden den größten Impact haben und sind in ~4-6 Stunden machbar! 🚀
