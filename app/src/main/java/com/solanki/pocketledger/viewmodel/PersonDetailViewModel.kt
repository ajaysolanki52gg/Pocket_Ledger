package com.solanki.pocketledger.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.solanki.pocketledger.data.DatabaseProvider
import com.solanki.pocketledger.data.Transaction
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PersonDetailViewModel(
    application: Application,
    private val personName: String
) : AndroidViewModel(application) {

    private val dao = DatabaseProvider
        .getDatabase(application)
        .transactionDao()

    val transactions = dao
        .getTransactionsForPerson(personName)
        .map { list ->
            list.sortedByDescending { it.timestamp }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    val totalReceived = transactions
        .map { list ->
            list.filter { it.type == "RECEIVED" }
                .sumOf { it.amount }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalSent = transactions
        .map { list ->
            list.filter { it.type == "SENT" }
                .sumOf { it.amount }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val netBalance = combine(totalReceived, totalSent) { received, sent ->
        received - sent
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    fun addTransaction(transaction: Transaction) {
        viewModelScope.launch {
            dao.insert(transaction)
        }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            dao.update(transaction)
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            dao.delete(transaction)
        }
    }

}
