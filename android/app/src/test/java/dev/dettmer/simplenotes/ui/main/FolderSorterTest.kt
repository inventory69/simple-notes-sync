package dev.dettmer.simplenotes.ui.main

import dev.dettmer.simplenotes.models.Folder
import dev.dettmer.simplenotes.models.SortDirection
import dev.dettmer.simplenotes.models.SortOption
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderSorterTest {
    private val folders = listOf(
        Folder("banana", color = "#FBBC04"),
        Folder("Apple", color = null),
        Folder("cherry", color = "#F28B82")
    )

    @Test
    fun `sorts by name case-insensitively, ascending`() {
        val result = sortFolders(folders, SortOption.TITLE, SortDirection.ASCENDING)
        assertEquals(listOf("Apple", "banana", "cherry"), result.map { it.name })
    }

    @Test
    fun `direction flips the order`() {
        val result = sortFolders(folders, SortOption.TITLE, SortDirection.DESCENDING)
        assertEquals(listOf("cherry", "banana", "Apple"), result.map { it.name })
    }

    @Test
    fun `time options fall back to the name order`() {
        val byUpdated = sortFolders(folders, SortOption.UPDATED_AT, SortDirection.ASCENDING)
        val byTitle = sortFolders(folders, SortOption.TITLE, SortDirection.ASCENDING)
        assertEquals(byTitle.map { it.name }, byUpdated.map { it.name })
    }

    @Test
    fun `color sorts by palette order, colorless last`() {
        val result = sortFolders(folders, SortOption.COLOR, SortDirection.ASCENDING)
        assertEquals(listOf("cherry", "banana", "Apple"), result.map { it.name })
    }
}
