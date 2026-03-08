package com.solanki.pocketledger.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val personName: String,
    val amount: Double,
    val type: String,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String? = null
)


