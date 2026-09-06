package com.recallos.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MemoryItem::class,
        MemoryItemFts::class,
    ],
    version = 2,
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
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE memory_items ADD COLUMN visualCaption TEXT")
                db.execSQL("UPDATE memory_items SET visualCaption = '' WHERE rawOcrText != 'PROCESSING'")
            }
        }
    }
}