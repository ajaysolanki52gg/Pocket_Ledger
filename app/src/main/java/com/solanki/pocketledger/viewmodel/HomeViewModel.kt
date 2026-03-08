package com.solanki.pocketledger.viewmodel

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.solanki.pocketledger.data.DatabaseProvider
import com.solanki.pocketledger.data.Transaction
import com.solanki.pocketledger.data.Person
import com.solanki.pocketledger.data.BackupManager
import com.solanki.pocketledger.data.DriveServiceHelper
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.launch
import com.solanki.pocketledger.data.PersonSummary
import com.solanki.pocketledger.ui.SortType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import java.io.File
import kotlin.math.abs

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val db = DatabaseProvider.getDatabase(application)
    private val dao = db.transactionDao()
    private val personDao = db.personDao()
    private val backupManager = BackupManager(application)
    private var driveServiceHelper: DriveServiceHelper? = null

    val transactions = dao
        .getAllTransactions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val sortType = MutableStateFlow(SortType.RECENT)

    fun setSortType(type: SortType) {
        sortType.value = type
    }

    val people = combine(
        transactions,
        personDao.getAllPeople(),
        sortType
    ) { transactionList, personList, sort ->

        val allNames = (transactionList.map { it.personName } + personList.map { it.name }).toSet()

        val grouped = allNames.map { name ->
            val personTransactions = transactionList.filter { it.personName == name }
            val personEntity = personList.find { it.name == name }

            val received = personTransactions
                .filter { it.type == "RECEIVED" }
                .sumOf { it.amount }

            val sent = personTransactions
                .filter { it.type == "SENT" }
                .sumOf { it.amount }

            val lastTransactionTimestamp = personTransactions
                .maxOfOrNull { it.timestamp } ?: 0L
            
            // For new people with no transactions, use their creation time to show them at top in 'RECENT'
            val effectiveTimestamp = if (lastTransactionTimestamp == 0L) {
                personEntity?.createdAt ?: System.currentTimeMillis()
            } else {
                lastTransactionTimestamp
            }

            PersonSummary(
                name = name,
                totalReceived = received,
                totalSent = sent,
                netBalance = received - sent,
                lastTransactionTimestamp = effectiveTimestamp,
                isArchived = personEntity?.isArchived ?: false,
                displayOrder = personEntity?.displayOrder ?: 0
            )
        }

        when (sort) {
            SortType.RECENT ->
                grouped.sortedByDescending { it.lastTransactionTimestamp }

            SortType.BALANCE_HIGH ->
                grouped.sortedByDescending { abs(it.netBalance) }

            SortType.BALANCE_LOW ->
                grouped.sortedBy { abs(it.netBalance) }

            SortType.NAME ->
                grouped.sortedBy { it.name.lowercase() }
            
            SortType.MANUAL ->
                grouped.sortedBy { it.displayOrder }
        }
    }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )


    // 1. Filter global totals to only include non-archived people
    val totalReceived = people
        .map { list ->
            list.filter { !it.isArchived }
                .sumOf { it.totalReceived }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalSent = people
        .map { list ->
            list.filter { !it.isArchived }
                .sumOf { it.totalSent }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val netBalance = combine(totalReceived, totalSent) { received, sent ->
        received - sent
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    fun addPerson(name: String) {
        viewModelScope.launch {
            // New person gets lowest displayOrder to appear at top in Manual sort
            val currentPeople = personDao.getAllPeople().first()
            val minOrder = currentPeople.minOfOrNull { it.displayOrder } ?: 0
            personDao.insert(Person(name = name, displayOrder = minOrder - 1))
        }
    }

    fun editPersonName(oldName: String, newName: String) {
        viewModelScope.launch {
            personDao.updateName(oldName, newName)
            dao.updatePersonName(oldName, newName)
        }
    }

    fun archivePerson(name: String) {
        viewModelScope.launch {
            val person = personDao.getByName(name)
            if (person != null) {
                personDao.setArchived(name, true)
            } else {
                personDao.insert(Person(name = name, isArchived = true))
            }
        }
    }

    fun restorePerson(name: String) {
        viewModelScope.launch {
            val person = personDao.getByName(name)
            if (person != null) {
                personDao.setArchived(name, false)
            } else {
                personDao.insert(Person(name = name, isArchived = false))
            }
        }
    }

    fun deletePerson(name: String) {
        viewModelScope.launch {
            personDao.deleteByName(name)
            dao.deleteByPersonName(name)
        }
    }
    
    fun updateDisplayOrder(reorderedNames: List<String>) {
        viewModelScope.launch {
            val currentPeople = personDao.getAllPeople().first()
            val updatedPeople = currentPeople.map { person ->
                val newIndex = reorderedNames.indexOf(person.name)
                if (newIndex != -1) person.copy(displayOrder = newIndex) else person
            }
            personDao.updateAll(updatedPeople)
            setSortType(SortType.MANUAL)
        }
    }

    fun exportDatabase(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = backupManager.exportDatabase(uri)
            onResult(result)
        }
    }

    fun importDatabase(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = backupManager.importDatabase(uri)
            onResult(result)
        }
    }

    fun initDriveHelper(account: GoogleSignInAccount) {
        driveServiceHelper = DriveServiceHelper(
            DriveServiceHelper.getDriveService(getApplication(), account)
        )
    }

    fun syncToDrive(onResult: (Boolean) -> Unit) {
        val helper = driveServiceHelper ?: return onResult(false)
        viewModelScope.launch {
            val dbFile = getApplication<Application>().getDatabasePath("pocketledger_db")
            val result = helper.createFile(dbFile.absolutePath)
            onResult(result != null)
        }
    }

    fun restoreFromDrive(onResult: (Boolean) -> Unit) {
        val helper = driveServiceHelper ?: return onResult(false)
        viewModelScope.launch {
            val tempFile = File(getApplication<Application>().cacheDir, "temp_restore.db")
            val downloaded = helper.downloadFile(tempFile)
            if (downloaded) {
                val imported = backupManager.importDatabase(Uri.fromFile(tempFile))
                tempFile.delete()
                onResult(imported)
            } else {
                onResult(false)
            }
        }
    }

}
