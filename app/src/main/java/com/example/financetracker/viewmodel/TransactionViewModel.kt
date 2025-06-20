package com.example.financetracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.financetracker.data.AppDatabase
import com.example.financetracker.data.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TransactionViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val transactionDao = database.transactionDao()

    val allTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactions()

    fun addTransaction(title: String, amount: Double, category: String, type: String) {
        viewModelScope.launch {
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val transaction = Transaction(
                title = title,
                amount = amount,
                date = dateFormat.format(Date()),
                category = category,
                type = type
            )
            transactionDao.insertTransaction(transaction)
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            transactionDao.deleteTransaction(transaction)
        }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            transactionDao.updateTransaction(transaction)
        }
    }
} 