package com.solanki.pocketledger.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "people")
data class Person(
    @PrimaryKey val name: String,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val displayOrder: Int = 0
)
