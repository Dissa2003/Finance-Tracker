package com.example.financetracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.financetracker.data.AppDatabase
import com.example.financetracker.data.DatabaseBackup
import com.example.financetracker.data.Transaction
import com.example.financetracker.viewmodel.TransactionViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var transactionAdapter: TransactionAdapter
    private lateinit var budgetEditText: EditText
    private var monthlyBudget: Double = 0.0
    private var currentExpenses: Double = 0.0
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var databaseBackup: DatabaseBackup

    private val viewModel: TransactionViewModel by viewModels()

    private val backupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            lifecycleScope.launch {
                val success = databaseBackup.backupDatabase(uri)
                Toast.makeText(
                    this@MainActivity,
                    if (success) "Backup successful" else "Backup failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private val restoreLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            lifecycleScope.launch {
                val success = databaseBackup.restoreDatabase(uri)
                Toast.makeText(
                    this@MainActivity,
                    if (success) "Restore successful" else "Restore failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        databaseBackup = DatabaseBackup(this)

        // Handle window insets for proper padding
        val rootView = findViewById<LinearLayout>(R.id.rootLayout)
        if (rootView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.updatePadding(
                    left = systemBars.left,
                    top = systemBars.top,
                    right = systemBars.right,
                    bottom = systemBars.bottom
                )
                insets
            }
        } else {
            // Log an error or handle the case where rootView is null
            Toast.makeText(this, "Error: Root layout not found", Toast.LENGTH_SHORT).show()
        }

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        // Initialize SharedPreferences (only for user session and budget)
        sharedPreferences = getSharedPreferences("FinancePrefs", Context.MODE_PRIVATE)

        // Check if user is logged in
        val storedEmail = sharedPreferences.getString("user_email", null)
        if (storedEmail == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Initialize UI Components
        recyclerView = findViewById(R.id.recyclerView)
        budgetEditText = findViewById(R.id.budgetEditText)
        val addButton: Button = findViewById(R.id.addButton)
        val backupButton: Button = findViewById(R.id.backupButton)
        val restoreButton: Button = findViewById(R.id.restoreButton)
        val categorySpinner: Spinner = findViewById(R.id.categorySpinner)
        val logoutButton: Button = findViewById(R.id.logoutButton)

        // Setup RecyclerView
        transactionAdapter = TransactionAdapter(mutableListOf()) { transaction ->
            deleteTransaction(transaction)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = transactionAdapter

        // Setup Category Spinner
        val categories = arrayOf("Food", "Transport", "Bills", "Entertainment", "Others")
        categorySpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)

        // Load Budget from SharedPreferences
        monthlyBudget = sharedPreferences.getFloat("monthlyBudget", 0f).toDouble()
        budgetEditText.setText(monthlyBudget.toString())

        // Observe transactions
        lifecycleScope.launch {
            viewModel.allTransactions.collectLatest { transactions ->
                transactionAdapter.updateTransactions(transactions)
                updateSummary(transactions)
            }
        }

        // Add Transaction
        addButton.setOnClickListener {
            addTransaction()
        }

        // Save Budget
        findViewById<Button>(R.id.saveBudgetButton).setOnClickListener {
            saveBudget()
        }

        // Backup and Restore
        backupButton.setOnClickListener { 
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            backupLauncher.launch("finance_tracker_backup_$timestamp.json")
        }
        restoreButton.setOnClickListener { 
            restoreLauncher.launch(arrayOf("application/json"))
        }

        // Logout Button
        logoutButton.setOnClickListener {
            with(sharedPreferences.edit()) {
                remove("user_email")
                remove("user_password")
                apply()
            }
            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
        }

        // Add Debug Button
        findViewById<Button>(R.id.debugButton)?.setOnClickListener {
            lifecycleScope.launch {
                val database = AppDatabase.getDatabase(this@MainActivity)
                val transactions = database.transactionDao().getAllTransactionsSync()
                val debugText = buildString {
                    append("Database Contents:\n\n")
                    transactions.forEach { transaction: Transaction ->
                        append("ID: ${transaction.id}\n")
                        append("Title: ${transaction.title}\n")
                        append("Amount: $${transaction.amount}\n")
                        append("Category: ${transaction.category}\n")
                        append("Date: ${transaction.date}\n")
                        append("Type: ${transaction.type}\n")
                        append("-------------------\n")
                    }
                }
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Database Contents")
                    .setMessage(debugText)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun addTransaction() {
        val title = findViewById<EditText>(R.id.titleEditText).text.toString()
        val amountStr = findViewById<EditText>(R.id.amountEditText).text.toString()
        val category = findViewById<Spinner>(R.id.categorySpinner).selectedItem.toString()
        val type = if (findViewById<RadioButton>(R.id.incomeRadio).isChecked) "Income" else "Expense"

        if (title.isEmpty() || amountStr.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val amount = amountStr.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.addTransaction(title, amount, category, type)
        clearInputs()
    }

    private fun deleteTransaction(transaction: Transaction) {
        viewModel.deleteTransaction(transaction)
    }

    private fun saveBudget() {
        val budgetStr = budgetEditText.text.toString()
        monthlyBudget = budgetStr.toDoubleOrNull() ?: 0.0
        with(sharedPreferences.edit()) {
            putFloat("monthlyBudget", monthlyBudget.toFloat())
            apply()
        }
        Toast.makeText(this, "Budget saved", Toast.LENGTH_SHORT).show()
    }

    private fun updateSummary(transactions: List<Transaction>) {
        currentExpenses = transactions.filter { it.type == "Expense" }.sumOf { it.amount }
        val summaryText = findViewById<TextView>(R.id.summaryText)
        summaryText.text = buildString {
            append("Total Expenses: $$currentExpenses\n")
            append("Budget: $$monthlyBudget\n")
            append("Remaining: $${monthlyBudget - currentExpenses}\n\n")
            append("Category-wise Spending:\n")
            transactions.groupBy { it.category }.forEach { (category, list) ->
                val total = list.filter { it.type == "Expense" }.sumOf { it.amount }
                append("$category: $$total\n")
            }
        }

        if (currentExpenses >= monthlyBudget * 0.9) {
            sendNotification("Budget Alert", "You are approaching or exceeding your monthly budget!")
        }
    }

    private fun clearInputs() {
        findViewById<EditText>(R.id.titleEditText).text.clear()
        findViewById<EditText>(R.id.amountEditText).text.clear()
        findViewById<RadioButton>(R.id.expenseRadio).isChecked = true
    }

    private fun sendNotification(title: String, message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "budget_alerts"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Budget Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(1, notification)
    }

    private fun enableEdgeToEdge() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
        }
    }
}

