package dev.dettmer.simplenotes.ui.main.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.ChecklistItem
import dev.dettmer.simplenotes.models.ChecklistSortOption
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.utils.toEnumOrDefault
import kotlin.math.roundToInt

/** Kleiner fixer Abstand zwischen Textende und dem rechtsbündigen Trailing-Icon. */
private val TRAILING_ICON_GAP = 4.dp

/** Icon für den Sync-Status einer Notiz — gemeinsam genutzt von allen Card-Varianten. */
@Composable
fun syncStatusIcon(status: SyncStatus): ImageVector = when (status) {
    SyncStatus.SYNCED -> Icons.Outlined.CloudDone
    SyncStatus.PENDING -> Icons.Outlined.CloudSync
    SyncStatus.CONFLICT -> Icons.Default.Warning
    SyncStatus.LOCAL_ONLY -> Icons.Outlined.CloudOff
    SyncStatus.DELETED_ON_SERVER -> Icons.Outlined.CloudOff // 🆕 v1.8.0
}

/** Tint für den Sync-Status einer Notiz — gemeinsam genutzt von allen Card-Varianten. */
@Composable
fun syncStatusTint(status: SyncStatus): Color = when (status) {
    SyncStatus.SYNCED -> MaterialTheme.colorScheme.primary
    SyncStatus.CONFLICT -> MaterialTheme.colorScheme.error
    SyncStatus.DELETED_ON_SERVER -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f) // 🆕 v1.8.0
    else -> MaterialTheme.colorScheme.outline
}

/** Lokalisierte Beschreibung für den Sync-Status — nur für Kontexte mit echtem A11y-Mehrwert. */
@Composable
fun syncStatusDescription(status: SyncStatus): String = when (status) {
    SyncStatus.SYNCED -> stringResource(R.string.sync_status_synced)
    SyncStatus.PENDING -> stringResource(R.string.sync_status_pending)
    SyncStatus.CONFLICT -> stringResource(R.string.sync_status_conflict)
    SyncStatus.LOCAL_ONLY -> stringResource(R.string.sync_status_local_only)
    SyncStatus.DELETED_ON_SERVER -> stringResource(R.string.sync_status_deleted_on_server) // 🆕 v1.8.0
}

/**
 * 🆕 Issue #100: Text mit einem Icon (Sync-Status), das IMMER am rechten Rand sitzt — der Text
 * darf bis kurz davor laufen und endet bei Bedarf mit "…".
 *
 * Reserviert dafür konstant `iconSize + Abstand` am Zeilenende (Modifier.padding), sodass
 * Androids eingebaute Ellipsis-Logik zuverlässig genau dort abschneidet — kein Icon-Anhängen
 * an den Text nötig (das ließ das Icon bei randvoller letzter Zeile früher komplett
 * verschwinden, s. Issue #100). Das Icon selbst ist ein unabhängiges Overlay, rechtsbündig via
 * [Alignment.TopEnd] und vertikal per `onTextLayout` exakt auf Höhe der letzten sichtbaren
 * Zeile ausgerichtet — passt sich dadurch automatisch an kurze wie lange Vorschautexte an.
 */
@Composable
fun TrailingIconText(
    text: AnnotatedString,
    style: TextStyle,
    color: Color,
    maxLines: Int,
    modifier: Modifier = Modifier,
    iconSize: Dp = 0.dp,
    icon: (@Composable () -> Unit)? = null
) {
    if (icon == null) {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
        )
        return
    }

    val density = LocalDensity.current
    var lastLineTopPx by remember(text) { mutableFloatStateOf(0f) }
    var lastLineHeightPx by remember(text) { mutableFloatStateOf(0f) }
    // 🔧 Bis der erste onTextLayout-Callback feuert, ist die Ziel-Zeile unbekannt — Icon so lange
    // ausblenden statt es (falsch positioniert) bei y=0 aufblitzen zu lassen.
    var hasMeasured by remember(text) { mutableStateOf(false) }

    Box(modifier = modifier) {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = iconSize + TRAILING_ICON_GAP),
            onTextLayout = { layout ->
                val lastLine = layout.lineCount - 1
                lastLineTopPx = layout.getLineTop(lastLine)
                lastLineHeightPx = layout.getLineBottom(lastLine) - lastLineTopPx
                hasMeasured = true
            }
        )
        if (hasMeasured) {
            val iconSizePx = with(density) { iconSize.toPx() }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        IntOffset(x = 0, y = (lastLineTopPx + (lastLineHeightPx - iconSizePx) / 2f).roundToInt())
                    }
            ) {
                icon()
            }
        }
    }
}

/**
 * 🆕 v1.8.1 (IMPL_03): Helper-Funktionen für die Checklisten-Vorschau in Main Activity.
 *
 * Stellt sicher, dass die Sortierung aus dem Editor konsistent
 * in allen Preview-Components (NoteCard, NoteCardCompact, NoteCardGrid)
 * angezeigt wird.
 */

