package dev.dettmer.simplenotes.models

/** Gemeinsame Sicht auf eine Checklisten-Zeile (Modell und Editor-State) für [ChecklistNesting]. */
interface ChecklistRow {
    val isChecked: Boolean
    val indentationLevel: Int
}

/**
 * Unterpunkte in Checklisten (Issues #64, #71): genau eine Ebene, das Modell bleibt eine flache Liste.
 *
 * Eine **Gruppe** ist ein Oberpunkt (Ebene 0) plus die direkt folgenden Zeilen mit Ebene 1. Es gibt
 * kein `parentId`: Zugehörigkeit ergibt sich allein aus der Position. Der **Abschnitt** einer Zeile
 * (offen oder erledigt, also über oder unter dem Trenner) ist der Zustand ihres Oberpunkts. Ein
 * abgehakter Unterpunkt bleibt so bei seinem offenen Oberpunkt, nur ganze Gruppen wandern.
 *
 * Invariante: Ein abgehakter Oberpunkt hat nur abgehakte Unterpunkte. [toggle] und [finishDrag] halten
 * sie beim Bearbeiten, [normalize] stellt sie für eingelesene Daten her (Text, Markdown, Import, Sync).
 */
object ChecklistNesting {
    const val MAX_LEVEL = 1

    /** Höchste gespeicherte Ebene (Keep-Import liefert bis zu 3). Mehr wird beim Lesen gekappt. */
    const val MAX_STORED_LEVEL = 3

    /** Einzug je Ebene in Markdown und Text (GFM-verschachtelte Task-Liste). */
    const val TEXT_INDENT = "  "

    /** Ebene aus führendem Leerraum einer Textzeile: ab zwei Leerzeichen (oder einem Tab) Unterpunkt. */
    fun levelOfLine(line: String): Int =
        if (line.takeWhile { it == ' ' || it == '\t' }.replace("\t", TEXT_INDENT).length >= 2) 1 else 0

    private val ChecklistRow.isChild get() = indentationLevel > 0

    /** Index des Oberpunkts der Gruppe, zu der [index] gehört. Eine führende Ebene-1-Zeile gilt als Oberpunkt. */
    fun groupStart(items: List<ChecklistRow>, index: Int): Int {
        var i = index
        while (i > 0 && items[i].isChild) i--
        return i
    }

    /** Alle Indizes der Gruppe von [index]. */
    fun groupRange(items: List<ChecklistRow>, index: Int): IntRange {
        val start = groupStart(items, index)
        var end = start + 1
        while (end < items.size && items[end].isChild) end++
        return start until end
    }

    /** true, wenn [index] im erledigten Abschnitt steht (sein Oberpunkt ist abgehakt). */
    fun inCheckedSection(items: List<ChecklistRow>, index: Int): Boolean = items[groupStart(items, index)].isChecked

    /** Zeilen in Gruppen mit offenem Oberpunkt. Ersetzt `count { !it.isChecked }` als Trenner-Position. */
    fun openRowCount(items: List<ChecklistRow>): Int {
        var parentChecked = false
        return items.withIndex().count { (i, row) ->
            if (i == 0 || !row.isChild) parentChecked = row.isChecked
            !parentChecked
        }
    }

    /** Zerlegt [items] in Gruppen (Oberpunkt zuerst). */
    fun <T : ChecklistRow> groups(items: List<T>): List<List<T>> {
        val result = mutableListOf<MutableList<T>>()
        items.forEach { row ->
            if (result.isEmpty() || !row.isChild) result += mutableListOf(row) else result.last() += row
        }
        return result
    }

