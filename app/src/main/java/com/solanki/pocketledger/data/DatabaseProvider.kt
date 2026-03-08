package com.solanki.pocketledger.data

import android.content.Context
import androidx.room.Room

object DatabaseProvider {

    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "pocketledger_db"
            )
                .addMigrations(AppDatabase.MIGRATION_3_4)
                .build()
            INSTANCE = instance
            instance
        }
    }

    /**
     * Closes and clears the database instance. 
     * Essential for import operations to ensure the new file is picked up.
     */
    fun resetDatabase() {
        synchronized(this) {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
