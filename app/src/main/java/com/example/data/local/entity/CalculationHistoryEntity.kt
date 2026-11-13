package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "calculation_history",
    indices = [Index(value = ["createdAtMillis"])]
)
data class CalculationHistoryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val expression: String,
    val result: String,
    val calculatorMode: String,
    val angleUnit: String,
    val precision: Int,
    val isApproximated: Boolean,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)
