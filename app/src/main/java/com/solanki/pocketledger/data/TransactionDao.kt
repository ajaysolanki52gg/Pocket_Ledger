package com.solanki.pocketledger.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: Transaction)

    @Update
    suspend fun update(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE personName = :name ORDER BY timestamp DESC")
    fun getTransactionsForPerson(name: String): Flow<List<Transaction>>

    @Query("DELETE FROM transactions WHERE personName = :name")
    suspend fun deleteByPersonName(name: String)

    @Query("UPDATE transactions SET personName = :newName WHERE personName = :oldName")
    suspend fun updatePersonName(oldName: String, newName: String)

    @Query("DELETE FROM transactions")
    suspend fun clearAll()
}
