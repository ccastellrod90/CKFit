package com.ckfit.app.data.db

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate

/** LocalDate como epochDay e Instant como epoch millis. Los enums los guarda Room por nombre. */
class Converters {
    @TypeConverter
    fun localDateToLong(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun longToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
}
