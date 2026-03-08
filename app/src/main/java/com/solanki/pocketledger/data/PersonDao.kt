package com.solanki.pocketledger.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {

    @Query("SELECT * FROM people ORDER BY displayOrder ASC")
    fun getAllPeople(): Flow<List<Person>>

    @Query("SELECT * FROM people WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): Person?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(person: Person)

    @Update
    suspend fun update(person: Person)

    @Query("UPDATE people SET isArchived = :archived WHERE name = :name")
    suspend fun setArchived(name: String, archived: Boolean)

    @Query("UPDATE people SET name = :newName WHERE name = :oldName")
    suspend fun updateName(oldName: String, newName: String)

    @Query("DELETE FROM people WHERE name = :name")
    suspend fun deleteByName(name: String)
    
    @Update
    suspend fun updateAll(people: List<Person>)
}
