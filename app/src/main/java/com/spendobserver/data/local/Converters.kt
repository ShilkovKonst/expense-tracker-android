package com.spendobserver.data.local

import androidx.room.TypeConverter
import kotlinx.datetime.LocalDate

class Converters {
    @TypeConverter
    fun fromLocalDate(date: LocalDate): Long = date.toEpochDays()

    @TypeConverter
    fun toLocalDate(epochDays: Long): LocalDate = LocalDate.fromEpochDays(epochDays)
}
