package com.example.financetracker.data

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*

class DatabaseBackup(private val context: Context) {
    private val gson = Gson()

    suspend fun backupDatabase(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val database = AppDatabase.getDatabase(context)
            val transactions = database.transactionDao().getAllTransactionsSync()
            
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    val backupData = BackupData(
                        timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            .format(Date()),
                        transactions = transactions
                    )
                    writer.write(gson.toJson(backupData))
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun restoreDatabase(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val database = AppDatabase.getDatabase(context)
            val transactionDao = database.transactionDao()

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    val jsonString = reader.readText()
                    val type = object : TypeToken<BackupData>() {}.type
                    val backupData = gson.fromJson<BackupData>(jsonString, type)

                    // Clear existing data
                    transactionDao.deleteAllTransactions()

                    // Restore transactions
                    backupData.transactions.forEach { transaction ->
                        transactionDao.insertTransaction(transaction)
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    data class BackupData(
        val timestamp: String,
        val transactions: List<Transaction>
    )
} 