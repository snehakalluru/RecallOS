package com.recallos.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Fts4(contentEntity = MemoryItem::class)
@Entity(tableName = "memory_items_fts")
data class MemoryItemFts(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long,
    val rawOcrText: String,
    val caption: String?,
)
