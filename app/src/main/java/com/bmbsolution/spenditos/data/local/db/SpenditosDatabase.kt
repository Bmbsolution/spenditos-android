package com.bmbsolution.spenditos.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.bmbsolution.spenditos.data.local.converter.DateConverter
import com.bmbsolution.spenditos.data.local.converter.StringListConverter
import com.bmbsolution.spenditos.data.local.dao.CategoryDao
import com.bmbsolution.spenditos.data.local.dao.TransactionDao
import com.bmbsolution.spenditos.data.local.entity.CategoryEntity
import com.bmbsolution.spenditos.data.local.entity.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(DateConverter::class, StringListConverter::class)
abstract class SpenditosDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
}
