package com.solanki.pocketledger.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.File

class BackupManager(private val context: Context) {

    private val dbName = "pocketledger_db"

    suspend fun exportDatabase(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            // Force checkpoint to ensure main DB file is up-to-date
            val db = DatabaseProvider.getDatabase(context)
            db.query("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }

            val dbFile = context.getDatabasePath(dbName)
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                FileInputStream(dbFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun importDatabase(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. Completely reset and close the database instance
            DatabaseProvider.resetDatabase()

            val dbFile = context.getDatabasePath(dbName)
            val dbWalFile = File(dbFile.path + "-wal")
            val dbShmFile = File(dbFile.path + "-shm")
            
            // 2. Delete existing DB files to prevent conflicts
            if (dbFile.exists()) dbFile.delete()
            if (dbWalFile.exists()) dbWalFile.delete()
            if (dbShmFile.exists()) dbShmFile.delete()

            // 3. Copy the backup file to the database location
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(dbFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            // Note: Room will handle any necessary migrations or 
            // destructive resets when getDatabase() is next called.
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
