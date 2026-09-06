package com.recallos.search

import androidx.sqlite.db.SimpleSQLiteQuery
import com.recallos.data.MemoryItem
import com.recallos.data.MemoryItemDao
import kotlinx.coroutines.flow.Flow

class MemorySearchRepository(
    private val dao: MemoryItemDao,
) {
    fun keywordSearch(query: String): Flow<List<MemoryItem>> =
        dao.searchFts4(query)

    fun keywordSearchFts5(query: String): Flow<List<MemoryItem>> =
        dao.searchFts5(
            SimpleSQLiteQuery(
                """
                SELECT memory_items.*
                FROM memory_items
                JOIN memory_items_fts5 ON memory_items.id = memory_items_fts5.rowid
                WHERE memory_items_fts5 MATCH ?
                ORDER BY memory_items.createdAt DESC
                """.trimIndent(),
                arrayOf(query),
            )
        )

    suspend fun embed(text: String): ByteArray {
        return text.encodeToByteArray()
    }
}