    /**
     * Ebene auf 0..[MAX_LEVEL] kappen, die erste Zeile ist immer Oberpunkt (Keep-Import kennt bis zu 3
     * Ebenen). Unterpunkte eines abgehakten Oberpunkts werden abgehakt (Invariante, s. o.): sonst stünde
     * ein offener Eintrag unter dem Trenner, und erneutes Abhaken des Oberpunkts griffe nicht.
     */
    fun normalize(items: List<ChecklistItem>): List<ChecklistItem> {
        var parentChecked = false
        return items.mapIndexed { i, item ->
            val level = if (i == 0) 0 else item.indentationLevel.coerceIn(0, MAX_LEVEL)
            if (level == 0) parentChecked = item.isChecked
            val checked = item.isChecked || (level > 0 && parentChecked)
            if (level == item.indentationLevel && checked == item.isChecked) {
                item
            } else {
                item.copy(indentationLevel = level, isChecked = checked)
            }
        }
    }

    /**
     * Abhaken mit Gruppenregeln:
     * - Oberpunkt abhaken hakt alle Unterpunkte mit ab, aufhaken ändert nur ihn.
     * - Unterpunkt aufhaken unter abgehaktem Oberpunkt hakt auch den Oberpunkt auf.
     * - Sonst ändert sich nur die Zeile selbst.
     * Sortiert nicht, das macht der Aufrufer über [ChecklistSorter].
     */
    fun toggle(items: List<ChecklistItem>, id: String, checked: Boolean): List<ChecklistItem> {
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return items
        val group = groupRange(items, index)
        val isParent = index == group.first
        return items.mapIndexed { i, item ->
            when {
                i == index -> item.copy(isChecked = checked)
                isParent && checked && i in group -> item.copy(isChecked = true)
                !isParent && !checked && i == group.first -> item.copy(isChecked = false)
                else -> item
            }
        }
    }

    /**
     * Ein Tausch beim Ziehen. Wechselt die Zeile den Abschnitt, übernimmt sie dessen Zustand
     * (wie bisher beim Überqueren des Trenners). Ein Unterpunkt, der dabei an einen Oberpunkt des
     * anderen Abschnitts geriete (z. B. direkt unter den Trenner oder ganz nach oben), wird selbst zum
     * Oberpunkt und übernimmt den Zustand des Abschnitts, in dem er jetzt steht.
     */
    fun move(items: List<ChecklistItem>, fromIndex: Int, toIndex: Int): List<ChecklistItem> {
        if (fromIndex !in items.indices || toIndex !in items.indices) return items
        val fromChecked = inCheckedSection(items, fromIndex)
        val toChecked = inCheckedSection(items, toIndex)
        val list = items.toMutableList()
        val item = list.removeAt(fromIndex)
        list.add(toIndex, if (fromChecked != toChecked) item.copy(isChecked = toChecked) else item)
        if (list[toIndex].isChild && inCheckedSection(list, toIndex) != toChecked) {
            list[toIndex] = list[toIndex].copy(indentationLevel = 0, isChecked = toChecked)
        }
        return normalize(list)
    }

    /**
     * Drag-Ende. [formerChildIds] sind die Unterpunkte, die der gezogene Oberpunkt beim Start hatte;
     * sie bleiben während des Ziehens stehen und rücken jetzt direkt hinter ihn. Landet die Zeile mitten
     * in einer Gruppe (Nachfolger hat Ebene 1), wird sie Unterpunkt. Ist der neue Oberpunkt abgehakt,
     * werden alle Zeilen der Gruppe abgehakt.
     */
    fun finishDrag(items: List<ChecklistItem>, draggedId: String, formerChildIds: List<String>): List<ChecklistItem> {
        val children = formerChildIds.mapNotNull { id -> items.find { it.id == id } }
        val list = items.filterNot { it.id in formerChildIds }.toMutableList()
        val at = list.indexOfFirst { it.id == draggedId }
        if (at < 0) return normalize(items)
        val intoGroup = list.getOrNull(at + 1)?.isChild == true
        val level = when {
            intoGroup -> 1
            children.isNotEmpty() -> 0
            else -> list[at].indentationLevel
        }
        list[at] = list[at].copy(indentationLevel = level)
        list.addAll(at + 1, children.map { it.copy(indentationLevel = 1) })
        val normalized = normalize(list)
        val group = groupRange(normalized, normalized.indexOfFirst { it.id == draggedId })
        if (!normalized[group.first].isChecked) return normalized
        return normalized.mapIndexed { i, item -> if (i in group && !item.isChecked) item.copy(isChecked = true) else item }
    }

