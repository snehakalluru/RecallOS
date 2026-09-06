package com.recallos.data

import androidx.room.TypeConverter

class SourceTypeConverters {
    @TypeConverter
    fun fromSourceType(sourceType: SourceType): String = sourceType.name

    @TypeConverter
    fun toSourceType(value: String): SourceType = SourceType.valueOf(value)
}
