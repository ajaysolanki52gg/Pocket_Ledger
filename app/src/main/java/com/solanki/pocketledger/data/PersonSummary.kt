package com.solanki.pocketledger.data

data class PersonSummary(
    val name: String,
    val totalReceived: Double,
    val totalSent: Double,
    val netBalance: Double,
    val lastTransactionTimestamp: Long,
    val isArchived: Boolean,
    val displayOrder: Int = 0
)