    /** Einrücken: nur wenn darüber eine Zeile desselben Abschnitts steht (nicht die erste Zeile einer Sektion). */
    fun canIndent(items: List<ChecklistRow>, index: Int): Boolean =
        index > 0 &&
            items[index].indentationLevel < MAX_LEVEL &&
            inCheckedSection(items, index - 1) == items[index].isChecked

    /** Einrücken: die Zeile wird Unterpunkt der Gruppe darüber, eigene Unterpunkte gehen mit (eine Ebene). */
    fun indent(items: List<ChecklistItem>, id: String): List<ChecklistItem> {
        val index = items.indexOfFirst { it.id == id }
        if (index < 0 || !canIndent(items, index)) return items
        return items.mapIndexed { i, item -> if (i == index) item.copy(indentationLevel = 1) else item }
    }

    /**
     * Ausrücken: die Zeile wird Oberpunkt und rückt ans Ende ihrer bisherigen Gruppe, damit die
     * folgenden Geschwister bei ihrem Oberpunkt bleiben. Ein abgehakter Unterpunkt wird so zu einem
     * abgehakten Oberpunkt; der Aufrufer sortiert ihn dann unter den Trenner.
     */
    fun outdent(items: List<ChecklistItem>, id: String): List<ChecklistItem> {
        val index = items.indexOfFirst { it.id == id }
        if (index < 0 || !items[index].isChild) return items
        val groupEnd = groupRange(items, index).last
        val list = items.toMutableList()
        val row = list.removeAt(index).copy(indentationLevel = 0)
        list.add(groupEnd, row)
        return list
    }

    /** Ebene einer neuen Zeile hinter [index]: erbt die Folgezeile, wenn die Unterpunkt ist, sonst die aktuelle Zeile. */
    fun levelForNewRowAfter(items: List<ChecklistRow>, index: Int): Int =
        if (items.getOrNull(index + 1)?.isChild == true) 1 else items[index].indentationLevel

    /**
     * Markdown-Import: Liegt die Datei flach vor (der Spiegel schreibt ohne Einzug, siehe
     * `Note.toMarkdown`), übernimmt jede Zeile die Ebene der lokalen Zeile mit gleichem Text. Eine externe
     * Bearbeitung verflacht die Struktur so nicht. Enthält die Datei Einzug, gilt sie wie geschrieben.
     */
    fun withLevelsFrom(md: List<ChecklistItem>?, local: List<ChecklistItem>?): List<ChecklistItem>? {
        if (md == null || local.isNullOrEmpty() || md.any { it.isChild }) return md
        val levelsByText = local.sortedBy { it.order }
            .groupBy({ it.text }, { it.indentationLevel })
            .mapValues { (_, levels) -> ArrayDeque(levels) }
        return normalize(
            md.map { item ->
                levelsByText[item.text]?.removeFirstOrNull()?.let { item.copy(indentationLevel = it) } ?: item
            }
        )
    }

    /** Leere Zeilen entfernen wie [delete] (Speichern verwirft sie): Unterpunkte eines leeren Oberpunkts werden Oberpunkte. */
    fun withoutBlank(items: List<ChecklistItem>): List<ChecklistItem> =
        items.filter { it.text.isBlank() }.fold(items) { acc, blank -> delete(acc, blank.id) }

    /** Unterpunkte eines gelöschten Oberpunkts werden Oberpunkte. */
    fun delete(items: List<ChecklistItem>, id: String): List<ChecklistItem> {
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return items
        val orphans = if (items[index].isChild) IntRange.EMPTY else groupRange(items, index)
        return normalize(
            items.mapIndexedNotNull { i, item ->
                when {
                    i == index -> null
                    i in orphans -> item.copy(indentationLevel = 0)
                    else -> item
                }
            }
        )
    }
}
