package com.bmbsolution.spenditos.data.local.converter

import androidx.room.TypeConverter
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DateConverter {
    @TypeConverter
    fun fromTimestamp(value: Long?): String? {
        return value?.let { java.time.Instant.ofEpochMilli(it).toString() }
    }

    @TypeConverter
    fun toTimestamp(value: String?): Long? {
        return value?.let { java.time.Instant.parse(it).toEpochMilli() }
    }
}

class StringListConverter {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return json.encodeToString(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return json.decodeFromString(value)
    }
}
