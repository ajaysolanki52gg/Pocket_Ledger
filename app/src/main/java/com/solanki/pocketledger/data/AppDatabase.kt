package com.solanki.pocketledger.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Transaction::class, Person::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun personDao(): PersonDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Check if columns exist before adding to prevent crashes if they were partially added
                val cursor = db.query("PRAGMA table_info(people)")
                var hasCreatedAt = false
                var hasDisplayOrder = false
                while (cursor.moveToNext()) {
                    val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                    if (name == "createdAt") hasCreatedAt = true
                    if (name == "displayOrder") hasDisplayOrder = true
                }
                cursor.close()

                if (!hasCreatedAt) {
                    db.execSQL("ALTER TABLE people ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                }
                if (!hasDisplayOrder) {
                    db.execSQL("ALTER TABLE people ADD COLUMN displayOrder INTEGER NOT NULL DEFAULT 0")
                }
            }
        }
    }
}
