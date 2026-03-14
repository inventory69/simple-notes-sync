import re
import os

# Keys to remove (strings)
UNUSED_STRINGS = {
    "add_note", "sync", "settings", "empty_state_emoji", "create_text_note",
    "create_checklist", "no_notes_yet", "note_title_placeholder",
    "note_content_placeholder", "note_timestamp_placeholder", "empty_checklist",
    "sync_status", "sync_syncing", "sync_completed", "sync_error",
    "sync_status_syncing", "sync_phase_checking", "sync_phase_saving",
    "legacy_delete_with_server", "legacy_delete_local_only",
    "auto_sync", "auto_sync_info", "backup_decryption_error",
    "backup_decryption_password", "backup_encryption_error_mismatch",
    "backup_encryption_error_too_short", "backup_mode_merge_full",
    "backup_mode_overwrite_full", "backup_mode_replace_full",
    "backup_restore_title", "backup_restore_warning",
    "battery_optimization_dialog_message", "editor_mode_edit", "editor_mode_preview",
    "file_logging_privacy_notice", "language_changed_restart",
    "reorder_item", "restore_button", "restore_confirmation_message",
    "restore_confirmation_title", "restore_error", "restore_from_server",
    "restore_progress", "restore_success", "server_settings",
    "server_status_not_configured", "server_url",
    "settings_about_app_version", "settings_about_app_version_loading",
    "settings_about_developer", "settings_about_github", "settings_about_license",
    "settings_backup_info", "settings_backup_local_title", "settings_backup_server_title",
    "settings_debug_delete_logs", "settings_debug_export_logs",
    "settings_debug_file_logging", "settings_debug_file_logging_desc",
    "settings_interval_15min", "settings_interval_30min", "settings_interval_60min",
    "settings_language_subtitle", "settings_markdown_manual_button",
    "settings_markdown_manual_hint", "settings_server_status_not_configured",
    "settings_sync_auto_off", "settings_sync_auto_on",
    "snackbar_already_synced", "snackbar_synced_count", "status_checking",
    "sync_auto_sync_enabled", "sync_auto_sync_info",
    "sync_interval_15min_subtitle", "sync_interval_30min_subtitle",
    "sync_interval_60min_subtitle", "sync_interval_info", "sync_interval_section",
    "sync_offline_mode_title", "sync_section_advanced", "sync_section_background",
    "sync_section_instant", "sync_section_network", "sync_settings",
    "sync_wifi_only_blocked", "toast_backup_failed", "toast_backup_success",
    "toast_battery_optimization", "toast_link_error", "toast_logs_delete_error",
    "toast_no_logs_to_delete", "toast_restore_failed", "toast_restore_success",
    "toast_sync_interval_changed", "version_not_available", "widget_note_not_found",
}

# Plurals to remove
UNUSED_PLURALS = {
    "notes_count", "notes_deleted_local", "notes_deleted_server", "notes_synced"
}


def remove_unused_from_file(filepath, string_keys, plural_keys):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    original = content
    removed_count = 0

    # Remove single-line and multi-line <string name="KEY">...</string>
    for key in string_keys:
        pattern = r'[ \t]*<string name="' + re.escape(key) + r'"[^>]*>.*?</string>\n?'
        new_content, count = re.subn(pattern, '', content, flags=re.DOTALL)
        if count > 0:
            content = new_content
            removed_count += count

    # Remove <plurals name="KEY">...</plurals>
    for key in plural_keys:
        pattern = r'[ \t]*<plurals name="' + re.escape(key) + r'"[^>]*>.*?</plurals>\n?'
        new_content, count = re.subn(pattern, '', content, flags=re.DOTALL)
        if count > 0:
            content = new_content
            removed_count += count

    # Collapse 3+ consecutive blank lines into at most 1
    content = re.sub(r'\n{3,}', '\n\n', content)

    if content != original:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"  {filepath}: removed {removed_count} entries")
    else:
        print(f"  {filepath}: no changes")

    return removed_count


base = "app/src/main/res"
lang_files = [
    f"{base}/values/strings.xml",
    f"{base}/values-de/strings.xml",
    f"{base}/values-uk/strings.xml",
    f"{base}/values-tr/strings.xml",
    f"{base}/values-zh-rCN/strings.xml",
]

total = 0
for f in lang_files:
    if os.path.exists(f):
        total += remove_unused_from_file(f, UNUSED_STRINGS, UNUSED_PLURALS)
    else:
        print(f"  MISSING: {f}")

print(f"\nTotal removed: {total} entries")
