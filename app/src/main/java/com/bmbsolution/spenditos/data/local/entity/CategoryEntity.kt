package com.bmbsolution.spenditos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val icon: String?,
    val color: String?,
    val type: String?, // "expense" | "income"
    val isSystem: Boolean,
    val displayOrder: Int = 0,
    val isSynced: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)
