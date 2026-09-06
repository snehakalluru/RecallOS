package com.recallos.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memory_items")
data class MemoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceUri: String,
    val sourceType: SourceType = SourceType.SCREENSHOT,
    val rawOcrText: String,
    val caption: String? = null,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val embeddingVector: ByteArray? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val tags: String = "",
)

enum class SourceType {
    SCREENSHOT,
}
