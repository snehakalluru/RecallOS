package com.recallos.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: MemoryItem): Long

    @Query("UPDATE memory_items SET rawOcrText = :rawOcrText, caption = :caption WHERE id = :id")
    suspend fun updateOcrResult(id: Long, rawOcrText: String, caption: String)

    @Query("UPDATE memory_items SET embeddingVector = :embeddingVector WHERE id = :id")
    suspend fun updateEmbedding(id: Long, embeddingVector: ByteArray)

    @Query("SELECT * FROM memory_items WHERE embeddingVector IS NOT NULL ORDER BY createdAt DESC")
    suspend fun findEmbeddedItems(): List<MemoryItem>

    @Query("SELECT * FROM memory_items WHERE rawOcrText = 'PROCESSING'")
    suspend fun findProcessingItems(): List<MemoryItem>

    @Query("SELECT * FROM memory_items ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MemoryItem>>

    @Query(
        """
        SELECT memory_items.*
        FROM memory_items
        JOIN memory_items_fts ON memory_items.id = memory_items_fts.rowid
        WHERE memory_items_fts MATCH :query
        ORDER BY memory_items.createdAt DESC
        """
    )
    fun searchFts4(query: String): Flow<List<MemoryItem>>

    @RawQuery(observedEntities = [MemoryItem::class])
    fun searchFts5(query: SupportSQLiteQuery): Flow<List<MemoryItem>>
}
