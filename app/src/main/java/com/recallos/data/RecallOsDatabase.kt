package com.recallos.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MemoryItem::class,
        MemoryItemFts::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(SourceTypeConverters::class)
abstract class RecallOsDatabase : RoomDatabase() {
    abstract fun memoryItemDao(): MemoryItemDao

    companion object {
        @Volatile
        private var instance: RecallOsDatabase? = null

        fun getInstance(context: Context): RecallOsDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RecallOsDatabase::class.java,
                    "recallos.db",
                )
                    .addCallback(fts5Callback)
                    .build()
                    .also { instance = it }
            }

        private val fts5Callback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS memory_items_fts5
                    USING fts5(
                        rawOcrText,
                        caption,
                        content='memory_items',
                        content_rowid='id'
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS memory_items_fts5_ai
                    AFTER INSERT ON memory_items BEGIN
                        INSERT INTO memory_items_fts5(rowid, rawOcrText, caption)
                        VALUES (new.id, new.rawOcrText, new.caption);
                    END
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS memory_items_fts5_ad
                    AFTER DELETE ON memory_items BEGIN
                        INSERT INTO memory_items_fts5(memory_items_fts5, rowid, rawOcrText, caption)
                        VALUES ('delete', old.id, old.rawOcrText, old.caption);
                    END
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS memory_items_fts5_au
                    AFTER UPDATE ON memory_items BEGIN
                        INSERT INTO memory_items_fts5(memory_items_fts5, rowid, rawOcrText, caption)
                        VALUES ('delete', old.id, old.rawOcrText, old.caption);
                        INSERT INTO memory_items_fts5(rowid, rawOcrText, caption)
                        VALUES (new.id, new.rawOcrText, new.caption);
                    END
                    """.trimIndent()
                )
            }
        }
    }
}
