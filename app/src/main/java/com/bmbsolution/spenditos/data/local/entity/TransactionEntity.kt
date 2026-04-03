package com.bmbsolution.spenditos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.bmbsolution.spenditos.data.local.converter.DateConverter

@Entity(tableName = "transactions")
@TypeConverters(DateConverter::class)
data class TransactionEntity(
    @PrimaryKey
    val id: String,
    val description: String,
    val amount: Double,
    val type: String, // "expense" | "income"
    val categoryId: String?,
    val categoryName: String?,
    val date: String, // ISO 8601
    val groupId: String?,
    val createdBy: String,
    val isRecurring: Boolean,
    val recurringTemplateId: String?,
    val isSynced: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
