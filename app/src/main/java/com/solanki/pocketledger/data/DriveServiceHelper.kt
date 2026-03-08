package com.solanki.pocketledger.data

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections

class DriveServiceHelper(private val mDriveService: Drive) {

    suspend fun createFile(filePath: String): String? = withContext(Dispatchers.IO) {
        val metadata = com.google.api.services.drive.model.File()
            .setName("PocketLedger_Backup.db")
            .setParents(Collections.singletonList("appDataFolder"))

        val file = File(filePath)
        val content = FileContent("application/x-sqlite3", file)

        try {
            // Check if file already exists
            val result = mDriveService.files().list()
                .setSpaces("appDataFolder")
                .execute()
            
            val existingFile = result.files.find { it.name == "PocketLedger_Backup.db" }
            
            if (existingFile != null) {
                // Update existing file
                mDriveService.files().update(existingFile.id, null, content).execute()
                existingFile.id
            } else {
                // Create new file
                val googleFile = mDriveService.files().create(metadata, content).execute()
                googleFile.id
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun downloadFile(targetFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = mDriveService.files().list()
                .setSpaces("appDataFolder")
                .execute()

            val driveFile = result.files.find { it.name == "PocketLedger_Backup.db" }
                ?: return@withContext false

            targetFile.outputStream().use { outputStream ->
                mDriveService.files().get(driveFile.id).executeMediaAndDownloadTo(outputStream)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    companion object {
        fun getDriveService(context: Context, account: GoogleSignInAccount): Drive {
            val credential = GoogleAccountCredential.usingOAuth2(
                context, Collections.singletonList(DriveScopes.DRIVE_APPDATA)
            )
            credential.selectedAccount = account.account

            return Drive.Builder(
                AndroidHttp.newCompatibleTransport(),
                GsonFactory(),
                credential
            )
                .setApplicationName("PocketLedger")
                .build()
        }
    }
}