/**
 * Sortiert Checklist-Items für die Vorschau basierend auf der
 * gespeicherten Sortier-Option.
 */
fun sortChecklistItemsForPreview(items: List<ChecklistItem>, sortOptionName: String?): List<ChecklistItem> {
    val sortOption = sortOptionName.toEnumOrDefault(ChecklistSortOption.MANUAL)

    return when (sortOption) {
        ChecklistSortOption.MANUAL,
        ChecklistSortOption.UNCHECKED_FIRST ->
            items.sortedBy { it.isChecked }

        ChecklistSortOption.CHECKED_FIRST ->
            items.sortedByDescending { it.isChecked }

        ChecklistSortOption.ALPHABETICAL_ASC ->
            items.sortedBy { it.text.lowercase() }

        ChecklistSortOption.ALPHABETICAL_DESC ->
            items.sortedByDescending { it.text.lowercase() }

        ChecklistSortOption.CREATION_DATE -> {
            // 🆕 v1.11.0: Unchecked first, then checked — both sorted by creation timestamp (ascending)
            val unchecked = items.filter { !it.isChecked }.sortedBy { it.createdAt }
            val checked = items.filter { it.isChecked }.sortedBy { it.createdAt }
            unchecked + checked
        }

        ChecklistSortOption.CREATION_DATE_DESC -> {
            // 🆕 v1.11.0: Unchecked first, then checked — both sorted by creation timestamp (descending)
            val unchecked = items.filter { !it.isChecked }.sortedByDescending { it.createdAt }
            val checked = items.filter { it.isChecked }.sortedByDescending { it.createdAt }
            unchecked + checked
        }
    }
}

/**
 * Generiert den Vorschau-Text für eine Checkliste mit korrekter
 * Sortierung und passenden Emojis.
 *
 * @param items Die Checklisten-Items
 * @param sortOptionName Der Name der ChecklistSortOption (oder null für MANUAL)
 * @return Formatierter Preview-String mit Emojis und Zeilenumbrüchen
 *
 * 🆕 v1.8.1 (IMPL_06): Emoji-Änderung (☑️ statt ✅ für checked items)
 */
fun generateChecklistPreview(items: List<ChecklistItem>, sortOptionName: String?): String {
    val sorted = sortChecklistItemsForPreview(items, sortOptionName)
    return sorted.joinToString("\n") { item ->
        val prefix = if (item.isChecked) "☑️" else "☐"
        "$prefix ${item.text}"
    }
}

@Composable
fun ChecklistItemsPreview(
    items: List<ChecklistItem>,
    sortOptionName: String?,
    maxItems: Int,
    style: TextStyle,
    modifier: Modifier = Modifier,
    itemMaxLines: Int = 1, // 🆕 v2.11.0: erlaubt mehrzeiliges Wrapping langer Items (Preset-gesteuert)
    // 🆕 Issue #100: hängt (falls gesetzt) ein Inline-Icon ans Ende der TATSÄCHLICH letzten
    // gerenderten Zeile an ("+N mehr" falls vorhanden, sonst letztes sichtbares Item).
    trailingIcon: (@Composable () -> Unit)? = null,
    trailingIconSize: Dp = 0.dp
) {
    val sorted = remember(items, sortOptionName) { sortChecklistItemsForPreview(items, sortOptionName) }
    val remaining = (sorted.size - maxItems).coerceAtLeast(0)
    // If only 1 item would be hidden, showing "+1 more" wastes a line vs. just showing the item
    val effectiveMax = if (remaining == 1) maxItems + 1 else maxItems
    val visible = sorted.take(effectiveMax)
    val finalRemaining = (sorted.size - visible.size).coerceAtLeast(0)

    Column(modifier = modifier) {
        // 🆕 Issue #100 fix: eine leere Checkliste hat sonst keine Zeile, an die sich das
        // Sync-Icon hängen könnte — ohne Zeitstempel-Footer verschwindet der Status sonst komplett.
        if (visible.isEmpty() && finalRemaining == 0 && trailingIcon != null) {
            TrailingIconText(
                text = AnnotatedString(""),
                style = style,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                iconSize = trailingIconSize,
                icon = trailingIcon
            )
        }
        visible.forEachIndexed { index, item ->
            val prefix = if (item.isChecked) "☑️" else "☐"
            val isLastLine = index == visible.lastIndex && finalRemaining == 0
            TrailingIconText(
                text = AnnotatedString("$prefix ${item.text}"),
                style = style,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = itemMaxLines,
                iconSize = trailingIconSize,
                icon = if (isLastLine) trailingIcon else null
            )
        }
        if (finalRemaining > 0) {
            TrailingIconText(
                text = AnnotatedString(stringResource(R.string.checklist_items_more, finalRemaining)),
                style = style,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                iconSize = trailingIconSize,
                icon = trailingIcon
            )
        }
    }
}
